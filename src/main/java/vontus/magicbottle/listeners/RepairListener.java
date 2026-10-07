package vontus.magicbottle.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.config.Config;

// Auto-repair of tools and armor with the bottles that have the repair upgrade, in the player's hotbar or offhand
public class RepairListener implements Listener {
	private final Plugin plugin;

	public RepairListener(Plugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onItemDamage(PlayerItemDamageEvent e) {
		Player p = e.getPlayer();
		// Cheap checks first: this fires for every durability loss of every player. It doesn't use the click cooldown,
		// so it neither blocks nor is blocked by bottle clicks.
		if (!Config.repairAutoEnabled) {
			return;
		}
		ItemStack i = e.getItem();
		// 1 exp repairs 2 durability points (like Mending), so only repair when the accumulated damage is odd: an even
		// value would spend 1 exp on a single point. Intentional, not a bug.
		if (i.getDurability() % 2 == 0 || e.isCancelled() || !Config.canRepair(i)) {
			return;
		}
		MagicBottle mb = MagicBottle.findWithUpgrade(p, Upgrade.AUTO_REPAIR, bottle -> !bottle.isEmpty());
		if (mb == null) {
			return;
		}
		i.setDurability((short) (i.getDurability() + e.getDamage()));
		plugin.autoRepairFeedback.add(p, mb.repair(i, false));
		e.setCancelled(true);
		p.updateInventory();
	}
}
