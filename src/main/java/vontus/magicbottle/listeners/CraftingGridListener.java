package vontus.magicbottle.listeners;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Recipes;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

import java.util.function.Consumer;

// Everything that happens in crafting grids and crafters: depositing/withdrawing with a lone bottle and the new bottle recipe
public class CraftingGridListener implements Listener {
	private final Plugin plugin;

	public CraftingGridListener(Plugin plugin) {
		this.plugin = plugin;
	}

	// Depositing and withdrawing in a crafting grid aren't registered recipes, and vanilla's result slot doesn't consume
	// the ingredients right when no recipe matched (it would duplicate the bottle). So vanilla must never handle a
	// click on the result while a lone bottle is in the grid: the transaction is done here, from the current state.
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onClickCraftResult(InventoryClickEvent e) {
		InventoryType invType = e.getView().getType();
		if (e.getSlotType() != InventoryType.SlotType.RESULT
				|| (invType != InventoryType.CRAFTING && invType != InventoryType.WORKBENCH)
				|| !(e.getView().getTopInventory() instanceof CraftingInventory inv)) {
			return;
		}
		ItemStack lone = getLoneBottle(inv);
		// Middle click only clones the result in creative, without consuming anything, so vanilla can handle it
		if (lone == null || e.getClick() == ClickType.MIDDLE) {
			return;
		}

		e.setCancelled(true);
		Player player = (Player) e.getWhoClicked();
		plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);

		// Pick the destination before changing anything, so no exp moves if the bottle can't be delivered
		Consumer<ItemStack> destination = ResultSlot.destination(e, player);
		if (destination == null) {
			return;
		}
		ItemStack result = fillOrPour(player, lone.clone());
		if (result == null) {
			return;
		}

		inv.setMatrix(new ItemStack[inv.getMatrix().length]);
		inv.setResult(null);
		destination.accept(result);
	}

	// Deposits all the player's exp into the bottle or withdraws all of it, if still allowed. Returns the resulting bottle.
	private ItemStack fillOrPour(Player player, ItemStack bottleItem) {
		MagicBottle bottle = new MagicBottle(bottleItem);
		if (bottle.isEmpty()) {
			if (!canFillInGrid(player) || !bottle.deposit(player, Exp.getPoints(player))) {
				return null;
			}
		} else {
			if (!canPourInGrid(player)) {
				return null;
			}
			bottle.withdraw(player, bottle.getExp());
		}
		return bottle.getItem();
	}

	// Crafters have no player, so they can't have their permissions checked: the new bottle recipe is only allowed if
	// the config allows it. Depositing and withdrawing aren't recipes, so crafters can't do them.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onCrafterCraft(CrafterCraftEvent e) {
		if (e.getRecipe().getKey().equals(Recipes.getKey(plugin, Recipes.nameBottle))) {
			// Unlike in the crafting grid (isEmptyBottleRecipe), nothing else stops a MagicBottle from being used up
			// as an ingredient here
			if (Config.settings.recipe.bottle.allowCrafters && !containsMagicBottle(e.getBlock())) {
				e.setResult(new MagicBottle(0).getItem());
			} else {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepareCraft(PrepareItemCraftEvent event) {
		CraftingInventory inv = event.getInventory();
		Player player = (Player) event.getView().getPlayer();
		ItemStack lone = getLoneBottle(inv);
		if (lone != null) {
			// This event also fires when no recipe matched, which is always the case for depositing and withdrawing
			inv.setResult(getGridPreview(new MagicBottle(lone.clone()), player));
		} else if (event.getRecipe() != null && MagicBottle.isMagicBottle(event.getRecipe().getResult())) {
			if (!isEmptyBottleRecipe(event.getRecipe(), inv) || !player.hasPermission(Config.permCraft)) {
				inv.setResult(null);
			}
		}
	}

	// The result was already removed by onPrepareCraft if the new bottle can't be crafted, so vanilla does the craft
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onCraft(CraftItemEvent e) {
		if (MagicBottle.isMagicBottle(e.getRecipe().getResult())) {
			if (!isEmptyBottleRecipe(e.getRecipe(), e.getInventory())) {
				e.setCancelled(true);
			} else if (Utils.getMaterial(e.getCurrentItem()) != Material.AIR) {
				SoundEffect.newBottle((Player) e.getView().getPlayer());
			}
		}
	}

	private boolean canFillInGrid(Player player) {
		return Config.settings.recipe.deposit && player.hasPermission(Config.permDeposit) && Exp.getPoints(player) > 0;
	}

	private boolean canPourInGrid(Player player) {
		return Config.settings.recipe.withdraw && player.hasPermission(Config.permWithdraw);
	}

	// What depositing or withdrawing the lone bottle of a crafting grid would give, or null if the player can't do it
	private ItemStack getGridPreview(MagicBottle ingredient, Player player) {
		if (ingredient.isEmpty()) {
			// No result if the deposit would be refused (the bottle has no room or the cost leaves nothing)
			int gain = ingredient.getDepositGain(player, Exp.getPoints(player));
			if (canFillInGrid(player) && gain > 0) {
				return new MagicBottle(gain).getItem();
			}
		} else if (canPourInGrid(player)) {
			return new MagicBottle(0).getItem();
		}
		return null;
	}

	// The only item in the grid, if it's a single MagicBottle (not a stack)
	private ItemStack getLoneBottle(CraftingInventory inv) {
		ItemStack lone = null;
		for (ItemStack i : inv.getMatrix()) {
			if (Utils.getMaterial(i) != Material.AIR) {
				if (lone != null) {
					return null;
				}
				lone = i;
			}
		}
		if (lone != null && lone.getAmount() == 1 && MagicBottle.isMagicBottle(lone)) {
			return lone;
		}
		return null;
	}

	// The new bottle recipe can match anywhere in the grid (and mirrored), so it's identified by its key. A MagicBottle
	// can still match one of its ingredients (e.g. dragon_breath), but it must never be used up to craft a new one.
	private boolean isEmptyBottleRecipe(Recipe recipe, CraftingInventory inv) {
		if (!(recipe instanceof Keyed keyed) || !keyed.getKey().equals(Recipes.getKey(plugin, Recipes.nameBottle))) {
			return false;
		}
		for (ItemStack item : inv.getMatrix()) {
			if (MagicBottle.isMagicBottle(item)) {
				return false;
			}
		}
		return true;
	}

	// The ingredients are still in the crafter when CrafterCraftEvent is called
	private boolean containsMagicBottle(Block block) {
		if (block.getState(false) instanceof Crafter crafter) {
			for (ItemStack item : crafter.getInventory()) {
				if (MagicBottle.isMagicBottle(item)) {
					return true;
				}
			}
			return false;
		}
		return true;
	}
}
