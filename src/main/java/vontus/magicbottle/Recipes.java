package vontus.magicbottle;

import org.bukkit.Bukkit;
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
			recipeFill.addIngredient(1, Material.GLASS_BOTTLE);
			plugin.getServer().addRecipe(recipeFill);
		}

		if (Config.recipePour) {
			ShapelessRecipe recipePour = getShapelessRecipe(0, "pour");
			recipePour.addIngredient(1, Material.EXP_BOTTLE);
			plugin.getServer().addRecipe(recipePour);
		}

		if (Config.recipeNewBottleEnabled) {
			ShapedRecipe craftBottle = getShapedRecipe(0, "bottle");
			craftBottle.shape("123", "456", "789");
			for (int i = 1; i < 10; i++) {
				craftBottle.setIngredient((char) (i + 48), Config.getBottleRecipeIngredient(i));
			}
			plugin.getServer().addRecipe(craftBottle);
		}
	}

	@SuppressWarnings("deprecation")
	public ShapelessRecipe getShapelessRecipe(int level, String name) {
		ItemStack item = new MagicBottle(level).getItem();
		if (Bukkit.getVersion().contains("1.12")) {
			NamespacedKey key = new NamespacedKey(plugin, name);
			return new ShapelessRecipe(key, item);
		} else {
			return new ShapelessRecipe(item);
		}
	}

	@SuppressWarnings("deprecation")
	public ShapedRecipe getShapedRecipe(int level, String name) {
		ItemStack item = new MagicBottle(level).getItem();
		if (Bukkit.getVersion().contains("1.12")) {
			NamespacedKey key = new NamespacedKey(plugin, name);
			return new ShapedRecipe(key, item);
		} else {
			return new ShapedRecipe(item);
		}
	}
}
