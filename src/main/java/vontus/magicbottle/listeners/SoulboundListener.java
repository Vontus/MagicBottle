package vontus.magicbottle.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.config.Config;

// The soulbound upgrade: the bottle isn't dropped on death, the player gets it back when they respawn
public class SoulboundListener implements Listener {
    // After LoanListener (LOWEST) has settled any loan, so the bottle kept holds the repaid points. Only what is in the
    // drops is moved: with keepInventory the items stay in the inventory and keeping them too would duplicate them.
    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        if (!Config.isUpgradeEnabled(Upgrade.SOULBOUND)) {
            return;
        }
        Player player = e.getEntity();
        if (!player.hasPermission(Upgrade.SOULBOUND.usePermission())) {
            return;
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null
                    && MagicBottle.isMagicBottle(item)
                    && new MagicBottle(item).hasUpgrade(Upgrade.SOULBOUND)
                    && e.getDrops().remove(item)) {
                e.getItemsToKeep().add(item);
            }
        }
    }
}
