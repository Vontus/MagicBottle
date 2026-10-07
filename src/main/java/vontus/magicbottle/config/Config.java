package vontus.magicbottle.config;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.util.Exp;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Config {
	public static final String permDeposit = "magicbottle.action.deposit";
	public static final String permWithdraw = "magicbottle.action.withdraw";
	public static final String permCraft = "magicbottle.action.craft";
	public static final String permGive = "magicbottle.command.give";
	public static final String permReload = "magicbottle.command.reload";
	public static final String permRepair = "magicbottle.action.repair";
	public static final String permRepairAuto = "magicbottle.command.repair.auto";
	public static final String permDepositCostExempt = "magicbottle.action.deposit.cost.exempt";

	private static final String maxLevelsBasePermission = "magicbottle.maxlevel.";
	private static final String maxLevelsUnlimitedPermission = "magicbottle.maxlevel.unlimited";

	public static final int maxLevel = 20000;

	/** The options of config.yml, as loaded. */
	public static Settings settings;

	// What the options become once validated. A recipe or an enchantment that is wrong disables what depends on it
	// instead of the whole plugin.
	public static boolean recipeNewBottleEnabled;
	public static String[] recipeNewBottleShape;
	public static Map<Character, RecipeChoice> recipeNewBottleIngredients;
	public static boolean repairEnabled;
	public static boolean repairAutoEnabled;
	// Whether an enabled upgrade has a wrong ingredient, which the admin must fix: the plugin doesn't enable like that
	public static boolean invalidUpgrade;
	// The ingredient of each upgrade that is enabled and valid; the others have no recipe
	public static final Map<Upgrade, RecipeChoice> upgradeIngredients = new EnumMap<>(Upgrade.class);

	public static double costPercentageDeposit;

	public static void load(Plugin plugin) {
		settings = ConfigFile.load(plugin, "config.yml", Settings.class, Settings.HEADER);

		recipeNewBottleEnabled = settings.recipe.bottle.enabled;
		if (recipeNewBottleEnabled) {
			loadNewBottleRecipe(settings.recipe.bottle);
		}

		repairEnabled = settings.repair.enabled;
		repairAutoEnabled = settings.upgrades.autoRepair.enabled;
		if (settings.repair.enchantment.error != null && (repairEnabled || repairAutoEnabled)) {
			repairEnabled = false;
			repairAutoEnabled = false;
			Plugin.logger.severe("Invalid 'repair.enchantment' in config.yml: " + settings.repair.enchantment.error
					+ ". Repairing has been disabled.");
		}

		loadUpgrades();

		costPercentageDeposit = settings.costs.deposit.expPercentage / 100.0;
	}

	// An enabled upgrade needs a valid ingredient; Plugin refuses to enable with a wrong one (see invalidUpgrade)
	private static void loadUpgrades() {
		upgradeIngredients.clear();
		invalidUpgrade = false;
		for (Upgrade upgrade : Upgrade.values()) {
			Settings.UpgradeOption option = upgradeSettings(upgrade);
			if (!option.enabled) {
				continue;
			}
			if (option.ingredient == null || option.ingredient.error != null) {
				String error = option.ingredient == null ? "it has no item" : option.ingredient.error;
				Plugin.logger.severe("Invalid 'upgrades." + upgrade.id() + ".ingredient' in config.yml: " + error
						+ ".");
				invalidUpgrade = true;
			} else {
				upgradeIngredients.put(upgrade, option.ingredient.choice);
			}
		}
	}

	private static Settings.UpgradeOption upgradeSettings(Upgrade upgrade) {
		return switch (upgrade) {
			case REPAIR -> settings.upgrades.autoRepair;
		};
	}

	public static boolean isUpgradeEnabled(Upgrade upgrade) {
		return upgradeSettings(upgrade).enabled;
	}

	public static boolean canRepair(ItemStack is) {
		return (repairEnabled || repairAutoEnabled)
				&& settings.repair.enchantment.canRepair(is)
				&& is.getType().getMaxDurability() > 0
				&& is.getDurability() > 0;
	}

	// Validates the shape against the ingredients. If anything is wrong, the recipe is disabled instead of failing to
	// enable the plugin.
	private static void loadNewBottleRecipe(Settings.Bottle bottle) {
		try {
			recipeNewBottleShape = parseRecipeShape(bottle.shape);
			recipeNewBottleIngredients = parseRecipeIngredients(recipeNewBottleShape, bottle.ingredients);
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

	private static Map<Character, RecipeChoice> parseRecipeIngredients(String[] shape, Map<String, Ingredient> section) {
		Map<Character, RecipeChoice> ingredients = new HashMap<>();
		for (String row : shape) {
			for (char c : row.toCharArray()) {
				if (c != ' ' && !ingredients.containsKey(c)) {
					Ingredient ingredient = section.get(String.valueOf(c));
					if (ingredient == null) {
						throw new IllegalArgumentException("'" + c + "' is used in the shape but has no ingredient");
					}
					if (ingredient.error != null) {
						throw new IllegalArgumentException("ingredient '" + c + "': " + ingredient.error);
					}
					ingredients.put(c, ingredient.choice);
				}
			}
		}
		for (String key : section.keySet()) {
			if (key.length() != 1 || !ingredients.containsKey(key.charAt(0))) {
				Plugin.logger.warning("The new bottle recipe ingredient '" + key + "' isn't used in the shape, ignoring it.");
			}
		}
		return ingredients;
	}

	public static int getMaxFillPointsFor(final Player p) {
		return Exp.getExpAtLevel(getMaxLevelsFor(p));
	}

	public static int getMaxLevelsFor(final Player p) {
		if (p.hasPermission(maxLevelsUnlimitedPermission)) {
			return maxLevel;
		}
		int max = -1;
		for (Map.Entry<String, Integer> entry : settings.maxLevel.permissions.entrySet()) {
			if (p.hasPermission(maxLevelsBasePermission + entry.getKey())) {
				max = Math.max(max, entry.getValue());
			}
		}
		return max == -1 ? settings.maxLevel.def : max;
	}
}
