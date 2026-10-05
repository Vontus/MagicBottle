package vontus.magicbottle.listeners;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Repairing in the anvil with a MagicBottle as the second item. Vanilla would consume the second slot when the result is
// taken (the bottle would vanish), so the click on the result is cancelled and the transaction is done here.
public class AnvilListener implements Listener {
	private static final int RESULT_SLOT = 2;

	private final Plugin plugin;

	public AnvilListener(Plugin plugin) {
		this.plugin = plugin;
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
		int spent = new MagicBottle(inv.getSecondItem().clone()).repair(result, true);
		// Only the preview has this line: taking the result recalculates it from the first slot
		result.editMeta(meta -> {
			List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
			lore.add(Messages.renderItemText(Messages.texts.messages.repair.anvilCost,
					Placeholder.unparsed("xp", Utils.roundInt(spent)),
					Placeholder.unparsed("levels", Utils.roundDouble(Exp.getLevelFromExp(spent)))));
			meta.lore(lore);
		});
		e.setResult(result);
		e.getView().setRepairCost(0);
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

	// A damaged item that can be repaired in the first slot and a usable MagicBottle in the second
	private static boolean isBottleRepair(AnvilInventory inv) {
		ItemStack first = inv.getFirstItem();
		return Config.repairEnabled && first != null && Config.canRepair(first)
				&& MagicBottle.isUsableMagicBottle(inv.getSecondItem());
	}
}
