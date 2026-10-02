package vontus.magicbottle.listeners;

import org.bukkit.entity.Player;
import vontus.magicbottle.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Bottle clicks are ignored for a few ticks after an accepted one
public class ClickCooldown {
	private static final int CLICK_COOLDOWN_TICKS = 3;

	// Tick of each player's last accepted bottle click
	private final Map<UUID, Integer> lastClick = new HashMap<>();
	private final Plugin plugin;

	public ClickCooldown(Plugin plugin) {
		this.plugin = plugin;
	}

	// Returns true (and starts the cooldown) if the click is allowed
	public boolean throttle(Player p) {
		int now = plugin.getServer().getCurrentTick();
		Integer last = lastClick.get(p.getUniqueId());
		if (last != null && now - last < CLICK_COOLDOWN_TICKS) {
			return false;
		}
		lastClick.put(p.getUniqueId(), now);
		return true;
	}

	public void clear(Player p) {
		lastClick.remove(p.getUniqueId());
	}
}
