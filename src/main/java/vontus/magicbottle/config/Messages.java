package vontus.magicbottle.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import vontus.magicbottle.Plugin;

import java.util.List;

/**
 * Messages from messages.yml, written in MiniMessage. Messages without placeholders are parsed once on load into
 * Components; the ones with placeholders are kept as raw strings and rendered with {@link #render} and the
 * TagResolvers of their placeholders.
 */
public class Messages {
	private static FileConfiguration file;

	// Placeholders: <level>
	public static String msgMaxLevelReached;

	public static Component msgUnauthorizedToDeposit;
	public static Component msgUnauthorizedToWithdraw;

	public static Component cmdMsgReloadCompleted;
	// Placeholders: <amount>, <player>, <level>
	public static String cmdMsgGivenMagicBottle;
	public static Component cmdMsgPlayerRequired;

	// Placeholders of the bottle texts: <level>, <points>, <xpbar>
	public static String bottleName;
	public static String bottleExperienceTitle;
	public static String bottleExperience;
	public static List<String> bottleLore;
	// Segments <xpbar> is made of
	public static Component bottleFilledBar;
	public static Component bottleEmptyBar;

	public static Component recipeTitle;
	public static Component recipeDisabled;

	// Placeholders: <xp>
	public static String repairInvRepaired;
	public static Component repairAutoEnabled;
	public static Component repairAutoDisabled;
	// Placeholders: <xp>
	public static String repairAutoSpent;
	public static Component repairDisabledConfig;
	public static Component repairAutoDisabledConfig;
	public static Component repairMbNotInHand;

	public static void load(Plugin plugin) {
		PluginFile lang = new PluginFile(plugin, "messages.yml");
		file = lang.getConfig();

		// ************************************************

		msgMaxLevelReached = file.getString("messages.max level reached");

		msgUnauthorizedToDeposit = component("messages.unauthorized.deposit");
		msgUnauthorizedToWithdraw = component("messages.unauthorized.withdraw");

		cmdMsgReloadCompleted = component("messages.commands.reload completed");
		cmdMsgGivenMagicBottle = file.getString("messages.commands.given bottle");
		cmdMsgPlayerRequired = component("messages.commands.player required");

		bottleName = file.getString("bottle text.name");
		bottleExperienceTitle = file.getString("bottle text.experience text");
		bottleExperience = file.getString("bottle text.experience");
		bottleLore = file.getStringList("bottle text.lore");
		bottleFilledBar = component("bottle text.filled bar");
		bottleEmptyBar = component("bottle text.empty bar");

		recipeTitle = component("messages.recipe.title");
		recipeDisabled = component("messages.recipe.disabled");

		repairInvRepaired = file.getString("messages.repair.inventory repaired");
		repairAutoEnabled = component("messages.repair.enabled autorepair");
		repairAutoDisabled = component("messages.repair.disabled autorepair");
		repairAutoSpent = file.getString("messages.repair.auto spent");
		repairDisabledConfig = component("messages.repair.config repairing disabled");
		repairAutoDisabledConfig = component("messages.repair.config auto repairing disabled");
		repairMbNotInHand = component("messages.repair.mb not in hand");
	}

	private static Component component(String config) {
		return render(file.getString(config));
	}

	public static Component render(String message, TagResolver... placeholders) {
		return MiniMessage.miniMessage().deserialize(message, placeholders);
	}

	// Like render, but not italic unless the message says so: items show custom names and lore in italics otherwise
	public static Component renderItemText(String message, TagResolver... placeholders) {
		return render(message, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
	}
}
