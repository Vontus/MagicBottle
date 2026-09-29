package vontus.magicbottle;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import vontus.magicbottle.config.Config;

public class Recipes {
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

	private ShapelessRecipe getShapelessRecipe(int level, String name) {
		ItemStack item = new MagicBottle(level).getItem();
		NamespacedKey key = new NamespacedKey(plugin, name);
		return new ShapelessRecipe(key, item);
	}

	private ShapedRecipe getNewBottleRecipe() {
		ItemStack item = MagicBottle.getPreMagicBottle();
		NamespacedKey key = new NamespacedKey(plugin, "bottle");
		return new ShapedRecipe(key, item);
	}
}
