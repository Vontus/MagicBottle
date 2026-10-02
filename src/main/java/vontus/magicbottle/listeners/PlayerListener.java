package vontus.magicbottle.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vontus.magicbottle.LegacyBottle;
import vontus.magicbottle.Plugin;

// Per-player state: legacy bottle migration on join and cleanup on leave
public class PlayerListener implements Listener {
	private final Plugin plugin;
	private final ClickCooldown cooldown;

	public PlayerListener(Plugin plugin, ClickCooldown cooldown) {
		this.plugin = plugin;
		this.cooldown = cooldown;
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e) {
		LegacyBottle.migrateInventory(e.getPlayer().getInventory());
		LegacyBottle.migrateInventory(e.getPlayer().getEnderChest());
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent e) {
		forget(e.getPlayer());
	}

	@EventHandler
	public void onPlayerKicked(PlayerKickEvent e) {
		forget(e.getPlayer());
	}

	private void forget(Player p) {
		cooldown.clear(p);
		plugin.autoEnabled.remove(p);
		plugin.autoRepairFeedback.clear(p);
	}
}
