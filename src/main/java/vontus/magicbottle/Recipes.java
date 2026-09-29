package vontus.magicbottle;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import vontus.magicbottle.Plugin;
import vontus.magicbottle.config.Config;

public class Recipes {
	Plugin plugin;

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
			ShapedRecipe craftBottle = getNewBottleRecipe(0, "bottle");
			// AIR can't be an ingredient, so empty slots are left as spaces in the shape
			StringBuilder shape = new StringBuilder();
			for (int i = 1; i < 10; i++) {
				Material m = Config.getBottleRecipeIngredient(i);
				shape.append(m == null || m == Material.AIR ? ' ' : (char) (i + 48));
			}
			craftBottle.shape(shape.substring(0, 3), shape.substring(3, 6), shape.substring(6, 9));
			for (int i = 1; i < 10; i++) {
				Material m = Config.getBottleRecipeIngredient(i);
				if (m != null && m != Material.AIR) {
					craftBottle.setIngredient((char) (i + 48), m);
				}
			}
			plugin.getServer().addRecipe(craftBottle);
		}
	}

	public ShapelessRecipe getShapelessRecipe(int level, String name) {
		ItemStack item = new MagicBottle(level).getItem();
		return new ShapelessRecipe(new NamespacedKey(plugin, name), item);
	}

	public ShapedRecipe getNewBottleRecipe(int level, String name) {
		ItemStack item = MagicBottle.getPreMagicBottle();
		return new ShapedRecipe(new NamespacedKey(plugin, name), item);
	}
}
