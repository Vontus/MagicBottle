package vontus.magicbottle.config;

import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

	@Comment("Enable or disable sound and particle effects when a MagicBottle is used.")
	public Effect effect = new Effect();

	public Recipe recipe = new Recipe();

	@Setting("max level")
	@Comment("Set the maximum level a player can save in a single MagicBottle.\nTIP: Set this depending on how difficult you made the recipe.")
	public MaxLevel maxLevel = new MaxLevel();

	public Repair repair = new Repair();

	@Comment("Here you can configure what percentage of experience will be charged when transferring experience.")
	public Costs costs = new Costs();

	@ConfigSerializable
	public static class Effect {
		public boolean sound = true;
		public boolean particles = true;
	}

	@ConfigSerializable
	public static class Recipe {
		@Comment("Enable or disable the ability to deposit or withdraw XP with the bottle by putting it alone in a crafting grid.\nThese two options are applied with '/mb reload'.")
		public boolean deposit = true;
		public boolean withdraw = true;

		@Comment("Enable the ability to craft new MagicBottles.\nIf you disable this, players won't be able to craft new bottles but existing ones will remain usable.\nYou must restart your server after changing 'enabled', 'shape' or 'ingredients'.")
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
	public static class MaxLevel {
		@Comment("This will be applied when the player doesn't have any of the following permissions.")
		@Setting("default")
		public int def = 200;

		@Comment("""
				Allow players to have bottles with more levels than the default option.
				To remove the limit entirely, give people 'magicbottle.maxlevel.unlimited'; still, the maximum level a bottle can hold is 20,000.
				You need to grant people the permission 'magicbottle.maxlevel.<name>', being <name> in the list shown below.
				If a player has two or more of these permissions, the maximum value will be applied, including 'magicbottle.maxlevel.unlimited'.
				  For example, a Veteran user can have the permissions 'user' and 'veteran', but only 'veteran' will be applied.
				You can modify, add or remove any of the following limits at will.""")
		public Map<String, Integer> permissions = defaultPermissions();

		private static Map<String, Integer> defaultPermissions() {
			Map<String, Integer> permissions = new LinkedHashMap<>();
			permissions.put("user", 300);
			permissions.put("veteran", 350);
			permissions.put("vip", 400);
			return permissions;
		}
	}

	@ConfigSerializable
	public static class Repair {
		@Comment("""
				Enable or disable repairing in the anvil: a damaged tool or armor in the first slot and a MagicBottle with
				experience in the second. The bottle's experience pays the repair (1 exp repairs 2 durability points, like
				Mending) and it costs no levels.""")
		public boolean enabled = true;

		@Comment("Enable or disable the '/mb autorepair' command (repairing automatically while you use your tools)")
		public boolean auto = true;

		@Setting("auto feedback")
		@Comment("Show an action bar message with the experience spent by autorepair (sent once you stop repairing for 3 seconds)")
		public boolean autoFeedback = true;

		@Comment("""
				Tools and armor will need this enchantment in order to be able to repair them (its Minecraft ID, e.g. MENDING or minecraft:mending)
				Set this to ANY if you want MagicBottle to repair any repairable tool (unenchanted tools will be repaired too)""")
		public EnchantParser enchantment = EnchantParser.parse("MENDING");
	}

	@ConfigSerializable
	public static class Costs {
		public Deposit deposit = new Deposit();
	}

	@ConfigSerializable
	public static class Deposit {
		@Setting("exp-percentage")
		@Comment("""
				Integer from 0 to 100.
				Values from 50 to 100 might take experience without saving any in the bottle.
				I'd recommend using a value from 0 to 49.""")
		public int expPercentage = 0;
	}
}
