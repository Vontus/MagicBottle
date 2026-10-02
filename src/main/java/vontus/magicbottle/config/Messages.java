package vontus.magicbottle.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import vontus.magicbottle.Plugin;

import java.util.List;

/**
 * Messages from messages.yml, written in MiniMessage. {@link Texts} is the structure of the file, whose initial field
 * values are the default messages; the texts are rendered into Components with {@link #render} and the TagResolvers
 * of their placeholders.
 */
public class Messages {
	/** The messages, as loaded. */
	public static Texts texts;

	// The segments <xpbar> is made of
	public static Component bottleFilledBar;
	public static Component bottleEmptyBar;

	public static void load(Plugin plugin) {
		texts = ConfigFile.load(plugin, "messages.yml", Texts.class, Texts.HEADER);
		bottleFilledBar = render(texts.bottleText.filledBar);
		bottleEmptyBar = render(texts.bottleText.emptyBar);
	}

	public static Component render(String message, TagResolver... placeholders) {
		return MiniMessage.miniMessage().deserialize(message, placeholders);
	}

	// Like render, but not italic unless the message says so: items show custom names and lore in italics otherwise
	public static Component renderItemText(String message, TagResolver... placeholders) {
		return render(message, placeholders).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
	}

	@ConfigSerializable
	public static class Texts {
		static final String HEADER = """
				--------------------- MagicBottle Messages ---------------------
				Customize texts and colors MagicBottle uses when messaging players
				Messages use MiniMessage, e.g. '<gold>Level <level></gold>'. To see how to use it visit
				https://docs.advntr.dev/minimessage/format.html
				Placeholders are written between < and >, e.g. '<level>' represents the level number.
				Options missing from this file are added with their default message when the plugin loads or reloads.
				-------------------------------------------------------------""";

		@Setting("bottle text")
		public BottleText bottleText = new BottleText();

		public Chat messages = new Chat();
	}

	// Placeholders of the bottle texts: <level>, <points>, <xpbar>. They are not italic unless you add <italic>.
	@ConfigSerializable
	public static class BottleText {
		public String name = "<dark_purple><bold>MagicBottle</bold> <gray>| <yellow>Level <level> <gray>| <xpbar>";

		@Setting("experience text")
		public String experienceTitle = "<dark_purple>Experience points:";

		public String experience = " <yellow><points>";

		// <xpbar> is made of 18 parts, each one is the filled or the empty bar
		@Setting("filled bar")
		public String filledBar = "<dark_purple>|";

		@Setting("empty bar")
		public String emptyBar = "<yellow>|";

		public List<String> lore = List.of(
				"<dark_purple>Instructions:",
				" <yellow>Save XP: <gray>(Shift +) Left Click.",
				" <yellow>Take XP: <gray>(Shift +) Right Click.",
				" <yellow>You can also put the bottle in a crafting grid",
				" <yellow>to save or take all the experience.");
	}

	@ConfigSerializable
	public static class Chat {
		// Placeholders: <level>
		@Setting("max level reached")
		public String maxLevelReached = "<red>The maximum level you can save in a MagicBottle is <level>.";

		public Unauthorized unauthorized = new Unauthorized();
		public Commands commands = new Commands();
		public Recipe recipe = new Recipe();
		public Repair repair = new Repair();
	}

	@ConfigSerializable
	public static class Unauthorized {
		public String deposit = "<red>You do not have permission to save experience.";
		public String withdraw = "<red>You do not have permission to take experience.";
	}

	@ConfigSerializable
	public static class Commands {
		@Setting("reload completed")
		public String reloadCompleted = "<yellow>Configuration reloaded.";

		// Placeholders: <amount>, <player>, <level>
		@Setting("given bottle")
		public String givenBottle = "<yellow>Given <amount> MagicBottle(s) to <player> with <level> levels.";

		@Setting("player required")
		public String playerRequired = "<red>You must specify a connected player.";
	}

	@ConfigSerializable
	public static class Recipe {
		public String title = "<dark_purple>MagicBottle recipe";
		public String disabled = "<red>Crafting new MagicBottles is disabled in this server.";
	}

	@ConfigSerializable
	public static class Repair {
		// Placeholders: <xp>
		@Setting("inventory repaired")
		public String inventoryRepaired = "<yellow>Spent <dark_purple><xp></dark_purple> XP in repairing your tools.";

		@Setting("enabled autorepair")
		public String enabledAutorepair = "<yellow>Autorepair has been <green>enabled</green>.";

		@Setting("disabled autorepair")
		public String disabledAutorepair = "<yellow>Autorepair has been <red>disabled</red>.";

		// Placeholders: <xp>
		@Setting("auto spent")
		public String autoSpent = "<yellow>Autorepair spent <dark_purple><xp></dark_purple> XP.";

		@Setting("config repairing disabled")
		public String configRepairingDisabled = "<red>Repairing is disabled in this server.";

		@Setting("config auto repairing disabled")
		public String configAutoRepairingDisabled = "<red>Automatic repairing is disabled in this server.";

		@Setting("mb not in hand")
		public String mbNotInHand = "<red>You must have a MagicBottle with experience in your main hand to use this command.";
	}
}
