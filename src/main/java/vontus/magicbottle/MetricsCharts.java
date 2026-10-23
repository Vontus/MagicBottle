package vontus.magicbottle;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimpleBarChart;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
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
        metrics.addCustomChart(new SingleLineChart("online_bottled_xp", MetricsCharts::onlineBottledXp));
        metrics.addCustomChart(new SimpleBarChart("online_bottle_upgrades", MetricsCharts::onlineBottleUpgrades));
        metrics.addCustomChart(new AdvancedPie("online_players_with_bottle", MetricsCharts::onlinePlayersWithBottle));
        metrics.addCustomChart(new DrilldownPie("online_bottle_sizes", MetricsCharts::onlineBottleSizes));
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

    // The charts below count the bottles of the online players (see OnlineBottleStats); bStats adds the values of all
    // servers

    // In thousands of points: a bottle at the hard cap holds about 1.8 billion, so a few would overflow an int
    private static int onlineBottledXp() {
        return (int) Math.min(OnlineBottleStats.current().points / 1000, Integer.MAX_VALUE);
    }

    // Disabled upgrades count too (they stay on the bottle); total is every bottle
    private static Map<String, Integer> onlineBottleUpgrades() {
        OnlineBottleStats stats = OnlineBottleStats.current();
        Map<String, Integer> bars = new LinkedHashMap<>();
        for (Upgrade upgrade : Upgrade.values()) {
            bars.put(upgrade.id(), stats.upgrades.getOrDefault(upgrade, 0));
        }
        bars.put("total", stats.bottles);
        return bars;
    }

    private static Map<String, Integer> onlinePlayersWithBottle() {
        OnlineBottleStats stats = OnlineBottleStats.current();
        Map<String, Integer> pie = new LinkedHashMap<>();
        pie.put("With bottle", stats.playersWithBottle);
        pie.put("Without bottle", stats.players - stats.playersWithBottle);
        return pie;
    }

    // Bucket -> exact size -> bottles, so the buckets can change later without losing data
    private static Map<String, Map<String, Integer>> onlineBottleSizes() {
        Map<String, Map<String, Integer>> sizes = new LinkedHashMap<>();
        OnlineBottleStats.current().sizes.forEach((size, bottles) -> {
            String bucket = size <= 2 ? String.valueOf(size) : size <= 4 ? "3-4" : "5+";
            sizes.computeIfAbsent(bucket, k -> new LinkedHashMap<>()).put(String.valueOf(size), bottles);
        });
        return sizes;
    }
}
