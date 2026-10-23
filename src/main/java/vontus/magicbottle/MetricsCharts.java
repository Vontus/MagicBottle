package vontus.magicbottle;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimpleBarChart;
import org.bstats.charts.SimplePie;
import org.bukkit.enchantments.Enchantment;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.config.Settings;

/**
 * The custom charts sent to bStats: the configuration of the server, only enums, booleans and buckets (never free
 * text or anything about players). The ids must match the charts created on the bStats website, or their data is
 * dropped. The callbacks run on every submission and read the settings then, so a reload shows without extra code.
 * A new feature or option decides here if it is worth a chart (see CONTRIBUTING.md).
 */
final class MetricsCharts {
    private MetricsCharts() {}

    static void register(Metrics metrics) {
        metrics.addCustomChart(new DrilldownPie("language", MetricsCharts::language));
        metrics.addCustomChart(new DrilldownPie("capacity", MetricsCharts::capacity));
        metrics.addCustomChart(new SimpleBarChart("features", MetricsCharts::features));
        metrics.addCustomChart(new SimpleBarChart("upgrades", MetricsCharts::upgrades));
        metrics.addCustomChart(new SimplePie("repair_enchantment", MetricsCharts::repairEnchantment));
        metrics.addCustomChart(new DrilldownPie("deposit_cost", MetricsCharts::depositCost));
        metrics.addCustomChart(new SimplePie("new_bottle_recipe", MetricsCharts::newBottleRecipe));
    }

    // language -> whether messages.yml is used
    private static Map<String, Map<String, Integer>> language() {
        return Map.of(Messages.language(), Map.of(String.valueOf(Config.settings.customMessages), 1));
    }

    private static Map<String, Map<String, Integer>> capacity() {
        int capacity = Config.settings.capacity;
        String bucket = capacity < 30 ? "<30" : capacity == 30 ? "30" : capacity <= 100 ? "31-100" : ">100";
        return Map.of(bucket, Map.of(String.valueOf(capacity), 1));
    }

    private static Map<String, Integer> features() {
        Settings s = Config.settings;
        Map<String, Integer> features = new LinkedHashMap<>();
        count(features, "merge", s.merge.enabled);
        count(features, "repair", s.repair.enabled);
        count(features, "spend", s.spend.enabled);
        count(features, "recipe deposit", s.recipe.deposit);
        count(features, "recipe withdraw", s.recipe.withdraw);
        count(features, "new bottle recipe", s.recipe.bottle.enabled);
        count(features, "crafters", s.recipe.bottle.allowCrafters);
        count(features, "sound", s.effect.sound);
        count(features, "particles", s.effect.particles);
        features.put("total", 1);
        return features;
    }

    private static Map<String, Integer> upgrades() {
        Map<String, Integer> upgrades = new LinkedHashMap<>();
        for (Upgrade upgrade : Upgrade.values()) {
            count(upgrades, upgrade.id(), Config.isUpgradeEnabled(upgrade));
        }
        upgrades.put("total", 1);
        return upgrades;
    }

    // Every server sends 1 for what it has enabled and for total, so the gap to total is who disabled it
    private static void count(Map<String, Integer> bars, String name, boolean enabled) {
        if (enabled) {
            bars.put(name, 1);
        }
    }

    private static String repairEnchantment() {
        if (Config.settings.repair.enchantment.isAny()) {
            return "ANY";
        }
        return Enchantment.MENDING.equals(Config.settings.repair.enchantment.getRequiredEnchantment())
                ? "MENDING"
                : "other";
    }

    private static Map<String, Map<String, Integer>> depositCost() {
        int cost = Config.settings.costs.deposit.expPercentage;
        String bucket = cost <= 0 ? "0" : cost <= 10 ? "1-10" : cost <= 25 ? "11-25" : ">25";
        return Map.of(bucket, Map.of(String.valueOf(cost), 1));
    }

    private static String newBottleRecipe() {
        return Config.isDefaultNewBottleRecipe() ? "default" : "custom";
    }
}
