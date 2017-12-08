package vontus.magicbottle.cauldron;

import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitRunnable;
import vontus.magicbottle.effects.Effects;

public class CheckItemTask extends BukkitRunnable {
	private Item item;

	public CheckItemTask(Item item) {
		this.item = item;
	}

	@Override
	public void run() {
		if (item.isOnGround()) {
			this.cancel();
			MagicCauldron mc = MagicCauldron.getCauldronAt(item.getLocation());
			if (mc != null) {
				if (mc.addItem(item.getItemStack())) {
					Effects.addIngredientToCauldron(item.getLocation());
				}
			}
		}
	}
}
