package vontus.magicbottle;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.Recipe;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class Events implements Listener {
	private static final int CLICK_COOLDOWN_TICKS = 3;

	// Tick of each player's last accepted bottle click
	private Map<UUID, Integer> lastClick;
	private Plugin plugin;

	Events(Plugin plugin) {
		this.plugin = plugin;
		this.lastClick = new HashMap<>();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onClickInventory(InventoryClickEvent e) {
		InventoryType invType = e.getView().getType();
		if (invType == InventoryType.ANVIL || invType == InventoryType.BREWING) {
			e.setCancelled(MagicBottle.isMagicBottle(e.getCurrentItem()));
		}
	}

	// Filling and pouring in a crafting grid aren't registered recipes, and vanilla's result slot doesn't consume
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
		Consumer<ItemStack> destination = getResultDestination(e, player);
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

	// Where a click on the crafting result puts the bottle, like vanilla would, or null if it has nowhere to go
	private Consumer<ItemStack> getResultDestination(InventoryClickEvent e, Player player) {
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
			case DROP, CONTROL_DROP -> player::dropItem;
			case SWAP_OFFHAND -> isAir(inv.getItemInOffHand()) ? inv::setItemInOffHand : null;
			default -> null;
		};
	}

	// Fills the bottle with all the player's exp or pours all of it, if still allowed. Returns the resulting bottle.
	private ItemStack fillOrPour(Player player, ItemStack bottleItem) {
		MagicBottle bottle = new MagicBottle(bottleItem);
		if (bottle.isEmpty()) {
			if (!canFillInGrid(player)) {
				return null;
			}
			bottle.deposit(player, Exp.getPoints(player));
		} else {
			if (!canPourInGrid(player)) {
				return null;
			}
			bottle.withdraw(player, bottle.getExp());
		}
		return bottle.getItem();
	}

	private static boolean isAir(ItemStack item) {
		return Utils.getMaterial(item) == Material.AIR;
	}

	// Crafters have no player, so they can't have their permissions checked: the new bottle recipe is only allowed if
	// the config allows it. Filling and pouring aren't recipes, so crafters can't do them.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onCrafterCraft(CrafterCraftEvent e) {
		if (e.getRecipe().getKey().equals(Recipes.getKey(plugin, Recipes.nameBottle))) {
			// Unlike in the crafting grid (isEmptyBottleRecipe), nothing else stops a MagicBottle from being used up
			// as an ingredient here
			if (Config.recipeNewBottleAllowCrafters && !containsMagicBottle(e.getBlock())) {
				e.setResult(new MagicBottle(0).getItem());
			} else {
				e.setCancelled(true);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Action act = event.getAction();
		ItemStack item = event.getItem();

		if (MagicBottle.isMagicBottle(item)) {
			MagicBottle mb = new MagicBottle(item);
			if (item.getAmount() == 1 && throttle(player)) {
				if (act == Action.LEFT_CLICK_AIR || act == Action.LEFT_CLICK_BLOCK) {
					onInteractDeposit(mb, player);
				} else if (act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK) {
					onInteractWithdraw(mb, player);
				}
			}

			event.setCancelled(true);
			player.updateInventory();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepareCraft(PrepareItemCraftEvent event) {
		CraftingInventory inv = event.getInventory();
		Player player = (Player) event.getView().getPlayer();
		ItemStack lone = getLoneBottle(inv);
		if (lone != null) {
			// This event also fires when no recipe matched, which is always the case for filling and pouring
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

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onItemDamage(PlayerItemDamageEvent e) {
		Player p = e.getPlayer();
		// Cheap checks first: this fires for every durability loss of every player. It doesn't use the click cooldown,
		// so it neither blocks nor is blocked by bottle clicks.
		if (!Config.repairAutoEnabled || !plugin.autoEnabled.contains(p)) {
			return;
		}
		ItemStack i = e.getItem();
		// 1 exp repairs 2 durability points (like Mending), so only repair when the accumulated damage is odd: an even
		// value would spend 1 exp on a single point. Intentional, not a bug.
		if (i.getDurability() % 2 == 0 || e.isCancelled() || !Config.canRepair(i)) {
			return;
		}
		MagicBottle mb = MagicBottle.getUsableMBInInventory(p.getInventory());
		if (mb == null) {
			return;
		}
		i.setDurability((short) (i.getDurability() + e.getDamage()));
		mb.repair(i, false);
		e.setCancelled(true);
		p.updateInventory();
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent e) {
		LegacyBottle.migrateInventory(e.getPlayer().getInventory());
		LegacyBottle.migrateInventory(e.getPlayer().getEnderChest());
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent e) {
		lastClick.remove(e.getPlayer().getUniqueId());
		plugin.autoEnabled.remove(e.getPlayer());
	}

	@EventHandler
	public void onPlayerKicked(PlayerKickEvent e) {
		lastClick.remove(e.getPlayer().getUniqueId());
		plugin.autoEnabled.remove(e.getPlayer());
	}

	private void onInteractDeposit(MagicBottle bottle, Player p) {
		if (Exp.getPoints(p) > 0) {
			if (p.hasPermission(Config.permDeposit)) {
				int round = p.isSneaking() ? 1 : 0;
				int targetPlayerLevel = Exp.floorLevel(p, round);
				int expToDeposit = Exp.getExpToLevel(p, targetPlayerLevel) * -1;

				bottle.deposit(p, expToDeposit);
			} else
				p.sendMessage(Messages.msgUnauthorizedToDeposit);
		}
	}

	private void onInteractWithdraw(MagicBottle bottle, Player p) {
		if (bottle.getExp() > 0) {
			if (p.hasPermission(Config.permWithdraw)) {
				int round = p.isSneaking() ? 1 : 0;
				int targetPlayerLevel = Exp.ceilingLevel(p, round);
				int expToWithdraw = Exp.getExpToLevel(p, targetPlayerLevel);

				bottle.withdraw(p, expToWithdraw);
			} else {
				p.sendMessage(Messages.msgUnauthorizedToWithdraw);
			}
		}
	}

	private boolean canFillInGrid(Player player) {
		return Config.recipeFill && player.hasPermission(Config.permDeposit) && Exp.getPoints(player) > 0;
	}

	private boolean canPourInGrid(Player player) {
		return Config.recipePour && player.hasPermission(Config.permWithdraw);
	}

	// What filling or pouring the lone bottle of a crafting grid would give, or null if the player can't do it
	private ItemStack getGridPreview(MagicBottle ingredient, Player player) {
		if (ingredient.isEmpty()) {
			if (canFillInGrid(player)) {
				MagicBottle bottle = new MagicBottle(0);
				int playerPoints = Exp.getPoints(player);
				int expCost = (int) Math.round(playerPoints * Config.costPercentageDeposit);
				int maxPoints = bottle.getMaxFillablePoints(player, playerPoints - expCost);
				bottle.setExp(maxPoints);
				return bottle.getItem();
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

	// Bottle clicks are ignored for a few ticks after an accepted one. Returns true (and starts the cooldown) if allowed.
	private boolean throttle(Player p) {
		int now = plugin.getServer().getCurrentTick();
		Integer last = lastClick.get(p.getUniqueId());
		if (last != null && now - last < CLICK_COOLDOWN_TICKS) {
			return false;
		}
		lastClick.put(p.getUniqueId(), now);
		return true;
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
