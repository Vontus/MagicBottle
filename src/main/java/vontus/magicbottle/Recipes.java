package vontus.magicbottle;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import vontus.magicbottle.config.Config;

public class Recipes {
	private Plugin plugin;

	@SuppressWarnings("deprecation")
	public Recipes(Plugin plugin) {
		if (Config.recipeFill) {
			ShapelessRecipe recipeFill = getShapelessRecipe(1);
			recipeFill.addIngredient(1, MagicBottle.materialEmpty);
			plugin.getServer().addRecipe(recipeFill);
		}

		if (Config.recipePour) {
			ShapelessRecipe recipePour = getShapelessRecipe(0);
			recipePour.addIngredient(1, MagicBottle.materialFilled, 11);
			plugin.getServer().addRecipe(recipePour);
		}

		if (Config.recipeNewBottleEnabled) {
			ShapedRecipe craftBottle = getNewBottleRecipe();
			craftBottle.shape("123", "456", "789");
			for (int i = 1; i < 10; i++) {
				craftBottle.setIngredient((char) (i + 48), Config.getBottleRecipeIngredient(i));
			}
			plugin.getServer().addRecipe(craftBottle);
		}
	}

	private ShapelessRecipe getShapelessRecipe(int level) {
		ItemStack item = new MagicBottle(level).getItem();
		return new ShapelessRecipe(item);
	}

	private ShapedRecipe getNewBottleRecipe() {
		ItemStack item = MagicBottle.getPreMagicBottle();
		return new ShapedRecipe(item);
	}
}
