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

	// Bottles can't be used as anvil/brewing stand items (dragon's breath is a brewing ingredient and its exp would be
	// lost), so every route into those inventories is blocked: picking up or shift-clicking a bottle, the cursor,
	// number keys and offhand swap onto a top slot, dragging and hoppers.
	private static boolean isBlockedInventory(InventoryType type) {
		return type == InventoryType.ANVIL || type == InventoryType.BREWING;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onClickInventory(InventoryClickEvent e) {
		if (!isBlockedInventory(e.getView().getType())) {
			return;
		}
		boolean inTop = e.getRawSlot() >= 0 && e.getRawSlot() < e.getView().getTopInventory().getSize();
		boolean bottle = MagicBottle.isMagicBottle(e.getCurrentItem());
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
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDragInventory(InventoryDragEvent e) {
		if (!isBlockedInventory(e.getView().getType()) || !MagicBottle.isMagicBottle(e.getOldCursor())) {
			return;
		}
		int top = e.getView().getTopInventory().getSize();
		if (e.getRawSlots().stream().anyMatch(slot -> slot < top)) {
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
