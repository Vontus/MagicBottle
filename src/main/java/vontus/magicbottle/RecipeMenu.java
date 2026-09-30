package vontus.magicbottle;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.scheduler.BukkitTask;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

import java.util.List;

/**
 * Read-only inventory that shows the new bottle recipe. The display items are never meant to leave it, so it is
 * identified by its holder: Events cancels every click and drag on any inventory holding a RecipeMenu (even the
 * player's own inventory clicks, which could shift, swap or collect items from it), and it is cleared on close.
 */
public class RecipeMenu implements InventoryHolder {
	private static final int SIZE = 27;
	private static final int GRID_SIZE = 3;
	private static final int ARROW_SLOT = 13;
	private static final int RESULT_SLOT = 15;
	// Ticks between each item shown for an ingredient that accepts several (an item tag)
	private static final long CYCLE_PERIOD = 20;

	private final Inventory inventory;

	private RecipeMenu() {
		inventory = Bukkit.createInventory(this, SIZE, Messages.recipeTitle);
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	public static void open(Plugin plugin, Player player) {
		RecipeMenu menu = new RecipeMenu();
		menu.fill();
		menu.show(0);
		player.openInventory(menu.inventory);
		menu.startCycling(plugin, player);
	}

	public static boolean isRecipeMenu(Inventory inventory) {
		return inventory != null && inventory.getHolder(false) instanceof RecipeMenu;
	}

	// The bottle, the arrow and the empty slots never change
	private void fill() {
		ItemStack filler = hiddenTooltip(Material.GRAY_STAINED_GLASS_PANE);
		for (int slot = 0; slot < SIZE; slot++) {
			inventory.setItem(slot, filler);
		}
		inventory.setItem(ARROW_SLOT, hiddenTooltip(Material.ARROW));
		inventory.setItem(RESULT_SLOT, MagicBottle.createDisplayItem());
	}

	private static ItemStack hiddenTooltip(Material material) {
		ItemStack item = new ItemStack(material);
		item.editMeta(meta -> meta.setHideTooltip(true));
		return item;
	}

	// Draws the ingredients, using the given step to pick which item to show of the ingredients that accept several
	private void show(int step) {
		String[] shape = Config.recipeNewBottleShape;
		int rowOffset = (GRID_SIZE - shape.length) / 2;
		int columnOffset = (GRID_SIZE - shape[0].length()) / 2;

		for (int row = 0; row < GRID_SIZE; row++) {
			for (int column = 0; column < GRID_SIZE; column++) {
				ItemStack item = null;
				int shapeRow = row - rowOffset;
				int shapeColumn = column - columnOffset;
				if (shapeRow >= 0 && shapeRow < shape.length && shapeColumn >= 0 && shapeColumn < shape[0].length()) {
					RecipeChoice choice = Config.recipeNewBottleIngredients.get(shape[shapeRow].charAt(shapeColumn));
					if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
						List<Material> materials = materialChoice.getChoices();
						item = new ItemStack(materials.get(step % materials.size()));
					}
				}
				// The grid goes in the columns 1 to 3, leaving one free on the left
				inventory.setItem(row * 9 + 1 + column, item);
			}
		}
	}

	private void startCycling(Plugin plugin, Player player) {
		boolean cycles = Config.recipeNewBottleIngredients.values().stream()
				.anyMatch(choice -> choice instanceof RecipeChoice.MaterialChoice materialChoice && materialChoice.getChoices().size() > 1);
		if (!cycles) {
			return;
		}
		BukkitTask[] task = new BukkitTask[1];
		int[] step = {0};
		task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
			// Stops when it is closed (Events also clears it, so there is nothing else to clean up)
			if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder(false) != this) {
				task[0].cancel();
			} else {
				show(++step[0]);
			}
		}, CYCLE_PERIOD, CYCLE_PERIOD);
	}
}
