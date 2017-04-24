package vontus.magicbottle.config;

import java.util.ArrayList;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class Messages {
	static PluginFile lang;
	static FileConfiguration file;

//	public static final String prefixReplacer = "[prefix]";
	public static final String levelReplacer = "[level]";
	public static final String xpPointsReplacer = "[points]";
	public static final String xpBarReplacer = "[xpbar]";

	public static String msgMaxLevelReached;
	public static String msgUnauthorizedToDeposit;
	public static String msgUnauthorizedToWithdraw;
	public static String msgUnauthorizedToUseCommand;
	public static String msgUnauthorizedToReload;
	
	public static String cmdMsgCorrectUse;
	public static String cmdMsgReloadCompleted;
	public static String cmdMsgLevelNotValid;
	public static String cmdMsgGivenMagicBottle;
	
    public static String bottleName;
    public static String bottleLevelText;
    public static String bottleLevelFormat;
    public static String bottleFilledBarColor;
    public static String bottleEmptyBarColor;
    public static ArrayList<String> bottleLore;

	public Messages(JavaPlugin plugin) {
		lang = new PluginFile(plugin, "messages.yml");
		file = lang.getConfig();

		// ************************************************

		msgMaxLevelReached = getAndFormatMsg("messages.max level reached");
		msgUnauthorizedToDeposit = getAndFormatMsg("messages.unauthorized.deposit");
		msgUnauthorizedToWithdraw = getAndFormatMsg("messages.unauthorized.withdraw");
		msgUnauthorizedToUseCommand = getAndFormatMsg("messages.unauthorized.command");
		msgUnauthorizedToReload = getAndFormatMsg("messages.unauthorized.reload");
		
		cmdMsgCorrectUse = getAndFormatMsg("messages.commands.correct use");
		cmdMsgReloadCompleted = getAndFormatMsg("messages.commands.reload completed");
		cmdMsgLevelNotValid = getAndFormatMsg("messages.commands.level not valid");
		cmdMsgGivenMagicBottle = getAndFormatMsg("messages.commands.given bottle");
		
        bottleName = getAndFormatMsg("bottle name");
        bottleLevelText = getAndFormatMsg("bottle lore.experience text");
        bottleLevelFormat = getAndFormatMsg("bottle lore.experience color");
        bottleFilledBarColor = getAndFormatMsg("bottle lore.filled bar color");
        bottleEmptyBarColor = getAndFormatMsg("bottle lore.empty bar color");
        bottleLore = getStringList("bottle lore.lines");
	}

	private static String getAndFormatMsg(String config) {
		return replaceColors(
//				replacePrefix(
						getMsg(config));
	}
	
	private static String getMsg(String config) {
		return file.getString(config);
	}

	private static String replaceColors(String msg) {
		return ChatColor.translateAlternateColorCodes('&', msg);
	}

//	private static String replacePrefix(String msg) {
//		if (msg.contains(prefixReplacer)) {
//			msg = msg.replaceAll(prefixReplacer, chatPrefix);
//		}
//		return msg;
//	}
	
	private static ArrayList<String> getStringList(String config) {
		ArrayList<String> rawLines = new ArrayList<>(file.getStringList(config));
		ArrayList<String> processed = new ArrayList<>();
		for (String line : rawLines)
			processed.add(replaceColors(line));
		return processed;
	}
}
