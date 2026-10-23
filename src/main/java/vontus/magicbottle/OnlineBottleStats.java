package vontus.magicbottle;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * What the bottles in the inventory and ender chest of the online players hold, for bStats. Nothing is counted in game
 * events or persisted: it is a pass over the online players when a submission asks for it, on the main thread. The
 * charts of a submission are read in the same tick, so they share one pass.
 */
final class OnlineBottleStats {
    private static @Nullable OnlineBottleStats cached;
    private static int cachedTick = -1;

    final int players;
    final int playersWithBottle;
    // Bottles, counting every item of a stack
    int bottles;
    long points;
    final Map<Upgrade, Integer> upgrades = new EnumMap<>(Upgrade.class);
    // Size -> bottles of that size
    final Map<Integer, Integer> sizes = new TreeMap<>();

    private OnlineBottleStats() {
        players = Bukkit.getOnlinePlayers().size();
        int withBottle = 0;
        for (Player player : Bukkit.getOnlinePlayers()) {
            int before = bottles;
            count(player.getInventory());
            count(player.getEnderChest());
            if (bottles > before) {
                withBottle++;
            }
        }
        playersWithBottle = withBottle;
    }

    static synchronized OnlineBottleStats current() {
        int tick = Bukkit.getCurrentTick();
        OnlineBottleStats stats = cached;
        if (stats == null || tick != cachedTick) {
            stats = new OnlineBottleStats();
            cached = stats;
            cachedTick = tick;
        }
        return stats;
    }

    private void count(Inventory inventory) {
        for (ItemStack item : inventory) {
            if (item == null || !MagicBottle.isMagicBottle(item)) {
                continue;
            }
            // A copy: reading a 1.5.x bottle rewrites it, and a statistics pass must not touch the player's items
            MagicBottle bottle = new MagicBottle(item.clone());
            int amount = item.getAmount();
            bottles += amount;
            points += (long) bottle.getExp() * amount;
            for (Upgrade upgrade : Upgrade.values()) {
                if (bottle.hasUpgrade(upgrade)) {
                    upgrades.merge(upgrade, amount, Integer::sum);
                }
            }
            sizes.merge(bottle.getSize(), amount, Integer::sum);
        }
    }
}
