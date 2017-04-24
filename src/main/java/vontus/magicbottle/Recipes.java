package vontus.magicbottle;

import org.bukkit.Material;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import vontus.magicbottle.Plugin;
import vontus.magicbottle.config.Config;

public class Recipes {
    public Recipes(Plugin plugin) {
        if (Config.recipeFill) {
            ShapelessRecipe directFill = new ShapelessRecipe(new MagicBottle(1).getItem());
            directFill.addIngredient(1, Material.GLASS_BOTTLE);
            plugin.getServer().addRecipe(directFill);
        }
        if (Config.recipePour) {
            ShapelessRecipe directPour = new ShapelessRecipe(new MagicBottle(0).getItem());
            directPour.addIngredient(1, Material.EXP_BOTTLE);
            plugin.getServer().addRecipe(directPour);
        }
        
        if (Config.recipeNewBottleEnabled) {
            ShapedRecipe bottleCraft = new ShapedRecipe(new MagicBottle(0).getItem());
            bottleCraft.shape("123", "456", "789");
            for(int i = 1; i < 10; i++) {
            	bottleCraft.setIngredient((char)(i + 48), Config.getBottleRecipeIngredient(i));
            }
            plugin.getServer().addRecipe(bottleCraft);
        }
    }
}
