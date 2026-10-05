package vontus.magicbottle.listeners;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.RecipeMenu;
import vontus.magicbottle.config.Config;

// Inventories where the plugin must keep items from moving: the read-only recipe menu and the anvil/brewing stand
public class InventoryListener implements Listener {
	private static final int ANVIL_BOTTLE_SLOT = 1;

	// The recipe menu is read-only. Every click is cancelled while it is the top inventory, including the ones
	// in the player's own inventory (shift-click, number keys, offhand swap, double click collecting items...)
	@EventHandler(priority = EventPriority.LOWEST)
	public void onClickRecipeMenu(InventoryClickEvent e) {
		if (RecipeMenu.isRecipeMenu(e.getView().getTopInventory())) {
			e.setResult(Event.Result.DENY);
			e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onDragRecipeMenu(InventoryDragEvent e) {
		if (RecipeMenu.isRecipeMenu(e.getView().getTopInventory())) {
			e.setResult(Event.Result.DENY);
			e.setCancelled(true);
		}
	}

	// Nothing in it is real, so it is emptied before the server can return any item to the player
	@EventHandler(priority = EventPriority.MONITOR)
	public void onCloseRecipeMenu(InventoryCloseEvent e) {
		if (RecipeMenu.isRecipeMenu(e.getInventory())) {
			e.getInventory().clear();
		}
	}

	// Bottles can't be used as brewing stand items (dragon's breath is a brewing ingredient and its exp would be lost),
	// so every route into it is blocked: shift-clicking a bottle (moving them around the player's own inventory is fine),
	// the cursor, number keys and offhand swap onto a top slot, dragging and hoppers. The anvil is the same, except for
	// its second slot: a bottle goes there to repair
	// an item (see AnvilListener), but never in the first one.
	private static boolean isBlockedInventory(InventoryType type) {
		return type == InventoryType.ANVIL || type == InventoryType.BREWING;
	}

	// Whether a bottle may be put in this raw slot of the top inventory
	private static boolean acceptsBottle(InventoryView view, int rawSlot) {
		return view.getType() == InventoryType.ANVIL && Config.repairEnabled && rawSlot == ANVIL_BOTTLE_SLOT;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onClickInventory(InventoryClickEvent e) {
		InventoryView view = e.getView();
		if (!isBlockedInventory(view.getType())) {
			return;
		}
		boolean inTop = e.getRawSlot() >= 0 && e.getRawSlot() < view.getTopInventory().getSize();
		if (inTop && acceptsBottle(view, e.getRawSlot())) {
			return;
		}
		boolean bottle = MagicBottle.isMagicBottle(e.getCurrentItem());
		// Moving a bottle around the player's own inventory is harmless; only shift-click would send it to the top inventory
		if (!inTop && !e.getClick().isShiftClick()) {
			bottle = false;
		}
		if (!bottle && inTop) {
			PlayerInventory inv = e.getWhoClicked().getInventory();
			bottle = switch (e.getClick()) {
				case NUMBER_KEY -> e.getHotbarButton() >= 0 && MagicBottle.isMagicBottle(inv.getItem(e.getHotbarButton()));
				case SWAP_OFFHAND -> MagicBottle.isMagicBottle(inv.getItemInOffHand());
				default -> MagicBottle.isMagicBottle(e.getCursor());
			};
		}
		if (bottle) {
			e.setCancelled(true);
			// Vanilla's shift-click would use the first slot, so the bottle is moved to the second one by hand
			if (!inTop && acceptsBottle(view, ANVIL_BOTTLE_SLOT) && view.getTopInventory() instanceof AnvilInventory anvil
					&& anvil.getSecondItem() == null && e.getCurrentItem().getAmount() == 1) {
				anvil.setSecondItem(e.getCurrentItem());
				e.setCurrentItem(null);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDragInventory(InventoryDragEvent e) {
		if (!isBlockedInventory(e.getView().getType()) || !MagicBottle.isMagicBottle(e.getOldCursor())) {
			return;
		}
		int top = e.getView().getTopInventory().getSize();
		if (e.getRawSlots().stream().anyMatch(slot -> slot < top && !acceptsBottle(e.getView(), slot))) {
			e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onMoveItem(InventoryMoveItemEvent e) {
		if (isBlockedInventory(e.getDestination().getType()) && MagicBottle.isMagicBottle(e.getItem())) {
			e.setCancelled(true);
		}
	}
}
