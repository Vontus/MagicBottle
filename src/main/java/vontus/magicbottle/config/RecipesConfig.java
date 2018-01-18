package vontus.magicbottle.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class RecipesConfig {
	public static String BOTTLE = "bottle";
	private static PluginFile recipes;
	private static FileConfiguration file;

	public RecipesConfig(JavaPlugin plugin) {
		recipes = new PluginFile(plugin, "recipes.yml");
		file = recipes.getConfig();
	}

	public static List<RecipeIngredient> getRecipe(String recipe) {
		ArrayList<RecipeIngredient> ingredients = new ArrayList<>();
		List<?> configIngredients = file.getList(recipe);
		for (Object o : configIngredients) {
			LinkedHashMap hm = (LinkedHashMap) o;
			RecipeIngredient ingredient = new RecipeIngredient();
			ingredient.setAmount((int) hm.get("amount"));
			ingredient.setMaterial(Material.getMaterial((String) hm.get("material")));
			ingredients.add(ingredient);
		}
		return ingredients;
	}
}
