package vontus.magicbottle;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;

import vontus.magicbottle.config.Config;

public class Recipes {
	public static final String nameBottle = "bottle";

	private Plugin plugin;

	// Filling and pouring a bottle in a crafting grid aren't recipes, or the recipe book would autofill any glass
	// bottle or dragon's breath. Events handles them by hand (onPrepareCraft/onClickCraftResult).
	public Recipes(Plugin plugin) {
		this.plugin = plugin;

		if (Config.recipeNewBottleEnabled) {
			ShapedRecipe craftBottle = getNewBottleRecipe();
			// AIR can't be an ingredient, so empty slots are left as spaces in the shape
			char[] shape = "123456789".toCharArray();
			for (int i = 1; i < 10; i++) {
				Material m = Config.getBottleRecipeIngredient(i);
				if (m == null || m == Material.AIR) {
					shape[i - 1] = ' ';
				}
			}
			String s = new String(shape);
			craftBottle.shape(s.substring(0, 3), s.substring(3, 6), s.substring(6, 9));
			for (int i = 1; i < 10; i++) {
				if (shape[i - 1] != ' ') {
					craftBottle.setIngredient(shape[i - 1], Config.getBottleRecipeIngredient(i));
				}
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
