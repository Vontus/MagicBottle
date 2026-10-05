package vontus.magicbottle.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import vontus.magicbottle.util.Utils;

import java.util.function.Consumer;

// Clicks on a result slot that the plugin takes by hand (crafting grid, anvil), because vanilla would consume the bottle
final class ResultSlot {
	private ResultSlot() {
	}

	// Where a click on the result puts the item, like vanilla would, or null if it has nowhere to go
	static Consumer<ItemStack> destination(InventoryClickEvent e, Player player) {
		PlayerInventory inv = player.getInventory();
		return switch (e.getClick()) {
			case LEFT, RIGHT -> isAir(player.getItemOnCursor()) ? player::setItemOnCursor : null;
			case SHIFT_LEFT, SHIFT_RIGHT -> {
				int slot = inv.firstEmpty();
				yield slot >= 0 ? item -> inv.setItem(slot, item) : null;
			}
			case NUMBER_KEY -> {
				int slot = e.getHotbarButton();
				yield slot >= 0 && slot < 9 && isAir(inv.getItem(slot)) ? item -> inv.setItem(slot, item) : null;
			}
			case SWAP_OFFHAND -> isAir(inv.getItemInOffHand()) ? inv::setItemInOffHand : null;
			default -> null;
		};
	}

	static boolean isAir(ItemStack item) {
		return Utils.getMaterial(item) == Material.AIR;
	}
}
