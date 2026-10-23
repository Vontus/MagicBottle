package vontus.magicbottle.config;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;

/**
 * Messages from messages.yml, written in MiniMessage. {@link Texts} is the structure of the file, whose initial field
 * values are the default messages; the texts are rendered into Components with {@link #render} and the TagResolvers
 * of their placeholders.
 */
public class Messages {
    /** The messages, as loaded. */
    @SuppressWarnings("NullAway.Init") // set by load, which onEnable calls first
    public static Texts texts;

    // The segments <xpbar> is made of; set by load
    @SuppressWarnings("NullAway.Init")
    public static Component bottleFilledBar;

    @SuppressWarnings("NullAway.Init")
    public static Component bottleEmptyBar;

    /** The languages bundled as {@code lang/<language>.yml}, besides English, which is the defaults of {@link Texts}. */
    private static final List<String> TRANSLATIONS = List.of("es");

    /** The language in use: {@code language} from config.yml, or en if it is not one of the bundled ones. */
    public static String language() {
        String language = Config.settings.language;
        return language.equals("en") || TRANSLATIONS.contains(language) ? language : "en";
    }

    public static void load(Plugin plugin) {
        String language = language();
        if (!language.equals(Config.settings.language)) {
            Plugin.logger.severe("Invalid 'language' in config.yml: '" + Config.settings.language + "'. Use en or "
                    + String.join(", ", TRANSLATIONS) + ". Using en.");
        }
        // The translation fills what the admin's file lacks, and English (the field initializers) what the translation
        // lacks
        CommentedConfigurationNode translation =
                language.equals("en") ? null : ConfigFile.loadResource(plugin, "lang/" + language + ".yml");
        if (translation != null) {
            logMissing(language, translation);
        }

        if (Config.settings.customMessages) {
            texts = ConfigFile.load(plugin, "messages.yml", Texts.class, Texts.HEADER, root -> {
                if (translation != null) {
                    root.mergeFrom(translation);
                }
            });
        } else {
            if (Files.exists(plugin.getDataPath().resolve("messages.yml"))) {
                Plugin.logger.info("messages.yml is ignored: 'custom messages' is false in config.yml.");
            }
            texts = ConfigFile.map(translation, Texts.class);
        }
        bottleFilledBar = render(texts.bottleText.filledBar);
        bottleEmptyBar = render(texts.bottleText.emptyBar);
    }

    // A message a translation lacks is English; the admin is told so it can be reported
    private static void logMissing(String language, CommentedConfigurationNode translation) {
        try {
            CommentedConfigurationNode reference = CommentedConfigurationNode.root();
            reference.set(Texts.class, new Texts());
            List<String> missing = new ArrayList<>();
            collectMissing(reference, translation, missing);
            if (!missing.isEmpty()) {
                Plugin.logger.warning("lang/" + language + ".yml lacks " + missing + ", which are in English.");
            }
        } catch (ConfigurateException e) {
            Plugin.logger.severe("Could not check lang/" + language + ".yml: " + e.getMessage());
        }
    }

    private static void collectMissing(ConfigurationNode reference, ConfigurationNode translation, List<String> out) {
        for (Map.Entry<Object, ? extends ConfigurationNode> child :
                reference.childrenMap().entrySet()) {
            ConfigurationNode other = translation.node(child.getKey());
            if (other.virtual()) {
                out.add(Arrays.stream(child.getValue().path().array())
                        .map(Object::toString)
                        .collect(Collectors.joining(".")));
            } else {
                collectMissing(child.getValue(), other, out);
            }
        }
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
				This file is only used if 'custom messages' is true in config.yml; otherwise it is ignored.
				It starts in the language set by 'language' in config.yml (the comments stay in English).
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

    // Placeholders of the bottle texts: <level> (the whole level it holds), <max_level> (its capacity in levels),
    // <size>,
    // <points>, <max_points> (its capacity in experience points), <xpbar>. They are not italic unless you add <italic>.
    @ConfigSerializable
    public static class BottleText {
        public String name =
                "<dark_purple><bold>MagicBottle</bold> <gray>| <yellow>Level <level>/<max_level> <gray>| <xpbar>";

        @Setting("name full")
        @Comment(
                "The name when the bottle is full (it holds its capacity or more). Placeholders: the ones above, <level> is the level it holds.")
        public String nameFull = "<dark_purple><bold>MagicBottle</bold> <gray>| <gold>Level <level> ✦ <gray>| <xpbar>";

        // <xpbar> is made of 18 parts, each one is the filled or the empty bar
        @Setting("filled bar")
        public String filledBar = "<dark_purple>|";

        @Setting("empty bar")
        public String emptyBar = "<yellow>|";

        @Setting("experience text")
        public String experienceTitle = "<dark_purple>Experience points:";

        public String experience = " <yellow><points><gray>/<max_points>";

        @Setting("capacity text")
        @Comment(
                "The title of the capacity, under the experience of the bottles with a size above 1 (made by merging bottles).")
        public String capacityTitle = "<dark_purple>Capacity:";

        @Comment("The line under the title of the capacity.")
        public String capacity = " <yellow>Level <max_level> <gray>(×<size>)";

        // The title and the lines are only shown on bottles that have upgrades, one line per upgrade
        @Setting("upgrades title")
        public String upgradesTitle = "<dark_purple>Upgrades <gray>(hotbar/offhand)<dark_purple>:";

        public Upgrades upgrades = new Upgrades();

        @Setting("upgrade disabled")
        @Comment(
                "The line of an upgrade the bottle has but that is disabled in config.yml (the bottle keeps it, and it works again if enabled). Placeholders: <upgrade> (its line above)")
        public String upgradeDisabled = "<dark_gray><st><upgrade></st>";

        @Setting("upgrade preview")
        @Comment(
                "The line of the upgrade that is being applied, while previewing it in the smithing table. Placeholders: <upgrade> (its line above)")
        public String upgradePreview = "<green>+</green><upgrade>";

        @Comment(
                "Lines at the end of the bottle. The command is fixed to /magicbottle: /mb may belong to another plugin.")
        public List<String> lore = List.of("<light_purple>How to use: /magicbottle guide");
    }

    // The lore line of each upgrade
    @ConfigSerializable
    public static class Upgrades {
        @Setting("autorepair")
        public String autoRepair = " <yellow>Auto-repair: <gray>repairs your items while you use them.";

        public String collect = " <yellow>Collect: <gray>stores the XP orbs you pick up.";

        public String soulbound = " <yellow>Soulbound: <gray>you keep the bottle when you die.";

        public String get(Upgrade upgrade) {
            return switch (upgrade) {
                case AUTO_REPAIR -> autoRepair;
                case COLLECT -> collect;
                case SOULBOUND -> soulbound;
            };
        }
    }

    @ConfigSerializable
    public static class Chat {
        // Placeholders: <level> (the capacity of the bottle in levels)
        @Setting("max level reached")
        @Comment("When the bottle is full. <level> is its capacity in levels.")
        public String maxLevelReached = "<red>The maximum level this MagicBottle can hold is <level>.";

        @Setting("nothing to store")
        @Comment("When the deposit cost would leave no experience to save in the bottle, so nothing is taken.")
        public String nothingToStore = "<red>That is too little experience to save anything after the deposit cost.";

        public Unauthorized unauthorized = new Unauthorized();
        public Commands commands = new Commands();
        public Recipe recipe = new Recipe();
        public Guide guide = new Guide();
        public Repair repair = new Repair();
        public Merge merge = new Merge();
        public Spend spend = new Spend();
        public Collect collect = new Collect();

        @Setting("feedback separator")
        @Comment(
                "Goes between the action bar messages of auto-repair and collect when they are shown together (they share one message, sent once you stop for 3 seconds).")
        public String feedbackSeparator = " <gray>| ";
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

        @Setting("upgrade no bottle")
        public String upgradeNoBottle = "<red>Hold a MagicBottle in your main hand.";

        @Setting("upgrade stack")
        public String upgradeStack = "<red>Hold a single MagicBottle, not a stack.";

        // Placeholders: <upgrades> (the ones the bottle has now)
        @Setting("upgrades changed")
        public String upgradesChanged = "<yellow>Bottle upgraded. It now has: <upgrades>";

        // Placeholders: <upgrades>
        @Setting("upgrades unchanged")
        public String upgradesUnchanged = "<yellow>Nothing changed. The bottle has: <upgrades>";

        @Setting("upgrades none")
        public String upgradesNone = "none";
    }

    @ConfigSerializable
    public static class Recipe {
        public String title = "<dark_purple>MagicBottle recipe";

        @Comment("When /mb recipe has no recipe to show the player.")
        public String disabled = "<red>There is no MagicBottle recipe you can use.";

        @Comment("The name of the arrow between the ingredients and the result in /mb recipe.")
        public String arrow = "<gray>Makes";
    }

    /** A page of the guide: its title (also the name of its button) and its paragraphs. */
    @ConfigSerializable
    public static class Page {
        public String title;
        public List<String> text;

        // Used by Configurate before it sets the fields
        Page() {
            this("");
        }

        Page(String title, String... text) {
            this.title = title;
            this.text = List.of(text);
        }
    }

    @ConfigSerializable
    public static class Guide {
        public String title = "<gold>MagicBottle guide";
        public String intro = "<gray>Choose a topic.";
        public String back = "<gray>« Back";

        @Comment("The first button of the guide: opens the recipe menu (/mb recipe), only if the player has a recipe.")
        public String recipe = "Recipe";

        @Comment("The button of the About page that opens the plugin page.")
        public String link = "Open the plugin page";

        @Comment("The pages of /mb guide. The Upgrades page lists the upgrades the player can apply after its text.")
        public Page hand = new Page(
                "In hand",
                "Hold the bottle: <yellow>left click</yellow> saves your levels in it and <yellow>right click</yellow> takes them out. A click moves 1 level, <yellow>Shift</yellow> moves 10. A full bottle accepts no more.");

        @Setting("hand cost")
        @Comment(
                "Added to the In hand page only if saving costs something for the player (costs.deposit.exp-percentage in config.yml, 0 for who is exempt). Placeholders: <percent>")
        public String handCost = "Saving costs <yellow><percent>%</yellow>: the bottle keeps less than you lose.";

        @Setting("hand grid")
        @Comment(
                "Added to the In hand page only if saving or taking everything in a crafting grid is enabled in config.yml (recipe.deposit and recipe.withdraw).")
        public String handGrid =
                "To move <yellow>all</yellow> the experience at once, put the bottle alone in a crafting grid (the 2x2 one of your inventory works) and take the result.";

        public Page upgrades = new Page(
                "Upgrades",
                "Upgrades are applied in the <yellow>smithing table</yellow>, with no template: the bottle goes in the base slot and the ingredient shown below in the addition slot. The bottle keeps its experience.",
                "An upgrade only works while the bottle is in your <yellow>hotbar</yellow> or <yellow>offhand</yellow>. Move it out of there to turn it off.");

        @Comment(
                "Placeholders: <level> (the level a plain bottle holds), <merged_level> (the level two of them make when merged)")
        public Page capacity = new Page(
                "Capacity and merging",
                "A bottle holds <yellow>Level <level></yellow> when it is full. A bigger bottle shows <yellow>(×2)</yellow> in its lore: it is worth two bottles and holds <yellow>Level <merged_level></yellow>.",
                "In the <yellow>anvil</yellow>, two <yellow>full</yellow> bottles merge into one with their sizes added and the upgrades of both.");

        public Page costs = new Page(
                "Paying costs",
                "When something in the <yellow>anvil</yellow> or the <yellow>enchanting table</yellow> costs more levels than you have, a bottle in your inventory pays the rest. Your own experience goes first.",
                "Only what is spent is taken from the bottle.");

        public Page repair = new Page(
                "Repair",
                "In the <yellow>anvil</yellow>, put a damaged item in the first slot and a bottle with experience in the second to repair it with the bottle's experience, with no level cost. 1 experience repairs 2 durability points, like Mending.",
                "The <yellow>auto-repair</yellow> upgrade repairs the items you use while the bottle is in your hotbar or offhand.");

        @Setting("repair enchantment")
        @Comment(
                "Added to the Repair page only if repair.enchantment in config.yml is an enchantment and not ANY. Placeholders: <enchantment>")
        public String repairEnchantment = "Only items with <yellow><enchantment></yellow> can be repaired.";

        @Comment(
                "Also sent by /mb about, one line each. Placeholders: <name> (the plugin name and version). The About page of the guide shows these lines.")
        public Page about = new Page(
                "About",
                "<gold><name> by Vontus",
                "<yellow><click:open_url:'https://github.com/Vontus/MagicBottle'>https://github.com/Vontus/MagicBottle</click>");
    }

    @ConfigSerializable
    public static class Collect {
        // Placeholders: <xp>
        public String stored = "<yellow>Collect <dark_purple>+<xp></dark_purple> XP";
    }

    @ConfigSerializable
    public static class Spend {
        // Placeholders: <xp>
        @Setting("from bottle")
        public String fromBottle = "<gray>Your MagicBottle covered the anvil: <yellow>-<xp> XP</yellow>";

        // Placeholders: <xp>
        public String enchant = "<gray>Your MagicBottle covered the enchanting: <yellow>-<xp> XP</yellow>";
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
        public String anvilCost =
                "<gray>Uses <yellow><xp></yellow> XP from the bottle (<yellow><xp_left></yellow> XP left)";
    }

    @ConfigSerializable
    public static class Merge {
        // Placeholders: <xp>
        @Setting("anvil")
        @Comment(
                "Line added to the empty bottle shown in the anvil, before taking it. Placeholders: <xp> (the experience that is consumed: the capacity of both bottles; the rest stays in the new bottle)")
        public String anvil = "<red>Consumes <yellow><xp></yellow> XP from the two bottles";
    }
}
