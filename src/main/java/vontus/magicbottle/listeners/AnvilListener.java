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

// What a MagicBottle does in the anvil: repairing an item with it in the second slot, and merging two full bottles.
// Vanilla would consume the bottles when the result is taken (a repair would make the bottle vanish, a merge would leave
// the inputs in an inconsistent state), so the click on the result is cancelled and the transaction is done here.
// Any other operation is paid from a bottle with a loan (XpLoan) when the player lacks the levels: the bottle raises the
// player to the cost, so vanilla's own take charges it.
//
// First slot | Second slot | Result
// damaged item accepted by Config.canRepair | MagicBottle with exp | repair
// MagicBottle | MagicBottle | merge, if both are full
// MagicBottle | anything else | none (vanilla would rename it, but print() rewrites the name)
public class AnvilListener implements Listener {
	private static final int RESULT_SLOT = 2;

	private final Plugin plugin;

	public AnvilListener(Plugin plugin) {
		this.plugin = plugin;
	}

	// Vanilla has no result for a bottle in the anvil, so the preview is set here. It costs no levels, so the repair
	// cost is 0 (the result is taken by hand, vanilla's own take never runs for it).
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepareAnvil(PrepareAnvilEvent e) {
		AnvilInventory inv = e.getInventory();
		Player player = (Player) e.getView().getPlayer();
		if (MagicBottle.isMagicBottle(inv.getFirstItem())) {
			prepareMerge(e, inv, player);
		} else if (isBottleRepair(inv) && player.hasPermission(Config.permRepair)) {
			prepareRepair(e, inv);
		}
	}

	private void prepareRepair(PrepareAnvilEvent e, AnvilInventory inv) {
		ItemStack result = inv.getFirstItem().clone();
		MagicBottle bottle = new MagicBottle(inv.getSecondItem().clone());
		double levelBefore = bottle.getLevel();
		int spent = bottle.repair(result, true);
		// Only the preview has this line: taking the result recalculates it from the first slot
		addPreviewLine(result, Messages.renderItemText(Messages.texts.messages.repair.anvilCost,
				Placeholder.unparsed("xp", Utils.roundInt(spent)),
				Placeholder.unparsed("xp_left", Utils.roundInt(bottle.getExp())),
				Placeholder.unparsed("levels", Utils.roundDouble(levelBefore - bottle.getLevel())),
				Placeholder.unparsed("levels_left", Utils.roundInt((int) bottle.getLevel()))));
		e.setResult(result);
		e.getView().setRepairCost(0);
	}

	// A bottle in the first slot never has a vanilla result (that would be renaming it), only the merge one
	private void prepareMerge(PrepareAnvilEvent e, AnvilInventory inv, Player player) {
		MagicBottle merged = merge(inv, player);
		if (merged == null) {
			e.setResult(null);
			return;
		}
		ItemStack result = merged.getItem();
		// What the merge consumes, not all the exp of the bottles: what is above their capacity stays in the new one
		long consumed = MagicBottle.getMergeCost(new MagicBottle(inv.getFirstItem().clone()), new MagicBottle(inv.getSecondItem().clone()));
		addPreviewLine(result, Messages.renderItemText(Messages.texts.messages.merge.anvil,
				Placeholder.unparsed("xp", Utils.roundInt((int) consumed))));
		e.setResult(result);
		e.getView().setRepairCost(0);
	}

	private static void addPreviewLine(ItemStack item, Component line) {
		item.editMeta(meta -> {
			List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
			lore.add(line);
			meta.lore(lore);
		});
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onClickResult(InventoryClickEvent e) {
		if (e.getRawSlot() != RESULT_SLOT || !(e.getView().getTopInventory() instanceof AnvilInventory inv)) {
			return;
		}
		boolean merge = MagicBottle.isMagicBottle(inv.getFirstItem());
		if (!merge && !isBottleRepair(inv)) {
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
		if (destination == null) {
			return;
		}
		if (merge) {
			takeMerge(inv, player, destination);
		} else {
			takeRepair(inv, player, destination);
		}
	}

	private void takeMerge(AnvilInventory inv, Player player, Consumer<ItemStack> destination) {
		MagicBottle merged = merge(inv, player);
		if (merged == null) {
			return;
		}
		// Both bottles are used up: their exp is the price
		inv.setFirstItem(null);
		inv.setSecondItem(null);
		inv.setResult(null);
		destination.accept(merged.getItem());
		SoundEffect.merge(player);
	}

	private void takeRepair(AnvilInventory inv, Player player, Consumer<ItemStack> destination) {
		if (!player.hasPermission(Config.permRepair)) {
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

	// The empty bottle that merging the two of the anvil gives, or null if the player can't: merging is off or not
	// allowed for them, a slot isn't a single bottle, one isn't full or the result would go over the hard cap
	private static @Nullable MagicBottle merge(AnvilInventory inv, Player player) {
		ItemStack first = inv.getFirstItem();
		ItemStack second = inv.getSecondItem();
		if (!Config.settings.merge.enabled || !player.hasPermission(Config.permMerge)
				|| !isSingleBottle(first) || !isSingleBottle(second)) {
			return null;
		}
		return MagicBottle.merge(new MagicBottle(first.clone()), new MagicBottle(second.clone()));
	}

	private static boolean isSingleBottle(ItemStack item) {
		return MagicBottle.isMagicBottle(item) && item.getAmount() == 1;
	}

	// A damaged item that can be repaired in the first slot and a usable MagicBottle in the second
	private static boolean isBottleRepair(AnvilInventory inv) {
		ItemStack first = inv.getFirstItem();
		return Config.repairEnabled && first != null && Config.canRepair(first)
				&& MagicBottle.isUsableMagicBottle(inv.getSecondItem());
	}
}
