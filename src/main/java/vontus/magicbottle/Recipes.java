package vontus.magicbottle;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;

import vontus.magicbottle.config.Config;

import java.util.Map;

public class Recipes {
	public static final String nameBottle = "bottle";

	private Plugin plugin;

	// Filling and pouring a bottle in a crafting grid aren't recipes, or the recipe book would autofill any glass
	// bottle or dragon's breath. Events handles them by hand (onPrepareCraft/onClickCraftResult).
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
	}

	private ShapedRecipe getNewBottleRecipe() {
		ItemStack item = new MagicBottle(0).getItem();
		return new ShapedRecipe(getKey(plugin, nameBottle), item);
	}

	public static NamespacedKey getKey(Plugin plugin, String name) {
		return new NamespacedKey(plugin, name);
	}
}
