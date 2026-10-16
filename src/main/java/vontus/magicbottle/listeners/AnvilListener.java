package vontus.magicbottle.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// The anvil and MagicBottles: repairing with a bottle as the second item, and paying the level cost of any other operation
// with a bottle from the inventory. Vanilla would consume the second slot when the repair is taken (the bottle would
// vanish), so the click on that result is cancelled and the transaction is done here. The level cost is lent to the
// player by the bottle (XpLoan), so vanilla's own take charges it.
public class AnvilListener implements Listener {
	private static final int BOTTLE_SLOT = 1;
	private static final int RESULT_SLOT = 2;

	private final Plugin plugin;

	public AnvilListener(Plugin plugin) {
		this.plugin = plugin;
	}

	// Whether a bottle may be put in this raw slot of the top inventory: only the second slot of the anvil, to repair with it
	static boolean acceptsBottle(InventoryView view, int rawSlot) {
		return view.getType() == InventoryType.ANVIL && Config.repairEnabled && rawSlot == BOTTLE_SLOT;
	}

	// Vanilla has no result for a bottle in the second slot, so the preview is set here. It costs no levels, so the
	// repair cost is 0 (the result is taken by hand, vanilla's own take never runs for it).
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepareAnvil(PrepareAnvilEvent e) {
		AnvilInventory inv = e.getInventory();
		if (!isBottleRepair(inv) || !e.getView().getPlayer().hasPermission(Config.permRepair)) {
			return;
		}
		ItemStack result = inv.getFirstItem().clone();
		MagicBottle bottle = new MagicBottle(inv.getSecondItem().clone());
		double levelBefore = bottle.getLevel();
		int spent = bottle.repair(result, true);
		// Only the preview has this line: taking the result recalculates it from the first slot
		result.editMeta(meta -> {
			List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
			lore.add(Messages.renderItemText(Messages.texts.messages.repair.anvilCost,
					Placeholder.unparsed("xp", Utils.roundInt(spent)),
					Placeholder.unparsed("xp_left", Utils.roundInt(bottle.getExp())),
					Placeholder.unparsed("levels", Utils.roundDouble(levelBefore - bottle.getLevel())),
					Placeholder.unparsed("levels_left", Utils.roundInt((int) bottle.getLevel()))));
			meta.lore(lore);
		});
		e.setResult(result);
		e.getView().setRepairCost(0);
	}

	// Vanilla's shift-click would put the bottle in the first slot (InventoryListener cancels the click), so it is moved
	// to the second one by hand
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onShiftClickBottle(InventoryClickEvent e) {
		ItemStack item = e.getCurrentItem();
		if (e.getClick().isShiftClick() && e.getView().getTopInventory() instanceof AnvilInventory inv
				&& e.getRawSlot() >= inv.getSize() && acceptsBottle(e.getView(), BOTTLE_SLOT)
				&& MagicBottle.isMagicBottle(item) && item.getAmount() == 1 && inv.getSecondItem() == null) {
			inv.setSecondItem(item);
			e.setCurrentItem(null);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onClickResult(InventoryClickEvent e) {
		if (e.getRawSlot() != RESULT_SLOT || !(e.getView().getTopInventory() instanceof AnvilInventory inv)
				|| !isBottleRepair(inv)) {
			return;
		}
		// Middle click only clones the result in creative, without consuming anything, so vanilla can handle it
		if (e.getClick() == ClickType.MIDDLE) {
			return;
		}

		e.setCancelled(true);
		Player player = (Player) e.getWhoClicked();
		plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);

		// Pick the destination before changing anything, so no exp is spent if the item can't be delivered
		Consumer<ItemStack> destination = ResultSlot.destination(e, player);
		if (destination == null || !player.hasPermission(Config.permRepair)) {
			return;
		}
		ItemStack repaired = inv.getFirstItem().clone();
		MagicBottle bottle = new MagicBottle(inv.getSecondItem().clone());
		if (bottle.repair(repaired, true) == 0) {
			return;
		}

		// A bottle that runs out stays in the slot as an empty bottle; the player keeps it
		inv.setSecondItem(bottle.getItem());
		inv.setFirstItem(null);
		inv.setResult(null);
		destination.accept(repaired);
		SoundEffect.pourBottle(player);
	}

	// The anvil works out the result and its level cost whenever its slots or the new name change. Read at MONITOR, once
	// nothing else changes the cost (the bottle repair sets it to 0); only the player's exp is changed, not the event.
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPrepareSpend(PrepareAnvilEvent e) {
		lendCost((Player) e.getView().getPlayer(), e.getView(), e.getResult());
	}

	// Taking the result, or moving items around, may leave no result that needs the loan: it is checked again next tick, in
	// case no PrepareAnvilEvent follows
	@EventHandler(priority = EventPriority.MONITOR)
	public void onClickWithLoan(InventoryClickEvent e) {
		checkLoanLater(e);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDragWithLoan(InventoryDragEvent e) {
		checkLoanLater(e);
	}

	private void checkLoanLater(InventoryInteractEvent e) {
		if (!(e.getView() instanceof AnvilView) || !(e.getWhoClicked() instanceof Player player)
				|| !plugin.loan.hasLoan(player)) {
			return;
		}
		// Closed meanwhile: closing settles it (LoanListener)
		plugin.getServer().getScheduler().runTask(plugin, () -> {
			if (player.isOnline() && player.getOpenInventory() instanceof AnvilView view) {
				lendCost(player, view, view.getTopInventory().getResult());
			}
		});
	}

	/**
	 * Settles the loan made for what the anvil showed before (reporting what the bottle paid, if the result was taken), and
	 * lends what the result shown now needs, if the player can't afford its cost: the bottle raises the player to exactly
	 * the cost's level with an empty bar (XpLoan), so the anvil shows the cost affordable and vanilla's own take, which
	 * spends the levels and keeps the bar, charges {@code Exp.getExpAtLevel(cost)} points instead of the levels above the
	 * player's own. Own exp goes first. No loan for a cost the player can pay, "Too Expensive!" (no result can be taken),
	 * creative players (they don't pay) or the bottle repair (it costs no levels).
	 */
	private void lendCost(Player player, AnvilView view, @Nullable ItemStack result) {
		int cost = view.getRepairCost();
		boolean pays = Config.settings.spend.enabled && result != null && !ResultSlot.isAir(result) && cost > 0
				&& cost < view.getMaximumRepairCost() && player.getGameMode() != GameMode.CREATIVE
				&& player.hasPermission(Config.permSpend);
		int target = Exp.getExpAtLevel(cost);
		// The loan for this cost is untouched (nothing taken, nothing changed): settling and lending again would rewrite the
		// bottle on every click
		if (pays && plugin.loan.hasLoan(player) && Exp.getPoints(player) == target) {
			return;
		}
		plugin.loan.settle(player, true);
		if (pays && player.getLevel() < cost) {
			plugin.loan.borrow(player, target, AnvilListener::reportSpent);
		}
	}

	// Right away, unlike the upgrades' DebouncedFeedback: an anvil operation is a single action, not one of many in a row
	private static void reportSpent(Player player, int xp) {
		player.sendActionBar(Messages.render(Messages.texts.messages.spend.fromBottle,
				Placeholder.unparsed("xp", String.valueOf(xp))));
	}

	// A damaged item that can be repaired in the first slot and a usable MagicBottle in the second
	private static boolean isBottleRepair(AnvilInventory inv) {
		ItemStack first = inv.getFirstItem();
		return Config.repairEnabled && first != null && Config.canRepair(first)
				&& MagicBottle.isUsableMagicBottle(inv.getSecondItem());
	}
}
