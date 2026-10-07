package vontus.magicbottle;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;

import vontus.magicbottle.config.Config;

import java.util.Locale;
import java.util.Map;

public class Recipes {
	public static final String nameBottle = "bottle";
	private static final String upgradePrefix = "upgrade/";

	private Plugin plugin;

	// Depositing and withdrawing with a bottle in a crafting grid aren't recipes, or the recipe book would autofill any
	// dragon's breath. CraftingGridListener handles them by hand (onPrepareCraft/onClickCraftResult).
	public Recipes(Plugin plugin) {
		this.plugin = plugin;

		// The shape and ingredients were already validated by Config, which disables the recipe if they're invalid
		if (Config.recipeNewBottleEnabled) {
			ShapedRecipe craftBottle = getNewBottleRecipe();
			craftBottle.shape(Config.recipeNewBottleShape);
			for (Map.Entry<Character, RecipeChoice> ingredient : Config.recipeNewBottleIngredients.entrySet()) {
				craftBottle.setIngredient(ingredient.getKey(), ingredient.getValue());
			}
			plugin.getServer().addRecipe(craftBottle);
		}

		for (Map.Entry<Upgrade, RecipeChoice> ingredient : Config.upgradeIngredients.entrySet()) {
			plugin.getServer().addRecipe(getUpgradeRecipe(ingredient.getKey(), ingredient.getValue()));
		}
	}

	// Smithing slots only accept the items of a recipe, hence a real recipe per upgrade. It is a base for the preview:
	// SmithingListener builds the actual result from the bottle in the base slot, so it keeps its exp and upgrades.
	// Any dragon's breath matches the base (an exact choice would compare the PDC); the template slot stays empty.
	private SmithingTransformRecipe getUpgradeRecipe(Upgrade upgrade, RecipeChoice ingredient) {
		return new SmithingTransformRecipe(getKey(plugin, upgradePrefix + upgrade.id()), new MagicBottle(0).getItem(),
				null, new RecipeChoice.MaterialChoice(MagicBottle.material), ingredient);
	}

	private ShapedRecipe getNewBottleRecipe() {
		ItemStack item = new MagicBottle(0).getItem();
		return new ShapedRecipe(getKey(plugin, nameBottle), item);
	}

	// The upgrade a recipe applies, or null if it isn't the recipe of an upgrade
	public static Upgrade getUpgrade(Plugin plugin, NamespacedKey key) {
		if (!key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT)) || !key.getKey().startsWith(upgradePrefix)) {
			return null;
		}
		return Upgrade.fromId(key.getKey().substring(upgradePrefix.length()));
	}

	public static NamespacedKey getKey(Plugin plugin, String name) {
		return new NamespacedKey(plugin, name);
	}
}
