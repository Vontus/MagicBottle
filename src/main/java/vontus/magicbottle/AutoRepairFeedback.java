package vontus.magicbottle;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

import java.util.HashMap;
import java.util.UUID;

/**
 * Action bar message with the exp spent by auto-repair. Repairing happens every time, but the message is debounced:
 * it is only sent once the player has gone DELAY ticks without spending exp, with the total spent meanwhile. That
 * way it doesn't flood the action bar while mining or fighting.
 */
class AutoRepairFeedback {
	private static final long DELAY = 60;

	private final Plugin plugin;
	private final HashMap<UUID, Pending> pending = new HashMap<>();

	private static class Pending {
		int spent;
		BukkitTask task;
	}

	AutoRepairFeedback(Plugin plugin) {
		this.plugin = plugin;
	}

	void spent(Player player, int xp) {
		if (xp <= 0 || !Config.repairAutoFeedback) {
			return;
		}
		Pending p = pending.computeIfAbsent(player.getUniqueId(), id -> new Pending());
		p.spent += xp;
		if (p.task != null) {
			p.task.cancel();
		}
		p.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
			pending.remove(player.getUniqueId());
			player.sendActionBar(LegacyComponentSerializer.legacySection()
					.deserialize(Messages.repairAutoSpent.replace("[xp]", String.valueOf(p.spent))));
		}, DELAY);
	}

	// Drops the pending message, e.g. when the player leaves
	void clear(Player player) {
		Pending p = pending.remove(player.getUniqueId());
		if (p != null && p.task != null) {
			p.task.cancel();
		}
	}
}
