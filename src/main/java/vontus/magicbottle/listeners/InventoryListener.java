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
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.RecipeMenu;

// Inventories where the plugin must keep items from moving: the read-only recipe menu and the anvil/brewing stand
public class InventoryListener implements Listener {
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
	// its second slot: a bottle goes there to repair an item (see AnvilListener), but never in the first one.
	private static boolean isBlockedInventory(InventoryType type) {
		return type == InventoryType.ANVIL || type == InventoryType.BREWING;
	}

	// Whether this click would put a bottle in the top inventory, or take one out of it, where that isn't allowed
	private static boolean movesBottleIntoTop(InventoryClickEvent e) {
		InventoryView view = e.getView();
		boolean inTop = e.getRawSlot() >= 0 && e.getRawSlot() < view.getTopInventory().getSize();
		if (!inTop) {
			return e.getClick().isShiftClick() && MagicBottle.isMagicBottle(e.getCurrentItem());
		}
		if (AnvilListener.acceptsBottle(view, e.getRawSlot())) {
			return false;
		}
		if (MagicBottle.isMagicBottle(e.getCurrentItem())) {
			return true;
		}
		PlayerInventory inv = e.getWhoClicked().getInventory();
		return switch (e.getClick()) {
			case NUMBER_KEY -> e.getHotbarButton() >= 0 && MagicBottle.isMagicBottle(inv.getItem(e.getHotbarButton()));
			case SWAP_OFFHAND -> MagicBottle.isMagicBottle(inv.getItemInOffHand());
			default -> MagicBottle.isMagicBottle(e.getCursor());
		};
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onClickInventory(InventoryClickEvent e) {
		if (isBlockedInventory(e.getView().getType()) && movesBottleIntoTop(e)) {
			e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDragInventory(InventoryDragEvent e) {
		if (!isBlockedInventory(e.getView().getType()) || !MagicBottle.isMagicBottle(e.getOldCursor())) {
			return;
		}
		int top = e.getView().getTopInventory().getSize();
		if (e.getRawSlots().stream().anyMatch(slot -> slot < top && !AnvilListener.acceptsBottle(e.getView(), slot))) {
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
