package vontus.magicbottle.config;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;

public class Messages {
	static PluginFile lang;
	static FileConfiguration file;

	public static final String levelReplacer = "[level]";
	public static final String xpPointsReplacer = "[points]";
	public static final String xpBarReplacer = "[xpbar]";
	public static final String moneyReplacer = "[money]";

	public static String msgMaxLevelReached;
	public static String msgOnlyPlayersCommand;
	
	public static String msgUnauthorizedToDeposit;
	public static String msgUnauthorizedToWithdraw;
	public static String msgUnauthorizedToUseCommand;
	public static String msgUnauthorizedToCraft;
	
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
	
    public static String newBottleName;
    public static ArrayList<String> newBottleLore;
    
    public static String repairInvRepaired;
    public static String repairAutoEnabled;
    public static String repairAutoDisabled;
    public static String repairDisabledConfig;
    public static String repairAutoDisabledConfig;
    public static String repairMbNotInHand;
    
	public static String msgNotEnoughMoney;    
	public static String msgChargeXpPercentage;

	public Messages(JavaPlugin plugin) {
		lang = new PluginFile(plugin, "messages.yml");
		file = lang.getConfig();

		// ************************************************

		msgMaxLevelReached = prepMsg("messages.max level reached");
		msgOnlyPlayersCommand = prepMsg("messages.only players command");
		
		msgUnauthorizedToDeposit = prepMsg("messages.unauthorized.deposit");
		msgUnauthorizedToWithdraw = prepMsg("messages.unauthorized.withdraw");
		msgUnauthorizedToUseCommand = prepMsg("messages.unauthorized.command");
		msgUnauthorizedToCraft = prepMsg("messages.unauthorized.craft");
		
		cmdMsgCorrectUse = prepMsg("messages.commands.correct use");
		cmdMsgReloadCompleted = prepMsg("messages.commands.reload completed");
		cmdMsgLevelNotValid = prepMsg("messages.commands.level not valid");
		cmdMsgGivenMagicBottle = prepMsg("messages.commands.given bottle");

        bottleName = prepMsg("bottle text.name");
        bottleLevelText = prepMsg("bottle text.experience text");
        bottleLevelFormat = prepMsg("bottle text.experience color");
        bottleFilledBarColor = prepMsg("bottle text.filled bar color");
        bottleEmptyBarColor = prepMsg("bottle text.empty bar color");
        bottleLore = getStringList("bottle text.lore");
        
        newBottleName = prepMsg("new bottle text.name");
        newBottleLore = getStringList("new bottle text.lore");
        
        repairInvRepaired = prepMsg("messages.repair.inventory repaired");
        repairAutoEnabled = prepMsg("messages.repair.enabled autorepair");
        repairAutoDisabled = prepMsg("messages.repair.disabled autorepair");
        repairDisabledConfig = prepMsg("messages.repair.config repairing disabled");
        repairAutoDisabledConfig = prepMsg("messages.repair.config auto repairing disabled");
        repairMbNotInHand = prepMsg("messages.repair.mb not in hand");
        
		msgNotEnoughMoney = prepMsg("messages.costs.not enough money");
		//msgChargeXpPercentage = prepMsg("messages.costs.charge xp percentage");
	}

	private static String prepMsg(String config) {
		return replaceColors(getMsg(config));
	}
	
	private static String getMsg(String config) {
		return file.getString(config);
	}

	private static String replaceColors(String msg) {
		return ChatColor.translateAlternateColorCodes('&', msg);
	}
	
	private static ArrayList<String> getStringList(String config) {
		ArrayList<String> rawLines = new ArrayList<>(file.getStringList(config));
		ArrayList<String> processed = new ArrayList<>();
		for (String line : rawLines)
			processed.add(replaceColors(line));
		return processed;
	}
}
