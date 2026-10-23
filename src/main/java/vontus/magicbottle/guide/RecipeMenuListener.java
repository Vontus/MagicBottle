package vontus.magicbottle.guide;

import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

// Keeps the recipe menu (/mb recipe) read-only
public class RecipeMenuListener implements Listener {
    // Every click is cancelled while it is the top inventory, including the ones in the inventory of the player
    // (shift-click, number keys, offhand swap, double click collecting items...).
    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent e) {
        if (RecipeMenu.isRecipeMenu(e.getView().getTopInventory())) {
            e.setResult(Event.Result.DENY);
            e.setCancelled(true);
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
