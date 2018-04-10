package vontus.magicbottle;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import net.md_5.bungee.api.ChatColor;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.util.Exp;

public class Commands implements CommandExecutor {
	private Plugin plugin;
	
	private final String USAGE_ABOUT = "/magicbottle about";
	private final String USAGE_REPAIR = "/magicbottle repair [auto]";
	private final String USAGE_GIVE = "/magicbottle give <level> [amount] [player]";
	private final String USAGE_RELOAD = "/magicbottle reload";

	public Commands(Plugin plugin) {
		this.plugin = plugin;
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String alias, String[] argument) {
		if (command.getName().equalsIgnoreCase("magicbottle")) {
			if (argument.length > 0) {
				switch (argument[0]) {
				case "about":
					about(sender);
					break;
				case "reload":
					reload(sender);
					break;
				case "give":
					give(sender, argument);
					break;
				case "repair":
					repair(sender, argument);
					break;
				default:
					sendMenu(sender);
				}
			} else {
				sendMenu(sender);
			}
		}
		return true;
	}

	private void repair(CommandSender sender, String[] args) {
		if (sender instanceof Player) {
			Player p = (Player) sender;
			switch (args.length) {
			case 1:
				commandRepairInventory(p);
				break;
			case 2:
				if (args[1].equals("auto")) {
					commandAutoRepair(p);
				} else {
					p.sendMessage(correctUse(USAGE_REPAIR));
				}
				break;
			}
		} else {
			sender.sendMessage(Messages.msgOnlyPlayersCommand);
		}
	}

	private void commandAutoRepair(Player p) {
		if (Config.repairAutoEnabled) {
			if (p.hasPermission(Config.permRepairAuto)) {
				if (plugin.autoEnabled.add(p)) {
					p.sendMessage(Messages.repairAutoEnabled);
				} else {
					plugin.autoEnabled.remove(p);
					p.sendMessage(Messages.repairAutoDisabled);
				}
			} else {
				p.sendMessage(Messages.msgUnauthorizedToUseCommand);
			}
		} else {
			p.sendMessage(Messages.repairAutoDisabledConfig);
		}
	}

	private void commandRepairInventory(Player p) {
		if (Config.repairEnabled) {
			if (p.hasPermission(Config.permRepair)) {
				ItemStack inHand = p.getInventory().getItemInHand();

				if (MagicBottle.isUsableMagicBottle(inHand)) {
					MagicBottle mb = new MagicBottle(inHand);
					Integer usedXP = mb.repair(p.getInventory());
					p.updateInventory();
					p.sendMessage(Messages.repairInvRepaired.replace("[xp]", usedXP.toString()));
				} else {
					p.sendMessage(Messages.repairMbNotInHand);
				}
			} else {
				p.sendMessage(Messages.msgUnauthorizedToUseCommand);
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
		if (sender.hasPermission(Config.permReload)) {
			plugin.loadConfig();
			sender.sendMessage(Messages.cmdMsgReloadCompleted);
		} else {
			sender.sendMessage(Messages.msgUnauthorizedToUseCommand);
		}
	}
	
	private void give(CommandSender sender, String[] args) {
		if (sender.hasPermission(Config.permGive)) {
			Player player = null;
			Integer level = 0;
			Integer amount = 1;
			
			if (sender instanceof Player) {
				player = (Player) sender;
			}
			
			try {
				switch (args.length) {
				case 4:
					Player p = plugin.getServer().getPlayer(args[3]);
					if (p != null)
						player = p;
				case 3:
					amount = Integer.parseInt(args[2]);
				case 2:
					level = Integer.parseInt(args[1]);
				case 1:
					if (level < 0 || level > Config.maxLevel) {
						sender.sendMessage(Messages.cmdMsgLevelNotValid);
					} else if (player == null) {
						sender.sendMessage("You must specify a connected player");
					} else {
						giveBottlesWithLevel(level, amount, player);
						String m = Messages.cmdMsgGivenMagicBottle;
						m = m.replace("[amount]", amount.toString())
								.replace("[player]", player.getName())
								.replace("[level]", level.toString());
						sender.sendMessage(m);
					}
					break;
				default:
					sender.sendMessage(correctUse(USAGE_GIVE));
				}
			} catch (NumberFormatException e) {
				sender.sendMessage(correctUse(USAGE_GIVE));
			}
		} else {
			sender.sendMessage(Messages.msgUnauthorizedToUseCommand);
		}
	}

	private String correctUse(String s) {
		String msg = Messages.cmdMsgCorrectUse;
		return msg.replace("[use]", s);
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
		player.getInventory().addItem(item);
	}
}
