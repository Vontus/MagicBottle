package vontus.magicbottle;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.util.Exp;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.YELLOW;

public class Commands {
	private final Plugin plugin;
	private LiteralCommandNode<CommandSourceStack> root;

	// Usage shown in the menu for each subcommand node
	private static final Map<String, String> USAGES = Map.of(
			"about", "/magicbottle about",
			"reload", "/magicbottle reload",
			"give", "/magicbottle give <level> [amount] [player]",
			"recipe", "/magicbottle recipe",
			"repair", "/magicbottle repair [auto]");

	// One stack: both bottle materials stack up to 64
	private static final int MAX_GIVE_AMOUNT = 64;

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
						.then(argument("level", integer(0, Config.maxLevel))
								.executes(ctx -> give(ctx, 1, ctx.getSource().getExecutor()))
								.then(argument("amount", integer(1, MAX_GIVE_AMOUNT))
										.executes(ctx -> give(ctx, getInteger(ctx, "amount"), ctx.getSource().getExecutor()))
										.then(argument("player", ArgumentTypes.player())
												// Fails if the selector matches nobody, it never falls back to the executor
												.executes(ctx -> give(ctx, getInteger(ctx, "amount"), ctx
														.getArgument("player", PlayerSelectorArgumentResolver.class)
														.resolve(ctx.getSource()).getFirst()))))))
				.then(literal("recipe").requires(perm(Config.permRecipe).and(isPlayer())).executes(asPlayer(this::recipe)))
				.then(literal("repair")
						.requires(perm(Config.permRepair).and(isPlayer()))
						.executes(asPlayer(this::repair))
						.then(literal("auto").requires(perm(Config.permRepairAuto)).executes(asPlayer(this::toggleAutoRepair))))
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

	private void repair(Player p) {
		if (!Config.repairEnabled) {
			p.sendMessage(Messages.repairDisabledConfig);
			return;
		}
		ItemStack inHand = p.getInventory().getItemInMainHand();
		if (!MagicBottle.isUsableMagicBottle(inHand)) {
			p.sendMessage(Messages.repairMbNotInHand);
			return;
		}
		int usedXP = new MagicBottle(inHand).repair(p.getInventory(), true);
		p.updateInventory();
		p.sendMessage(Messages.repairInvRepaired.replace("[xp]", String.valueOf(usedXP)));
	}

	private void recipe(Player p) {
		if (Config.recipeNewBottleEnabled) {
			RecipeMenu.open(plugin, p);
		} else {
			p.sendMessage(Messages.recipeDisabled);
		}
	}

	private void toggleAutoRepair(Player p) {
		if (!Config.repairAutoEnabled) {
			p.sendMessage(Messages.repairAutoDisabledConfig);
		} else if (plugin.autoEnabled.add(p)) {
			p.sendMessage(Messages.repairAutoEnabled);
		} else {
			plugin.autoEnabled.remove(p);
			p.sendMessage(Messages.repairAutoDisabled);
		}
	}

	private void about(CommandSender sender) {
		sender.sendMessage(text(plugin.getPluginMeta().getDisplayName() + " by Vontus", GOLD));
		sender.sendMessage(text("https://www.spigotmc.org/resources/magicbottle.40039/", YELLOW));
	}

	private void reload(CommandSender sender) {
		plugin.loadConfig();
		sender.sendMessage(Messages.cmdMsgReloadCompleted);
	}

	/** Gives the bottles to {@code target}, which is the executor unless a player was specified. */
	private int give(CommandContext<CommandSourceStack> ctx, int amount, Entity target) {
		CommandSender sender = ctx.getSource().getSender();
		int level = getInteger(ctx, "level");

		if (!(target instanceof Player player)) {
			sender.sendMessage(Messages.cmdMsgPlayerRequired);
			return 0;
		}

		giveBottlesWithLevel(level, amount, player);
		sender.sendMessage(Messages.cmdMsgGivenMagicBottle
				.replace("[amount]", String.valueOf(amount))
				.replace("[player]", player.getName())
				.replace("[level]", String.valueOf(level)));
		return Command.SINGLE_SUCCESS;
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

	private static void giveBottlesWithLevel(int level, int amount, Player player) {
		MagicBottle bottle = new MagicBottle(Exp.getExpAtLevel(level));
		ItemStack item = bottle.getItem();
		item.setAmount(amount);
		// What doesn't fit in the inventory is dropped at the player's feet instead of being lost
		player.getInventory().addItem(item).values()
				.forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
	}
}
