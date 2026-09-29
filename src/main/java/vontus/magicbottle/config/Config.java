package vontus.magicbottle.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.util.Exp;

import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

public class Config {
	private static HashMap<String, Integer> maxLevelsPermission;

	private static Plugin plugin;

	public static final String permDeposit = "magicbottle.action.deposit";
	public static final String permWithdraw = "magicbottle.action.withdraw";
	public static final String permCraft = "magicbottle.action.craft";
	public static final String permGive = "magicbottle.command.give";
	public static final String permReload = "magicbottle.command.reload";
	public static final String permRecipe = "magicbottle.command.recipe";
	public static final String permRepair = "magicbottle.command.repair";
	public static final String permRepairAuto = "magicbottle.command.repair.auto";
	public static final String permDepositCostExempt = "magicbottle.action.deposit.cost.exempt";

	private static final String maxLevelsBasePermission = "magicbottle.maxlevel.";
	private static final String maxLevelsUnlimitedPermission = "magicbottle.maxlevel.unlimited";

	public static boolean effectSound;
	public static boolean effectParticles;
	public static boolean recipeFill;
	public static boolean recipePour;
	public static boolean recipeNewBottleEnabled;
	public static boolean recipeNewBottleAllowCrafters;
	public static String[] recipeNewBottleShape;
	public static Map<Character, RecipeChoice> recipeNewBottleIngredients;
	public static boolean repairEnabled;
	public static boolean repairAutoEnabled;

	private static int defaultRankMaxLevel;
	public static int maxLevel = 20000;

	public static double costPercentageDeposit;

	public static EnchantParser repairEnchantment;

	public static void load(Plugin plugin) {
		Config.plugin = plugin;
		maxLevelsPermission = new HashMap<>();

		effectSound = plugin.getConfig().getBoolean("effect.sound");
		effectParticles = plugin.getConfig().getBoolean("effect.particles");
		recipeFill = plugin.getConfig().getBoolean("recipe.deposit");
		recipePour = plugin.getConfig().getBoolean("recipe.withdraw");
		recipeNewBottleEnabled = plugin.getConfig().getBoolean("recipe.bottle.enabled");
		recipeNewBottleAllowCrafters = plugin.getConfig().getBoolean("recipe.bottle.allow crafters", true);
		if (plugin.getConfig().contains("recipe.bottle.recipe", true)) {
			Plugin.logger.warning("'recipe.bottle.recipe' in config.yml is no longer used, the new bottle recipe is now set with"
					+ " 'recipe.bottle.shape' and 'recipe.bottle.ingredients' (the default recipe is used if they are missing)."
					+ " Delete config.yml and restart the server to regenerate it.");
		}
		if (recipeNewBottleEnabled) {
			loadNewBottleRecipe();
		}
		defaultRankMaxLevel = plugin.getConfig().getInt("max level.default");

		for (String parent : plugin.getConfig().getConfigurationSection("max level.permissions").getKeys(false)) {
			int value = plugin.getConfig().getInt("max level.permissions." + parent);
			maxLevelsPermission.put(parent, value);
		}

		repairEnabled = plugin.getConfig().getBoolean("repair.enabled");
		repairAutoEnabled = plugin.getConfig().getBoolean("repair.auto");

		costPercentageDeposit = plugin.getConfig().getDouble("costs.deposit.exp-percentage") / 100;

		try {
			repairEnchantment = EnchantParser.parseForBukkit(plugin.getConfig().getString("repair.enchantment"));
		} catch (ParseException e) {
			if (repairEnabled || repairAutoEnabled) {
				repairEnabled = false;
				repairAutoEnabled = false;
				Plugin.logger.severe(e.getMessage() + ". Repairing has been disabled.");
			}
		}
	}

	public static boolean canRepair(ItemStack is) {
		return (repairEnabled || repairAutoEnabled)
				&& repairEnchantment.canRepair(is)
				&& is.getType().getMaxDurability() > 0
				&& is.getDurability() > 0;
	}

	// Parses the datapack-style shape and ingredients of the new bottle recipe. If anything is wrong, the recipe
	// is disabled instead of failing to enable the plugin.
	private static void loadNewBottleRecipe() {
		try {
			recipeNewBottleShape = parseRecipeShape(plugin.getConfig().getStringList("recipe.bottle.shape"));
			// get() instead of getConfigurationSection(): the latter returns a new empty section when the key is
			// missing from the file, instead of the one in the jar defaults
			Object ingredients = plugin.getConfig().get("recipe.bottle.ingredients");
			if (!(ingredients instanceof ConfigurationSection)) {
				throw new IllegalArgumentException("'ingredients' must be a section of 'character: item' entries");
			}
			recipeNewBottleIngredients = parseRecipeIngredients(recipeNewBottleShape, (ConfigurationSection) ingredients);
		} catch (IllegalArgumentException e) {
			recipeNewBottleEnabled = false;
			Plugin.logger.severe("Invalid new bottle recipe in config.yml: " + e.getMessage()
					+ ". The recipe won't be registered.");
		}
	}

	private static String[] parseRecipeShape(List<String> rows) {
		if (rows.isEmpty() || rows.size() > 3) {
			throw new IllegalArgumentException("'shape' must have 1 to 3 rows, found " + rows.size());
		}
		int length = rows.get(0).length();
		for (String row : rows) {
			if (row.isEmpty() || row.length() > 3) {
				throw new IllegalArgumentException("the shape row \"" + row + "\" must have 1 to 3 characters");
			}
			if (row.length() != length) {
				throw new IllegalArgumentException("all the shape rows must have the same length (use spaces for empty slots)");
			}
		}
		if (String.join("", rows).isBlank()) {
			throw new IllegalArgumentException("the shape is empty");
		}
		return rows.toArray(new String[0]);
	}

	private static Map<Character, RecipeChoice> parseRecipeIngredients(String[] shape, ConfigurationSection section) {
		Map<Character, RecipeChoice> ingredients = new HashMap<>();
		for (String row : shape) {
			for (char c : row.toCharArray()) {
				if (c != ' ' && !ingredients.containsKey(c)) {
					// With a default value, a key missing from the file doesn't fall back to the jar defaults
					String value = section.getString(String.valueOf(c), null);
					if (value == null) {
						throw new IllegalArgumentException("'" + c + "' is used in the shape but has no ingredient");
					}
					ingredients.put(c, parseRecipeIngredient(c, value.trim()));
				}
			}
		}
		for (String key : section.getKeys(false)) {
			if (key.length() != 1 || !ingredients.containsKey(key.charAt(0))) {
				Plugin.logger.warning("The new bottle recipe ingredient '" + key + "' isn't used in the shape, ignoring it.");
			}
		}
		return ingredients;
	}

	// An item ID (blaze_powder, BLAZE_POWDER or minecraft:blaze_powder) or an item tag (#minecraft:planks)
	private static RecipeChoice parseRecipeIngredient(char c, String value) {
		if (value.startsWith("#")) {
			NamespacedKey key = NamespacedKey.fromString(value.substring(1).toLowerCase(Locale.ROOT));
			Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
			if (tag == null || tag.getValues().isEmpty()) {
				throw new IllegalArgumentException("unknown item tag '" + value + "' for '" + c + "'");
			}
			return new RecipeChoice.MaterialChoice(tag);
		}
		Material m = Material.matchMaterial(value);
		if (m != null && m.isAir()) {
			throw new IllegalArgumentException("'" + c + "' can't be AIR, use a space in the shape for an empty slot");
		}
		if (m == null || !m.isItem()) {
			throw new IllegalArgumentException("unknown item '" + value + "' for '" + c + "'");
		}
		return new RecipeChoice.MaterialChoice(m);
	}

	public static int getMaxFillPointsFor(final Player p) {
		return Exp.getExpAtLevel(getMaxLevelsFor(p));
	}

	public static int getMaxLevelsFor(final Player p) {
		int max = -1;

		if (p.hasPermission(maxLevelsUnlimitedPermission))
			max = Config.maxLevel;
		else
			for (Entry<String, Integer> entry : maxLevelsPermission.entrySet()) {
				if (p.hasPermission(maxLevelsBasePermission + entry.getKey())) {
					max = Math.max(max, maxLevelsPermission.get(entry.getKey()));
				}
			}

		if (max == -1)
			max = defaultRankMaxLevel;
		return max;
	}
}
