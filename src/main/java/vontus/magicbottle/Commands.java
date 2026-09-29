package vontus.magicbottle;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.util.Exp;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public class Commands {
	private final Plugin plugin;

	private static final String USAGE_ABOUT = "/magicbottle about";
	private static final String USAGE_REPAIR = "/magicbottle repair [auto]";
	private static final String USAGE_GIVE = "/magicbottle give <level> [amount] [player]";
	private static final String USAGE_RELOAD = "/magicbottle reload";

	Commands(Plugin plugin) {
		this.plugin = plugin;
	}

	/**
	 * Builds the /magicbottle command tree. Each subcommand is a literal node, and it is hidden from (and refused
	 * to) senders lacking its permission, so new subcommands are added here.
	 */
	LiteralCommandNode<CommandSourceStack> build() {
		return literal("magicbottle")
				.executes(ctx -> {
					sendMenu(ctx.getSource().getSender());
					return 1;
				})
				.then(literal("about")
						.executes(ctx -> {
							about(ctx.getSource().getSender());
							return 1;
						}))
				.then(literal("reload")
						.requires(s -> s.getSender().hasPermission(Config.permReload))
						.executes(ctx -> {
							reload(ctx.getSource().getSender());
							return 1;
						}))
				.then(literal("give")
						.requires(s -> s.getSender().hasPermission(Config.permGive))
						.then(argument("level", IntegerArgumentType.integer(0))
								.executes(ctx -> give(ctx, 1, false))
								.then(argument("amount", IntegerArgumentType.integer(1))
										.executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "amount"), false))
										.then(argument("player", ArgumentTypes.player())
												.executes(ctx -> give(ctx, IntegerArgumentType.getInteger(ctx, "amount"), true))))))
				.then(literal("repair")
						.requires(s -> s.getSender().hasPermission(Config.permRepair))
						.executes(ctx -> {
							repair(ctx.getSource().getSender());
							return 1;
						})
						.then(literal("auto")
								.requires(s -> s.getSender().hasPermission(Config.permRepairAuto))
								.executes(ctx -> {
									repairAuto(ctx.getSource().getSender());
									return 1;
								})))
				.build();
	}

	private void repair(CommandSender sender) {
		if (sender instanceof Player p) {
			commandRepairInventory(p);
		} else {
			sender.sendMessage(Messages.msgOnlyPlayersCommand);
		}
	}

	private void repairAuto(CommandSender sender) {
		if (sender instanceof Player p) {
			commandAutoRepair(p);
		} else {
			sender.sendMessage(Messages.msgOnlyPlayersCommand);
		}
	}

	private void commandAutoRepair(Player p) {
		if (Config.repairAutoEnabled) {
			if (plugin.autoEnabled.add(p)) {
				p.sendMessage(Messages.repairAutoEnabled);
			} else {
				plugin.autoEnabled.remove(p);
				p.sendMessage(Messages.repairAutoDisabled);
			}
		} else {
			p.sendMessage(Messages.repairAutoDisabledConfig);
		}
	}

	private void commandRepairInventory(Player p) {
		if (Config.repairEnabled) {
			ItemStack inHand = p.getInventory().getItemInMainHand();

			if (MagicBottle.isUsableMagicBottle(inHand)) {
				MagicBottle mb = new MagicBottle(inHand);
				Integer usedXP = mb.repair(p.getInventory(), true);
				p.updateInventory();
				p.sendMessage(Messages.repairInvRepaired.replace("[xp]", usedXP.toString()));
			} else {
				p.sendMessage(Messages.repairMbNotInHand);
			}
		} else {
			p.sendMessage(Messages.repairDisabledConfig);
		}
	}

	private void about(CommandSender sender) {
		sender.sendMessage(ChatColor.GOLD + plugin.getDescription().getFullName() + " by Vontus");
		sender.sendMessage(ChatColor.YELLOW + "https://www.spigotmc.org/resources/magicbottle.40039/");
	}

	private void reload(CommandSender sender) {
		plugin.loadConfig();
		sender.sendMessage(Messages.cmdMsgReloadCompleted);
	}

	private int give(CommandContext<CommandSourceStack> ctx, int amount, boolean withPlayer) throws CommandSyntaxException {
		CommandSender sender = ctx.getSource().getSender();
		int level = IntegerArgumentType.getInteger(ctx, "level");

		// The max level is configurable (and reloadable), so it can't be a fixed bound of the argument
		if (level > Config.maxLevel) {
			sender.sendMessage(Messages.cmdMsgLevelNotValid);
			return 0;
		}

		Player player;
		if (withPlayer) {
			// Fails if the selector matches nobody, it never falls back to the sender
			player = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource()).getFirst();
		} else if (sender instanceof Player p) {
			player = p;
		} else {
			sender.sendMessage("You must specify a connected player");
			return 0;
		}

		int maxAmount = new MagicBottle(Exp.getExpAtLevel(level)).getItem().getMaxStackSize();
		if (amount > maxAmount) {
			sender.sendMessage(Messages.cmdMsgAmountNotValid.replace("[max]", String.valueOf(maxAmount)));
			return 0;
		}

		giveBottlesWithLevel(level, amount, player);
		sender.sendMessage(Messages.cmdMsgGivenMagicBottle
				.replace("[amount]", String.valueOf(amount))
				.replace("[player]", player.getName())
				.replace("[level]", String.valueOf(level)));
		return 1;
	}

	private void sendMenu(CommandSender sender) {
		sender.sendMessage(ChatColor.GOLD + "- MagicBottle Commands -");
		sender.sendMessage(ChatColor.YELLOW + " " + USAGE_ABOUT);
		if (sender.hasPermission(Config.permGive)) {
			sender.sendMessage(ChatColor.YELLOW + " " + USAGE_GIVE);
		}
		if (sender.hasPermission(Config.permReload)) {
			sender.sendMessage(ChatColor.YELLOW + " " + USAGE_RELOAD);
		}
		if (sender.hasPermission(Config.permRepair)) {
			sender.sendMessage(ChatColor.YELLOW + " " + USAGE_REPAIR);
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
