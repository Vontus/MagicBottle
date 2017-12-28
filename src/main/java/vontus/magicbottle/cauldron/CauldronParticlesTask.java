package vontus.magicbottle.cauldron;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import vontus.magicbottle.effects.Effects;
import vontus.magicbottle.util.TaskStatus;

public class CauldronParticlesTask extends BukkitRunnable {
	private MagicCauldron magicCauldron;
	private TaskStatus status;

	public CauldronParticlesTask(MagicCauldron magicCauldron) {
		this.magicCauldron = magicCauldron;
		status = TaskStatus.NOT_STARTED;
	}

	public TaskStatus getStatus() {
		return status;
	}

	@Override
	public void run() {
		if (magicCauldron.isComplete()) {
			Effects.cauldronComplete(magicCauldron.getLocation());
		} else {
			Effects.activeCauldron(magicCauldron.getLocation());
		}
	}

	@Override
	public synchronized void cancel() throws IllegalStateException {
		super.cancel();
		status = TaskStatus.CANCELLED;
	}

	@Override
	public synchronized BukkitTask runTaskTimer(Plugin plugin, long delay, long period) throws IllegalArgumentException, IllegalStateException {
		BukkitTask bt = super.runTaskTimer(plugin, delay, period);
		status = TaskStatus.RUNNING;
		return bt;
	}
}
