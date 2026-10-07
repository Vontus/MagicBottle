package vontus.magicbottle.listeners;

import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerExpChangeEvent;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.config.Config;

// The collect upgrade: the orbs a player picks up are stored in a bottle instead of going to their exp bar
public class CollectListener implements Listener {
	private final Plugin plugin;

	public CollectListener(Plugin plugin) {
		this.plugin = plugin;
	}

	// Fires once per orb unit (not per orb: the amount is already that of one unit) and after Mending, so the bottle only
	// gets what Mending leaves and collecting never blocks repairs. If an orb pickup was cancelled (e.g. a Magic Lantern
	// claimed it) this doesn't fire, and it doesn't cancel the pickup itself.
	@EventHandler(priority = EventPriority.HIGH)
	public void onExpChange(PlayerExpChangeEvent e) {
		Player p = e.getPlayer();
		// With a deposit cost of 100% nothing can be stored, so the orbs go to the player
		if (!(e.getSource() instanceof ExperienceOrb) || e.getAmount() <= 0 || !Config.isUpgradeEnabled(Upgrade.COLLECT)
				|| !MagicBottle.canStore(p)) {
			return;
		}
		int left = e.getAmount();
		// The first bottle with room; when it fills up the next one is tried, and what doesn't fit anywhere goes to the player
		MagicBottle bottle;
		while (left > 0 && (bottle = MagicBottle.findWithUpgrade(p, Upgrade.COLLECT, b -> b.hasRoom(p))) != null) {
			left = bottle.collect(p, left, gained -> plugin.collectFeedback.add(p, gained));
		}
		e.setAmount(left);
	}
}
