package vontus.magicbottle.config;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.util.Utils;

public class Config {
    public static final String permDeposit = "magicbottle.action.deposit";
    public static final String permWithdraw = "magicbottle.action.withdraw";
    public static final String permCraft = "magicbottle.action.craft";
    public static final String permGive = "magicbottle.command.give";
    public static final String permUpgrade = "magicbottle.command.upgrade";
    public static final String permReload = "magicbottle.command.reload";
    public static final String permRecipe = "magicbottle.command.recipe";
    public static final String permGuide = "magicbottle.command.guide";
    public static final String permRepair = "magicbottle.action.repair";
    public static final String permSpend = "magicbottle.action.spend";
    public static final String permMerge = "magicbottle.action.merge";
    public static final String permDepositCostExempt = "magicbottle.action.deposit.cost.exempt";

    public static final int maxLevel = 20000;

    /** The options of config.yml, as loaded. */
    @SuppressWarnings("NullAway.Init") // set by load, which onEnable calls first
    public static Settings settings;

    // What the options become once validated. A recipe or an enchantment that is wrong disables what depends on it
    // instead of the whole plugin.
    public static boolean recipeNewBottleEnabled;
    // Set by load only when the recipe is enabled and valid; readers check recipeNewBottleEnabled first
    @SuppressWarnings("NullAway.Init")
    public static String[] recipeNewBottleShape;

    @SuppressWarnings("NullAway.Init")
    public static Map<Character, RecipeChoice> recipeNewBottleIngredients;

    // The ingredients that are an item tag, with the tag as written in the config (#minecraft:wool), set with the
    // ingredients
    @SuppressWarnings("NullAway.Init")
    public static Map<Character, String> recipeNewBottleTags;

    public static boolean repairEnabled;
    public static boolean repairAutoEnabled;
    // Whether an enabled upgrade has a wrong ingredient, which the admin must fix: the plugin doesn't enable like that
    public static boolean invalidUpgrade;
    // The ingredient of each upgrade that is enabled and valid; the others have no recipe
    public static final Map<Upgrade, RecipeChoice.MaterialChoice> upgradeIngredients = new EnumMap<>(Upgrade.class);

    public static void load(Plugin plugin) {
        settings = ConfigFile.load(
                plugin, "config.yml", Settings.class, Settings.HEADER, root -> Settings.migrate(plugin, root));

        recipeNewBottleEnabled = settings.recipe.bottle.enabled;
        if (recipeNewBottleEnabled) {
            loadNewBottleRecipe(settings.recipe.bottle);
        }

        repairEnabled = settings.repair.enabled;
        repairAutoEnabled = settings.upgrades.autoRepair.enabled;
        if (settings.repair.enchantment.error != null && (repairEnabled || repairAutoEnabled)) {
            repairEnabled = false;
            repairAutoEnabled = false;
            Plugin.logger.severe("Invalid 'repair.enchantment' in config.yml: " + settings.repair.enchantment.error
                    + ". Repairing has been disabled.");
        }

        loadUpgrades();
    }

    // An enabled upgrade needs a valid ingredient, different from the one of every other enabled upgrade (the smithing
    // table would match only one of their recipes); Plugin refuses to enable with a wrong one (see invalidUpgrade)
    private static void loadUpgrades() {
        upgradeIngredients.clear();
        invalidUpgrade = false;
        Set<Upgrade> clashing = EnumSet.noneOf(Upgrade.class);
        for (Upgrade upgrade : Upgrade.values()) {
            Settings.UpgradeOption option = upgradeSettings(upgrade);
            if (!option.enabled) {
                continue;
            }
            RecipeChoice.MaterialChoice choice = option.ingredient.choice;
            if (choice == null) {
                Plugin.logger.severe("Invalid 'upgrades." + upgrade.id() + ".ingredient' in config.yml: "
                        + option.ingredient.error + ".");
                invalidUpgrade = true;
                continue;
            }
            // Two upgrades clash if any item is accepted by both (tags included); both are left without a recipe
            upgradeIngredients.forEach((other, otherChoice) -> {
                Set<Material> shared = new LinkedHashSet<>(otherChoice.getChoices());
                shared.retainAll(choice.getChoices());
                if (!shared.isEmpty()) {
                    Plugin.logger.severe(
                            "The upgrades '" + other.id() + "' and '" + upgrade.id()
                                    + "' in config.yml accept the same item(s) as ingredient (" + shared
                                    + "): the smithing table could apply only one of them. Every enabled upgrade needs a different ingredient.");
                    clashing.add(other);
                    clashing.add(upgrade);
                }
            });
            upgradeIngredients.put(upgrade, choice);
        }
        upgradeIngredients.keySet().removeAll(clashing);
        invalidUpgrade |= !clashing.isEmpty();
    }

    private static Settings.UpgradeOption upgradeSettings(Upgrade upgrade) {
        return switch (upgrade) {
            case AUTO_REPAIR -> settings.upgrades.autoRepair;
            case COLLECT -> settings.upgrades.collect;
            case SOULBOUND -> settings.upgrades.soulbound;
        };
    }

    public static boolean isUpgradeEnabled(Upgrade upgrade) {
        return upgradeSettings(upgrade).enabled;
    }

    public static boolean canRepair(ItemStack is) {
        return (repairEnabled || repairAutoEnabled)
                && settings.repair.enchantment.canRepair(is)
                && is.getType().getMaxDurability() > 0
                && Utils.getDamage(is) > 0;
    }

    /** Whether the shape and the ingredients of the new bottle recipe are the defaults, as the file says. */
    public static boolean isDefaultNewBottleRecipe() {
        Settings.Bottle current = settings.recipe.bottle;
        Settings.Bottle defaults = new Settings.Bottle();
        if (!current.shape.equals(defaults.shape) || current.ingredients.size() != defaults.ingredients.size()) {
            return false;
        }
        for (Map.Entry<String, Ingredient> entry : defaults.ingredients.entrySet()) {
            Ingredient ingredient = current.ingredients.get(entry.getKey());
            if (ingredient == null
                    || !Objects.equals(
                            ingredient.normalizedText(), entry.getValue().normalizedText())) {
                return false;
            }
        }
        return true;
    }

    // Validates the shape against the ingredients. If anything is wrong, the recipe is disabled instead of failing to
    // enable the plugin.
    private static void loadNewBottleRecipe(Settings.Bottle bottle) {
        try {
            recipeNewBottleShape = parseRecipeShape(bottle.shape);
            Map<Character, String> tags = new HashMap<>();
            recipeNewBottleIngredients = parseRecipeIngredients(recipeNewBottleShape, bottle.ingredients, tags);
            recipeNewBottleTags = tags;
        } catch (IllegalArgumentException e) {
            recipeNewBottleEnabled = false;
            Plugin.logger.severe(
                    "Invalid new bottle recipe in config.yml: " + e.getMessage() + ". The recipe won't be registered.");
        }
    }

    private static String[] parseRecipeShape(List<String> rows) {
        if (rows.isEmpty() || rows.size() > 3) {
            throw new IllegalArgumentException("'shape' must have 1 to 3 rows, found " + rows.size());
        }
        int length = rows.get(0).length();
        for (String row : rows) {
            if (row.isEmpty() || row.length() > 3) {
                throw new IllegalArgumentException("the shape row \"" + row + "\" must have 1 to 3 characters");
            }
            if (row.length() != length) {
                throw new IllegalArgumentException(
                        "all the shape rows must have the same length (use spaces for empty slots)");
            }
        }
        if (String.join("", rows).isBlank()) {
            throw new IllegalArgumentException("the shape is empty");
        }
        return rows.toArray(new String[0]);
    }

    private static Map<Character, RecipeChoice> parseRecipeIngredients(
            String[] shape, Map<String, Ingredient> section, Map<Character, String> tags) {
        Map<Character, RecipeChoice> ingredients = new HashMap<>();
        for (String row : shape) {
            for (int i = 0; i < row.length(); i++) {
                char c = row.charAt(i);
                if (c != ' ' && !ingredients.containsKey(c)) {
                    Ingredient ingredient = section.get(String.valueOf(c));
                    if (ingredient == null) {
                        throw new IllegalArgumentException("'" + c + "' is used in the shape but has no ingredient");
                    }
                    if (ingredient.error != null) {
                        throw new IllegalArgumentException("ingredient '" + c + "': " + ingredient.error);
                    }
                    ingredients.put(c, ingredient.choice);
                    if (ingredient.text != null && ingredient.text.startsWith("#")) {
                        tags.put(c, ingredient.text);
                    }
                }
            }
        }
        for (String key : section.keySet()) {
            if (key.length() != 1 || !ingredients.containsKey(key.charAt(0))) {
                Plugin.logger.warning(
                        "The new bottle recipe ingredient '" + key + "' isn't used in the shape, ignoring it.");
            }
        }
        return ingredients;
    }
}
