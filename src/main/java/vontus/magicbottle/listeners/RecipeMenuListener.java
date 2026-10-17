package vontus.magicbottle.listeners;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import vontus.magicbottle.RecipeMenu;

// Keeps the recipe menu (/mb recipes) read-only and switches its tabs
public class RecipeMenuListener implements Listener {
    // Every click is cancelled while it is the top inventory, including the ones in the player's own inventory
    // (shift-click, number keys, offhand swap, double click collecting items...).
    // A plain left or right click on a tab button only switches the tab, which redraws the menu and moves nothing.
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        if (top.getHolder(false) instanceof RecipeMenu menu) {
            e.setResult(Event.Result.DENY);
            e.setCancelled(true);
            if (e.getRawSlot() >= 0
                    && e.getRawSlot() < top.getSize()
                    && (e.getClick() == ClickType.LEFT || e.getClick() == ClickType.RIGHT)) {
                menu.click(e.getRawSlot());
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent e) {
        if (RecipeMenu.isRecipeMenu(e.getView().getTopInventory())) {
            e.setResult(Event.Result.DENY);
            e.setCancelled(true);
        }
    }

    // Nothing in it is real, so it is emptied before the server can return any item to the player
    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (RecipeMenu.isRecipeMenu(e.getInventory())) {
            e.getInventory().clear();
        }
    }
}
