package vontus.magicbottle.config;

import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import vontus.magicbottle.Plugin;

/**
 * The structure of config.yml. The initial value of each field is its default, and the keys keep the option names
 * the file has always had.
 */
@ConfigSerializable
public class Settings {
    static final String HEADER = """
			--------------------- MagicBottle Configuration ---------------------
			If a value is wrong, MagicBottle logs the option and its path when it loads.
			Options missing from this file are added with their default value when the plugin loads or reloads.
			For an explanation of how .yml (YAML) files work, visit https://github.com/Animosity/CraftIRC/wiki/Complete-idiot%27s-introduction-to-yaml
			I don't think you're an idiot.
			------------------------------------------------------------------""";

    @Comment("""
			The language of the default messages: en (English) or es (Spanish). An invalid value falls back to en.
			Bottles already made keep the language they had until they are rewritten (a deposit, a withdrawal...).
			This option is applied with '/mb reload'.""")
    public String language = "en";

    @Setting("custom messages")
    @Comment("""
			true: messages.yml is created in the chosen language and used, so you can customize every message (the messages
			that a new version adds are written into it in that language too). false: the messages of the chosen language are
			used and messages.yml is ignored, so don't edit it. This option is applied with '/mb reload'.""")
    public boolean customMessages = false;

    @Comment("Enable or disable sound and particle effects when a MagicBottle is used.")
    public Effect effect = new Effect();

    public Recipe recipe = new Recipe();

    @Comment("""
			The level a plain MagicBottle holds. Every bottle has a size (1 unless it was made by merging bottles) and holds the
			experience of 'size' bottles of this level, rounded down to a whole level: with 30, size 2 holds level 39 and size 4
			holds level 51. The capacity is calculated from this option every time, so changing it (and '/mb reload') rescales
			every bottle. A bottle that holds more than its capacity keeps all its experience and only refuses deposits until
			it goes below. From 1 to 20,000, which is also the most any bottle can hold.
			This replaces 'max level' of older versions: its 'default' is copied here once, and the permissions
			'magicbottle.maxlevel.*' no longer exist (the capacity is a property of the bottle, not of who holds it).""")
    public int capacity = 30;

    @Comment("Merging two full bottles in the anvil gives an empty bottle with the capacity of both.")
    public Merge merge = new Merge();

    public Repair repair = new Repair();

    public Spend spend = new Spend();

    @Comment("""
			Upgrades are applied to a MagicBottle in the smithing table: the bottle goes in the base slot and the ingredient in the
			addition slot (the template slot stays empty). The bottle keeps its experience. Each upgrade has an 'enabled' option and
			its 'ingredient', an item ID or an item tag like the ones of the bottle recipe. Applying an upgrade needs the permission
			'magicbottle.upgrade.apply.<upgrade>', and getting its effect from a bottle that has it, 'magicbottle.upgrade.use.<upgrade>'.
			Disabling an upgrade makes it stop working on the bottles that have it, but doesn't remove it from them: enabling it again brings it back.""")
    public Upgrades upgrades = new Upgrades();

    @Comment("Here you can configure what percentage of experience will be charged when transferring experience.")
    public Costs costs = new Costs();

    @ConfigSerializable
    public static class Effect {
        public boolean sound = true;
        public boolean particles = true;
    }

    @ConfigSerializable
    public static class Recipe {
        @Comment(
                "Enable or disable the ability to deposit or withdraw XP with the bottle by putting it alone in a crafting grid.\nThese two options are applied with '/mb reload'.")
        public boolean deposit = true;

        public boolean withdraw = true;

        @Comment(
                "Enable the ability to craft new MagicBottles.\nIf you disable this, players won't be able to craft new bottles but existing ones will remain usable.")
        public Bottle bottle = new Bottle();
    }

    @ConfigSerializable
    public static class Bottle {
        public boolean enabled = true;

        @Setting("allow crafters")
        @Comment("""
				Allow crafters to craft new MagicBottles.
				Crafting a bottle by hand requires the 'magicbottle.action.craft' permission, which is checked for each player.
				Crafters are not operated by a player, so that permission can't be checked: with this enabled,
				anyone can craft bottles through a crafter even without the permission.
				Set this to false if you use the permission to restrict who can craft bottles (e.g. only VIPs).""")
        public boolean allowCrafters = true;

        @Comment("""
				The recipe to craft a new empty MagicBottle, written like a shaped recipe in a datapack.
				shape: 1 to 3 rows of up to 3 characters, all the rows with the same length. Each character is a slot of the
				  crafting grid and a space is an empty slot. Every other character must be defined in ingredients.
				  Like any shaped recipe, it can be crafted anywhere in the grid, so a recipe of 2x2 or smaller can also be
				  crafted in the player's inventory.
				ingredients: the item for each character of the shape. Use item IDs (e.g. blaze_powder or minecraft:blaze_powder)
				  or item tags, which accept any item in the tag (e.g. "#minecraft:planks"; tags must be quoted because of the #).
				You can see a complete list of item IDs here: https://jd.papermc.io/paper/26.2/org/bukkit/Material.html
				and of item tags here: https://jd.papermc.io/paper/26.2/org/bukkit/Tag.html
				Filled bottles are dragon's breath, so having one grants the "You Need a Mint" advancement (an End advancement).
				The default recipe uses dragon_breath so that only players who have already got it can craft a bottle.
				If you remove it from the recipe, players will be able to get the advancement without going to the End.""")
        public List<String> shape = List.of("BEB", "GDG", "OOO");

        public Map<String, Ingredient> ingredients = defaultIngredients();

        private static Map<String, Ingredient> defaultIngredients() {
            Map<String, Ingredient> ingredients = new LinkedHashMap<>();
            ingredients.put("B", Ingredient.parse("blaze_powder"));
            ingredients.put("E", Ingredient.parse("ender_chest"));
            ingredients.put("G", Ingredient.parse("glowstone_dust"));
            ingredients.put("D", Ingredient.parse("dragon_breath"));
            ingredients.put("O", Ingredient.parse("gold_block"));
            return ingredients;
        }
    }

    @ConfigSerializable
    public static class Merge {
        @Comment("""
				Merging in the anvil: a full MagicBottle in the first slot and another one in the second give an empty bottle whose size is
				the sum of both (and with the upgrades of both). The capacity of each bottle is consumed; what a bottle holds above its capacity passes to the new bottle. It needs the permission
				'magicbottle.action.merge'. This option is applied with '/mb reload'.""")
        public boolean enabled = true;
    }

    /**
     * Brings a config.yml of an older version to the current structure, before it is mapped: 'custom messages' is true
     * if messages.yml exists, 'max level.default' becomes 'capacity' (unless the file already has it) and the whole
     * 'max level' node is removed, so no dead options stay.
     */
    static void migrate(Plugin plugin, CommentedConfigurationNode root) throws ConfigurateException {
        // Before 'custom messages' existed messages.yml was always used: keep it so no customization is lost
        if (root.node("custom messages").virtual()
                && Files.exists(plugin.getDataPath().resolve("messages.yml"))) {
            root.node("custom messages").set(true);
            Plugin.logger.info("config.yml: messages.yml exists, so 'custom messages' is true and it is still used.");
        }
        migrateMaxLevel(root);
    }

    private static void migrateMaxLevel(CommentedConfigurationNode root) throws ConfigurateException {
        ConfigurationNode old = root.node("max level");
        if (old.virtual()) {
            return;
        }
        if (root.node("capacity").virtual() && old.node("default").raw() instanceof Number level) {
            root.node("capacity").set(level.intValue());
            Plugin.logger.info("config.yml: 'max level.default' (" + level.intValue() + ") is now 'capacity'.");
        }
        if (!old.node("permissions").empty()) {
            Plugin.logger.warning(
                    "config.yml: 'max level.permissions' and the 'magicbottle.maxlevel.*' permissions no longer"
                            + " exist: the capacity belongs to the bottle. Bottles above their capacity keep their experience.");
        }
        root.removeChild("max level");
    }

    @ConfigSerializable
    public static class Repair {
        @Comment("""
				Enable or disable repairing in the anvil: a damaged tool or armor in the first slot and a MagicBottle with
				experience in the second. The repair is paid with the bottle's experience (1 exp repairs 2 durability points,
				like Mending) instead of the player's levels, which a vanilla anvil would charge.""")
        public boolean enabled = true;

        @Setting("auto feedback")
        @Comment(
                "Show an action bar message with the experience spent by auto-repair (sent once you stop repairing for 3 seconds)")
        public boolean autoFeedback = true;

        @Comment("""
				Tools and armor will need this enchantment in order to be able to repair them (its Minecraft ID, e.g. MENDING or minecraft:mending)
				Set this to ANY if you want MagicBottle to repair any repairable tool (unenchanted tools will be repaired too)""")
        public EnchantParser enchantment = EnchantParser.parse("MENDING");
    }

    @ConfigSerializable
    public static class Spend {
        @Comment("""
				Pay the level cost of anvil operations (combining enchantments, renaming...) and of the enchanting table with a
				MagicBottle in your inventory when you don't have enough levels. The cost is charged in experience points, not levels (the same points a vanilla
				anvil would take from level 0 up to the cost): your own experience goes first and the bottle covers the rest.
				The bottle in the selected slot is used first, then the offhand, then the rest of the inventory; if the first one
				doesn't cover the difference the anvil works as in vanilla (bottles aren't combined). Applied with '/mb reload'.
				While the anvil shows that result, the bottle lends you the levels (your experience bar shows them); what you don't
				spend goes back to the bottle as soon as the anvil changes, or when you close it.
				In the enchanting table the bottle lends you the points to reach the level of the best offer you can't afford; the
				table spends its 1 to 3 levels as usual and what is left goes back into the bottle when you close the table (or
				leave, die...), so only the exp the table spends is taken from it.""")
        public boolean enabled = true;
    }

    @ConfigSerializable
    public static class Upgrades {
        @Comment("""
				Auto-repair: with the bottle in the hotbar or the offhand, tools and armor repair themselves with its experience
				as they take damage (which items can be repaired is set in 'repair').""")
        @Setting("autorepair")
        public UpgradeOption autoRepair = new UpgradeOption("nether_star");

        @Comment("""
				Collect: with the bottle in the hotbar or the offhand, the experience orbs you pick up are stored in it instead of going to
				your experience bar. Mending takes its share first, and what doesn't fit in the bottle (it is full)
				goes to you. The deposit cost in 'costs' applies, rounded randomly orb by orb so small orbs
				aren't free (on average, you pay exactly the percentage). Take the bottle out of the hotbar to stop collecting.
				Note that without keepInventory the bottle drops when you die, unless it is soulbound.""")
        public CollectOption collect = new CollectOption("heart_of_the_sea");

        @Comment("""
				Soulbound: you keep the bottle, with all its experience and upgrades, when you die (without keepInventory it
				would drop). It works anywhere in your inventory, armor slots and offhand included, and the upgrade isn't
				consumed. It only protects against death drops: a bottle you throw, burn or clear, or one in your cursor or an
				open crafting grid when you die, is lost like any item.""")
        public UpgradeOption soulbound = new UpgradeOption("totem_of_undying");
    }

    @ConfigSerializable
    public static class UpgradeOption {
        public boolean enabled = true;

        public Ingredient ingredient = Ingredient.parse(null);

        public UpgradeOption() {}

        UpgradeOption(String ingredient) {
            this.ingredient = Ingredient.parse(ingredient);
        }
    }

    @ConfigSerializable
    public static class CollectOption extends UpgradeOption {
        @Comment("Show an action bar message with the experience stored (sent once you stop collecting for 3 seconds)")
        public boolean feedback = true;

        public CollectOption() {}

        CollectOption(String ingredient) {
            super(ingredient);
        }
    }

    @ConfigSerializable
    public static class Costs {
        public Deposit deposit = new Deposit();
    }

    @ConfigSerializable
    public static class Deposit {
        @Setting("exp-percentage")
        @Comment("""
				Percentage of the deposited experience that is lost, integer from 0 to 100.
				A deposit takes only what fills the bottle plus its cost, and is refused if the cost would leave
				nothing to save (always with 100).""")
        public int expPercentage = 0;
    }
}
