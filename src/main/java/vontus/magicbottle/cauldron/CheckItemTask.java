package vontus.magicbottle.cauldron;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.effects.Effects;

import java.util.ArrayList;
import java.util.List;

public class CheckItemTask extends BukkitRunnable {
	private Player player;
	private Item item;
	private int timesTried;
	private static int maxTryTimes = 200;
	public static List<Item> itemsBeingChecked = new ArrayList<>();

	public CheckItemTask(Item item, Player player) {
		this.player = player;
		this.item = item;
		itemsBeingChecked.add(item);
		timesTried = 0;
	}

	@Override
	public void run() {
		if (item.isOnGround()) {
			this.cancel();
			MagicCauldron mc = MagicCauldron.getCauldronAt(item.getLocation());
			if (mc != null) {
				if (mc.addItem(item.getItemStack(), player)) {
					Effects.addIngredientToCauldron(item.getLocation());
				}
			} else {
				itemsBeingChecked.remove(item);
			}
		} else {
			timesTried++;
			if (timesTried > maxTryTimes) {
				Plugin.logger.warning("Stopped checking item at " + item.getLocation().toString());
				this.cancel();
			}
		}
	}
}
