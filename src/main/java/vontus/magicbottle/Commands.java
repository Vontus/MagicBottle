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

public class Commands implements CommandExecutor {
	private Plugin plugin;

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
				commandAutoRepair(p, args);
				break;
			}
		}
	}
	
	private void commandAutoRepair(Player p, String[] args) {
		if (args[1].equals("auto")) {
			if (plugin.autoEnabled.add(p)) {
				p.sendMessage(Messages.repairAutoEnabled);
			} else {
				plugin.autoEnabled.remove(p);
				p.sendMessage(Messages.repairAutoDisabled);
			}
		}
	}
	
	private void commandRepairInventory(Player p) {
		
		ItemStack inHand = p.getInventory().getItemInMainHand();

		if (MagicBottle.isMagicBottle(inHand)) {
			MagicBottle mb = new MagicBottle(inHand);
			Integer usedXP = mb.repair(p.getInventory());
			p.updateInventory();
			p.sendMessage(Messages.repairInvRepaired.replace("%", usedXP.toString()));
		}
	}

	private void about(CommandSender sender) {
		sender.sendMessage(ChatColor.GOLD + plugin.getDescription().getFullName() + " by Vontus - WorldCraft version");
		sender.sendMessage(ChatColor.YELLOW + "https://www.spigotmc.org/resources/magicbottle.40039/");
	}

	private void reload(CommandSender sender) {
		if (sender.hasPermission(Config.authorizationReload)) {
			plugin.loadConfig();
			sender.sendMessage(Messages.cmdMsgReloadCompleted);
		} else {
			sender.sendMessage(Messages.msgUnauthorizedToReload);
		}
	}
	
	private void give(CommandSender sender, String[] args) {
		if (sender.hasPermission(Config.authorizationGive)) {
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
					sender.sendMessage(correctUse("/magicbottle give [level] [amount] [player]"));
				}
			} catch (NumberFormatException e) {
				sender.sendMessage(correctUse("/magicbottle give [level] [amount] [player]"));
			}
		} else {
			sender.sendMessage(Messages.msgUnauthorizedToUseCommand);
		}
	}

	private String correctUse(String s) {
		String msg = Messages.cmdMsgCorrectUse;
		return msg.replace("%", s);
	}
	
	private void sendMenu(CommandSender sender) {
		sender.sendMessage(ChatColor.GOLD + "- MagicBottle Commands -");
		sender.sendMessage(ChatColor.YELLOW + " /magicbottle about");
		if (sender.hasPermission(Config.authorizationGive)) {
			sender.sendMessage(ChatColor.YELLOW + " /magicbottle give [level] [amount] [player]");
		}
		if (sender.hasPermission(Config.authorizationReload)) {
			sender.sendMessage(ChatColor.YELLOW + " /magicbottle reload");
		}
	}
	
	public static void giveBottlesWithLevel(int level, int amount, Player player) {
		MagicBottle bottle = new MagicBottle(Exp.getExpAtLevel(level));
		ItemStack item = bottle.getItem();
		item.setAmount(amount);
		player.getInventory().addItem(new ItemStack[] { item });
	}
	
	public static void giveBottleWithExp(int exp, Player p) {
		MagicBottle bottle = new MagicBottle(exp);
		ItemStack item = bottle.getItem();
		item.setAmount(1);
		p.getInventory().addItem(new ItemStack[] { item });
	}
}
