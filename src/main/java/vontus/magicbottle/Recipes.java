package vontus.magicbottle;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import vontus.magicbottle.config.Config;

import java.util.Map;

public class Recipes {
	public static final String nameBottle = "bottle";

	private Plugin plugin;

	public Recipes(Plugin plugin) {
		this.plugin = plugin;

		if (Config.recipeFill) {
			ShapelessRecipe recipeFill = getShapelessRecipe(1, "fill");
			recipeFill.addIngredient(1, MagicBottle.materialEmpty);
			plugin.getServer().addRecipe(recipeFill);
		}

		if (Config.recipePour) {
			ShapelessRecipe recipePour = getShapelessRecipe(0, "pour");
			recipePour.addIngredient(1, MagicBottle.materialFilled);
			plugin.getServer().addRecipe(recipePour);
		}

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

	private ShapelessRecipe getShapelessRecipe(int level, String name) {
		ItemStack item = new MagicBottle(level).getItem();
		NamespacedKey key = new NamespacedKey(plugin, name);
		return new ShapelessRecipe(key, item);
	}

	private ShapedRecipe getNewBottleRecipe() {
		ItemStack item = MagicBottle.getPreMagicBottle();
		return new ShapedRecipe(getKey(plugin, nameBottle), item);
	}

	public static NamespacedKey getKey(Plugin plugin, String name) {
		return new NamespacedKey(plugin, name);
	}
}
