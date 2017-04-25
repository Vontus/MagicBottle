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
				}
			} else {
				sendMenu(sender);
			}
		}
		return true;
	}

	private void about(CommandSender sender) {
		sender.sendMessage(
				plugin.getDescription().getFullName() + " by Vontus");
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
					break;
				case 1:
					if (level < 0 || level > Config.maxLevel) {
						sender.sendMessage(Messages.cmdMsgLevelNotValid);
					} else if (player == null) {
						sender.sendMessage("You must specify a connected player");
					} else {
						giveBottle(level, amount, player);
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
	
	private void giveBottle(int level, int amount, Player player) {
		MagicBottle bottle = new MagicBottle(Exp.getExpAtLevel(level));
		ItemStack item = bottle.getItem();
		item.setAmount(amount);
		player.getInventory().addItem(new ItemStack[] { item });
	}
}
