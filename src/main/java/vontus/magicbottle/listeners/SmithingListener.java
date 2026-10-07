package vontus.magicbottle.listeners;

import org.bukkit.Keyed;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Recipes;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.config.Config;

// Applying upgrades in the smithing table: a bottle as the base and the upgrade's ingredient as the addition
public class SmithingListener implements Listener {
	private final Plugin plugin;

	public SmithingListener(Plugin plugin) {
		this.plugin = plugin;
	}

	// The recipe of the upgrade matches any dragon's breath, so it is the one that decides: the result is rebuilt from the
	// bottle in the base slot (keeping its exp and upgrades), and cleared if the base isn't a bottle, already has the
	// upgrade, or the upgrade is disabled or not allowed to the player.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepareSmithing(PrepareSmithingEvent e) {
		if (!(e.getInventory().getRecipe() instanceof Keyed recipe)) {
			return;
		}
		Upgrade upgrade = Recipes.getUpgrade(plugin, recipe.getKey());
		if (upgrade == null) {
			return;
		}
		ItemStack base = e.getInventory().getInputEquipment();
		HumanEntity player = e.getView().getPlayer();
		if (!MagicBottle.isMagicBottle(base) || !Config.isUpgradeEnabled(upgrade) || !player.hasPermission(upgrade.permission())) {
			e.setResult(null);
			return;
		}
		MagicBottle bottle = new MagicBottle(base.clone());
		if (bottle.hasUpgrade(upgrade)) {
			e.setResult(null);
			return;
		}
		bottle.addUpgrade(upgrade);
		ItemStack result = bottle.getItem();
		// The table consumes one bottle, even if the base is a stack
		result.setAmount(1);
		e.setResult(result);
	}
}
