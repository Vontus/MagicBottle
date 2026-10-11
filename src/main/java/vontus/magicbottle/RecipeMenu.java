package vontus.magicbottle;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only inventory that shows the new bottle recipe (if enabled) and, in a row each below it, the upgrades the player
 * may apply (a bottle plus the ingredient, as in the smithing table). The display items are never meant to leave it, so
 * it is identified by its holder: InventoryListener cancels every click and drag on any inventory holding a RecipeMenu
 * (even the player's own inventory clicks, which could shift, swap or collect items from it), and it is cleared on close.
 */
public class RecipeMenu implements InventoryHolder {
	private static final int ROW = 9;
	private static final int GRID_SIZE = 3;
	private static final int MAX_ROWS = 6;
	// The grid goes in the columns 1 to 3, leaving one free on the left
	private static final int GRID_COLUMN = 1;
	private static final int ARROW_COLUMN = 4;
	private static final int RESULT_COLUMN = 6;
	// In an upgrade row the bottle takes the first column of the grid and the ingredient the second
	private static final int BASE_COLUMN = 1;
	private static final int INGREDIENT_COLUMN = 2;
	// Ticks between each item shown for an ingredient that accepts several (an item tag)
	private static final long CYCLE_PERIOD = 20;

	private final Inventory inventory;
	private final boolean showNewBottle;
	private final List<Upgrade> upgrades;

	private RecipeMenu(boolean showNewBottle, List<Upgrade> upgrades) {
		this.showNewBottle = showNewBottle;
		this.upgrades = upgrades;
		int rows = (showNewBottle ? GRID_SIZE : 0) + upgrades.size();
		inventory = Bukkit.createInventory(this, rows * ROW, Messages.render(Messages.texts.messages.recipe.title));
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	/** Opens the menu; returns false (opening nothing) if there is no recipe to show the player. */
	public static boolean open(Plugin plugin, Player player) {
		boolean showNewBottle = Config.recipeNewBottleEnabled;
		List<Upgrade> upgrades = upgradesFor(player, showNewBottle);
		if (!showNewBottle && upgrades.isEmpty()) {
			return false;
		}
		RecipeMenu menu = new RecipeMenu(showNewBottle, upgrades);
		menu.fill();
		menu.show(0);
		player.openInventory(menu.inventory);
		menu.startCycling(plugin, player);
		return true;
	}

	// The enabled upgrades the player may apply, as many as fit in the inventory
	private static List<Upgrade> upgradesFor(Player player, boolean showNewBottle) {
		List<Upgrade> list = new ArrayList<>();
		for (Upgrade upgrade : Upgrade.values()) {
			if (Config.upgradeIngredients.containsKey(upgrade) && player.hasPermission(upgrade.permission())) {
				list.add(upgrade);
			}
		}
		int room = MAX_ROWS - (showNewBottle ? GRID_SIZE : 0);
		return list.subList(0, Math.min(list.size(), room));
	}

	public static boolean isRecipeMenu(Inventory inventory) {
		return inventory != null && inventory.getHolder(false) instanceof RecipeMenu;
	}

	private int upgradesFirstRow() {
		return showNewBottle ? GRID_SIZE : 0;
	}

	// The bottles, the arrows and the empty slots never change
	private void fill() {
		ItemStack filler = hiddenTooltip(Material.GRAY_STAINED_GLASS_PANE);
		for (int slot = 0; slot < inventory.getSize(); slot++) {
			inventory.setItem(slot, filler);
		}
		if (showNewBottle) {
			int middle = GRID_SIZE / 2 * ROW;
			inventory.setItem(middle + ARROW_COLUMN, hiddenTooltip(Material.ARROW));
			inventory.setItem(middle + RESULT_COLUMN, MagicBottle.createDisplayItem());
		}
		for (int i = 0; i < upgrades.size(); i++) {
			int start = (upgradesFirstRow() + i) * ROW;
			inventory.setItem(start + BASE_COLUMN, MagicBottle.createDisplayItem());
			inventory.setItem(start + ARROW_COLUMN, hiddenTooltip(Material.ARROW));
			inventory.setItem(start + RESULT_COLUMN, MagicBottle.createDisplayItem(upgrades.get(i)));
		}
	}

	private static ItemStack hiddenTooltip(Material material) {
		ItemStack item = new ItemStack(material);
		item.editMeta(meta -> meta.setHideTooltip(true));
		return item;
	}

	// Draws the ingredients, using the given step to pick which item to show of the ingredients that accept several
	private void show(int step) {
		if (showNewBottle) {
			showNewBottle(step);
		}
		for (int i = 0; i < upgrades.size(); i++) {
			RecipeChoice choice = Config.upgradeIngredients.get(upgrades.get(i));
			inventory.setItem((upgradesFirstRow() + i) * ROW + INGREDIENT_COLUMN, displayItem(choice, step));
		}
	}

	private void showNewBottle(int step) {
		String[] shape = Config.recipeNewBottleShape;
		int rowOffset = (GRID_SIZE - shape.length) / 2;
		int columnOffset = (GRID_SIZE - shape[0].length()) / 2;

		for (int row = 0; row < GRID_SIZE; row++) {
			for (int column = 0; column < GRID_SIZE; column++) {
				ItemStack item = null;
				int shapeRow = row - rowOffset;
				int shapeColumn = column - columnOffset;
				if (shapeRow >= 0 && shapeRow < shape.length && shapeColumn >= 0 && shapeColumn < shape[0].length()) {
					item = displayItem(Config.recipeNewBottleIngredients.get(shape[shapeRow].charAt(shapeColumn)), step);
				}
				inventory.setItem(row * ROW + GRID_COLUMN + column, item);
			}
		}
	}

	private static @Nullable ItemStack displayItem(@Nullable RecipeChoice choice, int step) {
		if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
			List<Material> materials = materialChoice.getChoices();
			return new ItemStack(materials.get(step % materials.size()));
		}
		return null;
	}

	@SuppressWarnings("ReferenceEquality") // the holder is compared by identity: it must be this very menu
	private void startCycling(Plugin plugin, Player player) {
		List<RecipeChoice> choices = new ArrayList<>(upgrades.stream().map(Config.upgradeIngredients::get).toList());
		if (showNewBottle) {
			choices.addAll(Config.recipeNewBottleIngredients.values());
		}
		boolean cycles = choices.stream()
				.anyMatch(choice -> choice instanceof RecipeChoice.MaterialChoice materialChoice && materialChoice.getChoices().size() > 1);
		if (!cycles) {
			return;
		}
		BukkitTask[] task = new BukkitTask[1];
		int[] step = {0};
		task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
			// Stops when it is closed (InventoryListener also clears it, so there is nothing else to clean up)
			if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder(false) != this) {
				task[0].cancel();
			} else {
				show(++step[0]);
			}
		}, CYCLE_PERIOD, CYCLE_PERIOD);
	}
}
