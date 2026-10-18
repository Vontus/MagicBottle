package vontus.magicbottle.guide;

import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.scheduler.BukkitTask;
import org.jspecify.annotations.Nullable;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

/**
 * Read-only inventory that shows the new bottle recipe: the configured shape (up to 3x3), an arrow and the empty
 * bottle. It is a chest and not a dialog because the recipe needs a grid of real items. The display items are never
 * meant to leave it, so it is identified by its holder: RecipeMenuListener cancels every click and drag on any
 * inventory holding a RecipeMenu (even the clicks in the inventory of the player, which could shift, swap or collect
 * items from it), and it is cleared on close.
 */
public class RecipeMenu implements InventoryHolder {
    private static final int ROW = 9;
    private static final int GRID_SIZE = 3;
    // The grid goes in the columns 1 to 3, the arrow and the result leave one free column on each side of them
    private static final int GRID_COLUMN = 1;
    private static final int ARROW_COLUMN = 5;
    private static final int RESULT_COLUMN = 7;
    // Ticks between each item shown for an ingredient that accepts several (an item tag)
    private static final long CYCLE_PERIOD = 20;

    private final Inventory inventory;
    private int step;

    private RecipeMenu() {
        inventory =
                Bukkit.createInventory(this, GRID_SIZE * ROW, Messages.render(Messages.texts.messages.recipe.title));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /** Whether the player has a recipe to see. */
    public static boolean hasRecipe(Player player) {
        return Config.recipeNewBottleEnabled && player.hasPermission(Config.permCraft);
    }

    /** Opens the menu; returns false (opening nothing) if there is no recipe to show the player. */
    public static boolean open(Plugin plugin, Player player) {
        if (!hasRecipe(player)) {
            return false;
        }
        RecipeMenu menu = new RecipeMenu();
        menu.draw();
        player.openInventory(menu.inventory);
        menu.startCycling(plugin, player);
        return true;
    }

    public static boolean isRecipeMenu(Inventory inventory) {
        return inventory != null && inventory.getHolder(false) instanceof RecipeMenu;
    }

    private void draw() {
        ItemStack filler = hiddenTooltip(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
        int middle = GRID_SIZE / 2 * ROW;
        inventory.setItem(middle + ARROW_COLUMN, arrow());
        inventory.setItem(middle + RESULT_COLUMN, MagicBottle.createDisplayItem(0));
        drawGrid();
    }

    // Named, so it reads as "gives" and not as one more ingredient
    private static ItemStack arrow() {
        ItemStack item = new ItemStack(Material.ARROW);
        item.editMeta(meta -> meta.itemName(Messages.render(Messages.texts.messages.recipe.arrow)));
        return item;
    }

    private static ItemStack hiddenTooltip(Material material) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> meta.setHideTooltip(true));
        return item;
    }

    // Draws the ingredients, using the step to pick which item to show of the ingredients that accept several
    private void drawGrid() {
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
                inventory.setItem(row * ROW + GRID_COLUMN + column, item);
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
        boolean cycles = Config.recipeNewBottleIngredients.values().stream()
                .anyMatch(choice -> choice instanceof RecipeChoice.MaterialChoice materialChoice
                        && materialChoice.getChoices().size() > 1);
        if (!cycles) {
            return;
        }
        BukkitTask[] task = new BukkitTask[1];
        task[0] = plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> {
                            // Stops when it is closed (RecipeMenuListener also clears it, so there is nothing else
                            // to clean up)
                            if (!player.isOnline()
                                    || player.getOpenInventory()
                                                    .getTopInventory()
                                                    .getHolder(false)
                                            != this) {
                                task[0].cancel();
                            } else {
                                step++;
                                drawGrid();
                            }
                        },
                        CYCLE_PERIOD,
                        CYCLE_PERIOD);
    }
}
