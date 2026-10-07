package vontus.magicbottle;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import vontus.magicbottle.config.Messages;

import java.util.HashMap;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Action bar message with the exp an upgrade (auto-repair, collect) has spent or stored. Those happen all the time, so
 * the message is debounced: it is only sent once the player has gone DELAY ticks without any, with the total meanwhile.
 * That way it doesn't flood the action bar while mining or fighting. The message has the placeholder {@code <xp>}.
 */
public class DebouncedFeedback {
	private static final long DELAY = 60;

	private final Plugin plugin;
	private final Supplier<String> message;
	private final BooleanSupplier enabled;
	private final HashMap<UUID, Pending> pending = new HashMap<>();

	private static class Pending {
		int total;
		BukkitTask task;
	}

	// Both are read each time, so reloading the config applies
	DebouncedFeedback(Plugin plugin, Supplier<String> message, BooleanSupplier enabled) {
		this.plugin = plugin;
		this.message = message;
		this.enabled = enabled;
	}

	public void add(Player player, int xp) {
		if (xp <= 0 || !enabled.getAsBoolean()) {
			return;
		}
		Pending p = pending.computeIfAbsent(player.getUniqueId(), id -> new Pending());
		p.total += xp;
		if (p.task != null) {
			p.task.cancel();
		}
		p.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
			pending.remove(player.getUniqueId());
			player.sendActionBar(Messages.render(message.get(), Placeholder.unparsed("xp", String.valueOf(p.total))));
		}, DELAY);
	}

	// Drops the pending message, e.g. when the player leaves
	public void clear(Player player) {
		Pending p = pending.remove(player.getUniqueId());
		if (p != null && p.task != null) {
			p.task.cancel();
		}
	}
}
