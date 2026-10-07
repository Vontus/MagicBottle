package vontus.magicbottle;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.RED;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public class Commands {
	private final Plugin plugin;
	private LiteralCommandNode<CommandSourceStack> root;

	// Usage shown in the menu for each subcommand node
	private static final Map<String, String> USAGES = Map.of(
			"about", "/magicbottle about",
			"reload", "/magicbottle reload",
			"give", "/magicbottle give <targets> <level>[upgrades] [count]",
			"upgrade", "/magicbottle upgrade <add|remove> <upgrades>",
			"recipe", "/magicbottle recipe");

	Commands(Plugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Builds the /magicbottle command tree. Each subcommand is a literal node, and it is hidden from (and refused
	 * to) senders lacking its permission, so new subcommands are added here along with their entry in USAGES.
	 */
	LiteralCommandNode<CommandSourceStack> build() {
		root = literal("magicbottle")
				.executes(ctx -> {
					sendMenu(ctx.getSource());
					return Command.SINGLE_SUCCESS;
				})
				.then(literal("about").executes(run(this::about)))
				.then(literal("reload").requires(perm(Config.permReload)).executes(run(this::reload)))
				.then(literal("give")
						.requires(perm(Config.permGive))
						// Fails if the selector matches nobody
						.then(argument("targets", ArgumentTypes.players())
								.then(argument("bottle", new BottleArgument())
										.executes(this::give))))
				.then(literal("upgrade")
						// The bottle is the one in the executor's hand
						.requires(perm(Config.permUpgrade).and(isPlayer()))
						.then(literal("add")
								.then(argument("upgrades", new UpgradeListArgument(true))
										.executes(ctx -> changeUpgrades(ctx, true))))
						.then(literal("remove")
								.then(argument("upgrades", new UpgradeListArgument(false))
										.executes(ctx -> changeUpgrades(ctx, false)))))
				.then(literal("recipe").requires(perm(Config.permCraft).and(isPlayer())).executes(asPlayer(this::recipe)))
				.build();
		return root;
	}

	private static Predicate<CommandSourceStack> perm(String permission) {
		return source -> source.getSender().hasPermission(permission);
	}

	private static Command<CommandSourceStack> run(Consumer<CommandSender> action) {
		return ctx -> {
			action.accept(ctx.getSource().getSender());
			return Command.SINGLE_SUCCESS;
		};
	}

	/** Runs the action on the executing player; the node must require {@link #isPlayer()}. */
	private static Command<CommandSourceStack> asPlayer(Consumer<Player> action) {
		return ctx -> {
			action.accept((Player) ctx.getSource().getExecutor());
			return Command.SINGLE_SUCCESS;
		};
	}

	private static Predicate<CommandSourceStack> isPlayer() {
		return source -> source.getExecutor() instanceof Player;
	}


	private void recipe(Player p) {
		if (Config.recipeNewBottleEnabled) {
			RecipeMenu.open(plugin, p);
		} else {
			p.sendMessage(Messages.render(Messages.texts.messages.recipe.disabled));
		}
	}

	private void about(CommandSender sender) {
		sender.sendMessage(text(plugin.getPluginMeta().getDisplayName() + " by Vontus", GOLD));
		sender.sendMessage(text("https://www.spigotmc.org/resources/magicbottle.40039/", YELLOW));
	}

	private void reload(CommandSender sender) {
		plugin.loadConfig();
		if (Config.invalidUpgrade) {
			sender.sendMessage(text("An upgrade ingredient in config.yml is invalid (see the console); fix it and restart the server.", RED));
		}
		sender.sendMessage(Messages.render(Messages.texts.messages.commands.reloadCompleted));
	}

	/** Gives {@code count} bottles to each of the targets. */
	private int give(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		BottleArgument.Spec spec = ctx.getArgument("bottle", BottleArgument.Spec.class);
		List<Player> targets = ctx.getArgument("targets", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());

		int count = spec.count();
		for (Player player : targets) {
			giveBottles(spec.createItem(), count, player);
		}
		TagResolver[] placeholders = {
				Placeholder.unparsed("amount", String.valueOf(count)),
				Placeholder.unparsed("level", String.valueOf(spec.level())),
				Placeholder.unparsed("player", targets.getFirst().getName()),
				Placeholder.unparsed("players", String.valueOf(targets.size()))};
		String message = targets.size() == 1 ? Messages.texts.messages.commands.givenBottle
				: Messages.texts.messages.commands.givenBottles;
		ctx.getSource().getSender().sendMessage(Messages.render(message, placeholders));
		return targets.size();
	}

	/**
	 * Adds or removes upgrades on the bottle in the executor's main hand, ignoring the upgrade permissions and
	 * settings (admin). Adding what the bottle has or removing what it lacks changes nothing.
	 */
	private int changeUpgrades(CommandContext<CommandSourceStack> ctx, boolean adding) {
		Player player = (Player) ctx.getSource().getExecutor();
		CommandSender sender = ctx.getSource().getSender();
		ItemStack held = player.getInventory().getItemInMainHand();
		if (!MagicBottle.isMagicBottle(held)) {
			sender.sendMessage(Messages.render(Messages.texts.messages.commands.upgradeNoBottle));
			return 0;
		}
		if (held.getAmount() != 1) {
			sender.sendMessage(Messages.render(Messages.texts.messages.commands.upgradeStack));
			return 0;
		}

		@SuppressWarnings("unchecked")
		Set<Upgrade> requested = ctx.getArgument("upgrades", Set.class);
		MagicBottle bottle = new MagicBottle(held);
		boolean changed = false;
		for (Upgrade upgrade : requested) {
			if (bottle.hasUpgrade(upgrade) != adding) {
				if (adding) {
					bottle.addUpgrade(upgrade);
				} else {
					bottle.removeUpgrade(upgrade);
				}
				changed = true;
			}
		}

		String now = Arrays.stream(Upgrade.values()).filter(bottle::hasUpgrade).map(Upgrade::id)
				.collect(Collectors.joining(", "));
		Messages.Commands texts = Messages.texts.messages.commands;
		sender.sendMessage(Messages.render(changed ? texts.upgradesChanged : texts.upgradesUnchanged,
				Placeholder.unparsed("upgrades", now.isEmpty() ? texts.upgradesNone : now)));
		return changed ? 1 : 0;
	}

	/** Lists the subcommands the source can use, as the tree's requirements decide. */
	private void sendMenu(CommandSourceStack source) {
		CommandSender sender = source.getSender();
		sender.sendMessage(text("- MagicBottle Commands -", GOLD));
		for (CommandNode<CommandSourceStack> node : root.getChildren()) {
			if (node.canUse(source)) {
				sender.sendMessage(text(" " + USAGES.get(node.getName()), YELLOW));
			}
		}
	}

	/** Splits the count into stacks; what doesn't fit in the inventory is dropped at the player's feet. */
	private static void giveBottles(ItemStack item, int count, Player player) {
		for (int left = count; left > 0; ) {
			ItemStack stack = item.clone();
			stack.setAmount(Math.min(left, stack.getMaxStackSize()));
			left -= stack.getAmount();
			player.getInventory().addItem(stack).values()
					.forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
		}
	}
}
