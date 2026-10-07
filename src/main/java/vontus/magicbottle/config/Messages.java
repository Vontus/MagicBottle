package vontus.magicbottle.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;

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

		// The title and the lines are only shown on bottles that have upgrades, one line per upgrade
		@Setting("upgrades title")
		public String upgradesTitle = "<dark_purple>Upgrades <gray>(hotbar/offhand)<dark_purple>:";

		@Setting("upgrade preview")
		@Comment("The line of the upgrade that is being applied, while previewing it in the smithing table. Placeholders: <upgrade> (its line above)")
		public String upgradePreview = "<green>+</green><upgrade>";

		public Upgrades upgrades = new Upgrades();

		public List<String> lore = List.of(
				"<dark_purple>Instructions:",
				" <yellow>Save XP: <gray>(Shift +) Left Click.",
				" <yellow>Take XP: <gray>(Shift +) Right Click.",
				" <yellow>You can also put the bottle in a crafting grid",
				" <yellow>to save or take all the experience.");
	}

	// The lore line of each upgrade
	@ConfigSerializable
	public static class Upgrades {
		@Setting("autorepair")
		public String autoRepair = " <yellow>Auto-repair: <gray>repairs your items while you use them.";

		public String collect = " <yellow>Collect: <gray>stores the XP orbs you pick up.";

		public String get(Upgrade upgrade) {
			return switch (upgrade) {
				case AUTO_REPAIR -> autoRepair;
				case COLLECT -> collect;
			};
		}
	}

	@ConfigSerializable
	public static class Chat {
		// Placeholders: <level>
		@Setting("max level reached")
		public String maxLevelReached = "<red>The maximum level you can save in a MagicBottle is <level>.";

		@Setting("feedback separator")
		@Comment("Goes between the action bar messages of auto-repair and collect when they are shown together (they share one message, sent once you stop for 3 seconds).")
		public String feedbackSeparator = " <gray>| ";

		public Unauthorized unauthorized = new Unauthorized();
		public Commands commands = new Commands();
		public Recipe recipe = new Recipe();
		public Collect collect = new Collect();
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

		// Placeholders: <amount>, <players> (how many), <level>
		@Setting("given bottles")
		public String givenBottles = "<yellow>Given <amount> MagicBottle(s) to <players> players with <level> levels.";
	}

	@ConfigSerializable
	public static class Recipe {
		public String title = "<dark_purple>MagicBottle recipe";
		public String disabled = "<red>Crafting new MagicBottles is disabled in this server.";
	}

	@ConfigSerializable
	public static class Collect {
		// Placeholders: <xp>
		public String stored = "<yellow>Collect <dark_purple>+<xp></dark_purple> XP";
	}

	@ConfigSerializable
	public static class Repair {
		// Placeholders: <xp>
		@Setting("auto spent")
		public String autoSpent = "<yellow>Repair <dark_purple>-<xp></dark_purple> XP";

		@Setting("anvil cost")
		@Comment("""
				Line added to the repaired item shown in the anvil, before taking it. Placeholders:
				  <xp>: experience points the repair takes from the bottle
				  <xp_left>: experience points the bottle will have left
				  <levels>: levels the bottle loses (how much its level drops, e.g. from level 30 to 24 is 6)
				  <levels_left>: the level the bottle will have after the repair""")
		public String anvilCost = "<gray>Uses <yellow><xp></yellow> XP from the bottle (<yellow><xp_left></yellow> XP left)";
	}
}
