package vontus.magicbottle;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Read-only inventory that shows what can be done with MagicBottles in each workstation, one tab each: the new bottle
 * recipe (crafting table), the upgrades (smithing table) and the repair (anvil). Only the tabs the player can use are
 * there, and the buttons in the bottom row switch between them. The display items are never meant to leave it, so it
 * is identified by its holder: InventoryListener cancels every click and drag on any inventory holding a RecipeMenu
 * (even the player's own inventory clicks, which could shift, swap or collect items from it), only switching the tab
 * when a button is clicked, and it is cleared on close.
 */
public class RecipeMenu implements InventoryHolder {
	private enum Tab {
		CRAFTING(Material.CRAFTING_TABLE),
		SMITHING(Material.SMITHING_TABLE),
		ANVIL(Material.ANVIL);

		final Material icon;

		Tab(Material icon) {
			this.icon = icon;
		}

		String description() {
			Messages.Recipe texts = Messages.texts.messages.recipe;
			return switch (this) {
				case CRAFTING -> texts.crafting;
				case SMITHING -> texts.smithing;
				case ANVIL -> texts.anvil;
			};
		}
	}

	private static final int ROW = 9;
	private static final int GRID_SIZE = 3;
	// Below the content, a row of dark panes separates it from the row of tab buttons
	private static final int BOTTOM_ROWS = 2;
	// Rows a chest can have, keeping the bottom ones
	private static final int MAX_CONTENT_ROWS = 6 - BOTTOM_ROWS;
	// The grid goes in the columns 1 to 3, the arrow and the result leave one free column on each side of them
	private static final int GRID_COLUMN = 1;
	private static final int ARROW_COLUMN = 5;
	private static final int RESULT_COLUMN = 7;
	// In the smithing and anvil rows the two inputs start where the grid does, leaving two free columns before the arrow
	private static final int FIRST_INPUT_COLUMN = 1;
	private static final int SECOND_INPUT_COLUMN = 2;
	// Ticks between each item shown for an ingredient that accepts several (an item tag)
	private static final long CYCLE_PERIOD = 20;
	// What the bottle shown in the anvil holds: any amount that repairs something
	private static final int REPAIR_EXAMPLE_LEVEL = 10;

	private final Inventory inventory;
	private final List<Tab> tabs;
	private final List<Upgrade> upgrades;
	private final int contentRows;
	private Tab tab;
	private int step;

	private RecipeMenu(List<Tab> tabs, List<Upgrade> upgrades) {
		this.tabs = tabs;
		this.upgrades = upgrades;
		tab = tabs.getFirst();
		contentRows = Math.max(GRID_SIZE, upgrades.size());
		if (contentRows > MAX_CONTENT_ROWS) {
			throw new IllegalStateException("the smithing tab has no room for " + upgrades.size() + " upgrades");
		}
		inventory = Bukkit.createInventory(this, (contentRows + BOTTOM_ROWS) * ROW, Messages.render(Messages.texts.messages.recipe.title));
	}

	@Override
	public Inventory getInventory() {
		return inventory;
	}

	/** Opens the menu; returns false (opening nothing) if there is no recipe to show the player. */
	public static boolean open(Plugin plugin, Player player) {
		List<Upgrade> upgrades = upgradesFor(player);
		List<Tab> tabs = new ArrayList<>();
		if (Config.recipeNewBottleEnabled && player.hasPermission(Config.permCraft)) {
			tabs.add(Tab.CRAFTING);
		}
		if (!upgrades.isEmpty()) {
			tabs.add(Tab.SMITHING);
		}
		if (Config.repairEnabled && player.hasPermission(Config.permRepair)) {
			tabs.add(Tab.ANVIL);
		}
		if (tabs.isEmpty()) {
			return false;
		}
		RecipeMenu menu = new RecipeMenu(tabs, upgrades);
		menu.draw();
		player.openInventory(menu.inventory);
		menu.startCycling(plugin, player);
		return true;
	}

	// The enabled upgrades the player may apply
	private static List<Upgrade> upgradesFor(Player player) {
		List<Upgrade> list = new ArrayList<>();
		for (Upgrade upgrade : Upgrade.values()) {
			if (Config.upgradeIngredients.containsKey(upgrade) && player.hasPermission(upgrade.permission())) {
				list.add(upgrade);
			}
		}
		return list;
	}

	public static boolean isRecipeMenu(Inventory inventory) {
		return inventory != null && inventory.getHolder(false) instanceof RecipeMenu;
	}

	/** Switches to the tab whose button is in this slot of the menu, if any. */
	public void click(int slot) {
		for (int i = 0; i < tabs.size(); i++) {
			if (slot == buttonSlot(i) && tabs.get(i) != tab) {
				tab = tabs.get(i);
				draw();
				return;
			}
		}
	}

	// The buttons are centered in the last row, with a free slot between each two
	private int buttonSlot(int index) {
		return (contentRows + BOTTOM_ROWS - 1) * ROW + ROW / 2 - (tabs.size() - 1) + index * 2;
	}

	private void draw() {
		ItemStack filler = hiddenTooltip(Material.GRAY_STAINED_GLASS_PANE);
		for (int slot = 0; slot < contentRows * ROW; slot++) {
			inventory.setItem(slot, filler);
		}
		drawButtons();
		switch (tab) {
			case CRAFTING -> {
				int middle = rowStart(GRID_SIZE / 2, GRID_SIZE);
				inventory.setItem(middle + ARROW_COLUMN, hiddenTooltip(Material.ARROW));
				inventory.setItem(middle + RESULT_COLUMN, MagicBottle.createDisplayItem(0));
			}
			case SMITHING -> {
				for (int i = 0; i < upgrades.size(); i++) {
					int start = rowStart(i, upgrades.size());
					inventory.setItem(start + FIRST_INPUT_COLUMN, MagicBottle.createDisplayItem(0));
					inventory.setItem(start + ARROW_COLUMN, hiddenTooltip(Material.ARROW));
					inventory.setItem(start + RESULT_COLUMN, MagicBottle.createDisplayItem(0, upgrades.get(i)));
				}
			}
			case ANVIL -> drawRepair(rowStart(0, 1));
		}
		drawIngredients();
	}

	private void drawButtons() {
		ItemStack filler = hiddenTooltip(Material.BLACK_STAINED_GLASS_PANE);
		for (int slot = contentRows * ROW; slot < inventory.getSize(); slot++) {
			inventory.setItem(slot, filler);
		}
		for (int i = 0; i < tabs.size(); i++) {
			Tab button = tabs.get(i);
			ItemStack item = new ItemStack(button.icon);
			item.editMeta(meta -> {
				meta.lore(List.of(Messages.renderItemText(button.description())));
				meta.setEnchantmentGlintOverride(button == tab);
			});
			inventory.setItem(buttonSlot(i), item);
		}
	}

	// The first slot of the row-th of several rows, centered in the content area
	private int rowStart(int row, int rows) {
		return ((contentRows - rows) / 2 + row) * ROW;
	}

	// A damaged pickaxe that the configured enchantment lets repair, a bottle, and the pickaxe repaired
	private void drawRepair(int start) {
		ItemStack damaged = new ItemStack(Material.DIAMOND_PICKAXE);
		Enchantment enchantment = Config.settings.repair.enchantment.enchantment();
		if (enchantment != null) {
			damaged.addUnsafeEnchantment(enchantment, enchantment.getMaxLevel());
		}
		ItemStack repaired = damaged.clone();
		Utils.setDamage(damaged, damaged.getType().getMaxDurability() / 2);
		inventory.setItem(start + FIRST_INPUT_COLUMN, damaged);
		inventory.setItem(start + SECOND_INPUT_COLUMN, MagicBottle.createDisplayItem(Exp.getExpAtLevel(REPAIR_EXAMPLE_LEVEL)));
		inventory.setItem(start + ARROW_COLUMN, hiddenTooltip(Material.ARROW));
		inventory.setItem(start + RESULT_COLUMN, repaired);
	}

	private static ItemStack hiddenTooltip(Material material) {
		ItemStack item = new ItemStack(material);
		item.editMeta(meta -> meta.setHideTooltip(true));
		return item;
	}

	// Draws the ingredients of the tab, using the step to pick which item to show of the ingredients that accept several
	private void drawIngredients() {
		switch (tab) {
			case CRAFTING -> drawNewBottleGrid();
			case SMITHING -> {
				for (int i = 0; i < upgrades.size(); i++) {
					RecipeChoice choice = Config.upgradeIngredients.get(upgrades.get(i));
					inventory.setItem(rowStart(i, upgrades.size()) + SECOND_INPUT_COLUMN, displayItem(choice));
				}
			}
			case ANVIL -> {
				// Its items are fixed
			}
		}
	}

	private void drawNewBottleGrid() {
		String[] shape = Config.recipeNewBottleShape;
		int rowOffset = (GRID_SIZE - shape.length) / 2;
		int columnOffset = (GRID_SIZE - shape[0].length()) / 2;

		for (int row = 0; row < GRID_SIZE; row++) {
			for (int column = 0; column < GRID_SIZE; column++) {
				ItemStack item = null;
				int shapeRow = row - rowOffset;
				int shapeColumn = column - columnOffset;
				if (shapeRow >= 0 && shapeRow < shape.length && shapeColumn >= 0 && shapeColumn < shape[0].length()) {
					item = displayItem(Config.recipeNewBottleIngredients.get(shape[shapeRow].charAt(shapeColumn)));
				}
				inventory.setItem(rowStart(row, GRID_SIZE) + GRID_COLUMN + column, item);
			}
		}
	}

	private @Nullable ItemStack displayItem(@Nullable RecipeChoice choice) {
		if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
			List<Material> materials = materialChoice.getChoices();
			return new ItemStack(materials.get(step % materials.size()));
		}
		return null;
	}

	@SuppressWarnings("ReferenceEquality") // the holder is compared by identity: it must be this very menu
	private void startCycling(Plugin plugin, Player player) {
		List<RecipeChoice> choices = new ArrayList<>(upgrades.stream().map(Config.upgradeIngredients::get).toList());
		if (tabs.contains(Tab.CRAFTING)) {
			choices.addAll(Config.recipeNewBottleIngredients.values());
		}
		boolean cycles = choices.stream()
				.anyMatch(choice -> choice instanceof RecipeChoice.MaterialChoice materialChoice && materialChoice.getChoices().size() > 1);
		if (!cycles) {
			return;
		}
		BukkitTask[] task = new BukkitTask[1];
		task[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
			// Stops when it is closed (InventoryListener also clears it, so there is nothing else to clean up)
			if (!player.isOnline() || player.getOpenInventory().getTopInventory().getHolder(false) != this) {
				task[0].cancel();
			} else {
				step++;
				drawIngredients();
			}
		}, CYCLE_PERIOD, CYCLE_PERIOD);
	}
}
