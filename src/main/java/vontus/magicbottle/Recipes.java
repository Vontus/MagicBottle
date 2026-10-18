package vontus.magicbottle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.jspecify.annotations.Nullable;
import vontus.magicbottle.config.Config;

public class Recipes {
    public static final String nameBottle = "bottle";
    private static final String upgradePrefix = "upgrade/";

    private final Plugin plugin;
    // What register added last time, so the next one replaces it
    private final List<NamespacedKey> registered = new ArrayList<>();

    // Depositing and withdrawing with a bottle in a crafting grid aren't recipes, or the recipe book would autofill any
    // dragon's breath. CraftingGridListener handles them by hand (onPrepareCraft/onClickCraftResult).
    public Recipes(Plugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Registers the recipes from what Config holds, replacing the ones registered before, so /mb reload applies a new
     * recipe or ingredient. {@code resend} sends the recipes to the online players, once after every change.
     */
    public void register(boolean resend) {
        Server server = plugin.getServer();
        for (NamespacedKey key : registered) {
            server.removeRecipe(key, false);
        }
        registered.clear();

        // The shape and ingredients were already validated by Config, which disables the recipe if they're invalid
        if (Config.recipeNewBottleEnabled) {
            ShapedRecipe craftBottle = getNewBottleRecipe();
            craftBottle.shape(Config.recipeNewBottleShape);
            for (Map.Entry<Character, RecipeChoice> ingredient : Config.recipeNewBottleIngredients.entrySet()) {
                craftBottle.setIngredient(ingredient.getKey(), ingredient.getValue());
            }
            add(server, craftBottle);
        }

        for (Map.Entry<Upgrade, RecipeChoice> ingredient : Config.upgradeIngredients.entrySet()) {
            add(server, getUpgradeRecipe(ingredient.getKey(), ingredient.getValue()));
        }

        if (resend) {
            server.updateRecipes();
        }
    }

    private <R extends Recipe & Keyed> void add(Server server, R recipe) {
        server.addRecipe(recipe, false);
        registered.add(recipe.getKey());
    }

    // Smithing slots only accept the items of a recipe, hence a real recipe per upgrade. It is a base for the preview:
    // SmithingListener builds the actual result from the bottle in the base slot, so it keeps its exp and upgrades.
    // Any dragon's breath matches the base (an exact choice would compare the PDC); the template slot stays empty.
    private SmithingTransformRecipe getUpgradeRecipe(Upgrade upgrade, RecipeChoice ingredient) {
        return new SmithingTransformRecipe(
                getKey(plugin, upgradePrefix + upgrade.id()),
                new MagicBottle(0).getItem(),
                null,
                new RecipeChoice.MaterialChoice(MagicBottle.material),
                ingredient);
    }

    private ShapedRecipe getNewBottleRecipe() {
        ItemStack item = new MagicBottle(0).getItem();
        return new ShapedRecipe(getKey(plugin, nameBottle), item);
    }

    // The upgrade a recipe applies, or null if it isn't the recipe of an upgrade
    public static @Nullable Upgrade getUpgrade(Plugin plugin, NamespacedKey key) {
        if (!key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT))
                || !key.getKey().startsWith(upgradePrefix)) {
            return null;
        }
        return Upgrade.fromId(key.getKey().substring(upgradePrefix.length()));
    }

    public static NamespacedKey getKey(Plugin plugin, String name) {
        return new NamespacedKey(plugin, name);
    }
}
