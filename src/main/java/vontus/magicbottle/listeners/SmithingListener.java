package vontus.magicbottle.listeners;

import org.bukkit.Keyed;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
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
	// upgrade, or the upgrade is disabled or not allowed to the player. The preview marks the line of the new upgrade.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepareSmithing(PrepareSmithingEvent e) {
		ItemStack result = buildResult(e.getInventory(), e.getView().getPlayer(), true);
		if (result != null || upgradeOf(e.getInventory()) != null) {
			e.setResult(result);
		}
	}

	// The preview is the very item the player takes, so the mark is replaced by the real result before it is taken
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onClickResult(InventoryClickEvent e) {
		if (e.getInventory() instanceof SmithingInventory inv && e.getSlotType() == InventoryType.SlotType.RESULT) {
			ItemStack result = buildResult(inv, e.getWhoClicked(), false);
			if (result != null) {
				inv.setResult(result);
			}
		}
	}

	private Upgrade upgradeOf(SmithingInventory inv) {
		return inv.getRecipe() instanceof Keyed recipe ? Recipes.getUpgrade(plugin, recipe.getKey()) : null;
	}

	// The bottle with the upgrade applied, or null if it can't be (or the recipe isn't an upgrade's)
	private ItemStack buildResult(SmithingInventory inv, HumanEntity player, boolean preview) {
		Upgrade upgrade = upgradeOf(inv);
		ItemStack base = inv.getInputEquipment();
		if (upgrade == null || !MagicBottle.isMagicBottle(base) || !Config.isUpgradeEnabled(upgrade)
				|| !player.hasPermission(upgrade.permission())) {
			return null;
		}
		MagicBottle bottle = new MagicBottle(base.clone());
		if (bottle.hasUpgrade(upgrade)) {
			return null;
		}
		bottle.addUpgrade(upgrade);
		ItemStack result = preview ? bottle.createUpgradePreview(upgrade) : bottle.getItem();
		// The table consumes one bottle, even if the base is a stack
		result.setAmount(1);
		return result;
	}
}
