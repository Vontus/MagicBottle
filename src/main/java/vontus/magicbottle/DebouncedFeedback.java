package vontus.magicbottle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import vontus.magicbottle.config.Messages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Action bar message with the exp the upgrades (auto-repair, collect) have spent or stored. They happen all the time, so
 * the message is debounced: it is only sent once the player has gone DELAY ticks without any, with the totals meanwhile.
 * That way it doesn't flood the action bar while mining or fighting. All the channels share the message and the timer,
 * since the action bar shows a single message: the channels with something to report are joined in one line, in the order
 * they were created. Each channel's message has the placeholder {@code <xp>}.
 */
public class DebouncedFeedback {
	private static final long DELAY = 60;

	private final Plugin plugin;
	private final List<Channel> channels = new ArrayList<>();
	private final HashMap<UUID, Pending> pending = new HashMap<>();

	private static class Pending {
		final int[] totals;
		BukkitTask task;

		Pending(int channels) {
			totals = new int[channels];
		}
	}

	/** A kind of exp reported in the shared message. Channels must be created before anything is reported. */
	public class Channel {
		private final int index = channels.size();
		private final Supplier<String> message;
		private final BooleanSupplier enabled;

		private Channel(Supplier<String> message, BooleanSupplier enabled) {
			this.message = message;
			this.enabled = enabled;
		}

		public void add(Player player, int xp) {
			if (xp > 0 && enabled.getAsBoolean()) {
				report(player, this, xp);
			}
		}
	}

	DebouncedFeedback(Plugin plugin) {
		this.plugin = plugin;
	}

	// Both are read each time, so reloading the config applies
	Channel channel(Supplier<String> message, BooleanSupplier enabled) {
		Channel channel = new Channel(message, enabled);
		channels.add(channel);
		return channel;
	}

	private void report(Player player, Channel channel, int xp) {
		Pending p = pending.computeIfAbsent(player.getUniqueId(), id -> new Pending(channels.size()));
		p.totals[channel.index] += xp;
		if (p.task != null) {
			p.task.cancel();
		}
		p.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
			pending.remove(player.getUniqueId());
			List<Component> parts = new ArrayList<>();
			for (Channel c : channels) {
				if (p.totals[c.index] > 0) {
					parts.add(Messages.render(c.message.get(), Placeholder.unparsed("xp", String.valueOf(p.totals[c.index]))));
				}
			}
			player.sendActionBar(Component.join(JoinConfiguration.separator(
					Messages.render(Messages.texts.messages.feedbackSeparator)), parts));
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
