package vontus.magicbottle.listeners;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.GameMode;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.EnchantmentView;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.util.Exp;

// The enchanting table and MagicBottles: the client and the server refuse an option whose level the player doesn't have
// before any event is fired, so the real level has to be there when the offers are computed. The bottle lends the
// points (XpLoan); LoanListener returns them when the player closes the table, dies or leaves, and this listener when
// the item is gone or enchanted.
public class EnchantListener implements Listener {
	// The highest cost vanilla offers; also the most a loan can be (1395 points)
	private static final int MAX_LEVEL = 30;

	private final Plugin plugin;

	public EnchantListener(Plugin plugin) {
		this.plugin = plugin;
	}

	// Fired every time the item or the lapis change, and when the table is opened. A loan from the previous offers is
	// settled first (it also returns the points of an enchantment that was just done), then the best offer the player
	// can't afford is paid for, if a bottle covers it.
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepare(PrepareItemEnchantEvent e) {
		Player player = e.getEnchanter();
		boolean pays = !e.isCancelled() && Config.settings.spend.enabled && player.hasPermission(Config.permSpend)
				&& player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR;
		// The level without the loan from the previous offers: this event fires several times in a row, and the loan
		// itself must not make a later offer look affordable
		int level = Exp.getLevelFromExp(plugin.loan.ownPoints(player)).intValue();
		EnchantmentOffer[] offers = e.getOffers();
		for (int slot = offers.length - 1; pays && slot >= 0; slot--) {
			if (offers[slot] == null || offers[slot].getCost() <= 0) {
				continue;
			}
			// What vanilla checks when the button is clicked: the cost of the offer and the levels it spends (button + 1)
			int required = Math.max(offers[slot].getCost(), slot + 1);
			if (required > MAX_LEVEL) {
				continue;
			}
			if (level >= required) {
				break;
			}
			int target = Exp.getExpAtLevel(required);
			// The loan for this level is untouched (nothing spent, nothing changed): settling and lending again would rewrite
			// the bottle on every change of the lapis
			if (plugin.loan.hasLoan(player) && Exp.getPoints(player) == target) {
				return;
			}
			plugin.loan.settle(player, true);
			if (plugin.loan.borrow(player, target, EnchantListener::reportSpent)) {
				return;
			}
		}
		plugin.loan.settle(player, true);
	}

	// Right away, like the anvil: an enchantment is a single action, not one of many in a row
	private static void reportSpent(Player player, int xp) {
		player.sendActionBar(Messages.render(Messages.texts.messages.spend.enchant,
				Placeholder.unparsed("xp", String.valueOf(xp))));
	}

	// The loan was made for the highest offer the player couldn't afford, but they picked one: vanilla is about to spend
	// its levels, which must come off the level that offer needs (what a player of that level would pay), so the loan is
	// lowered to it first. Then vanilla spends the levels and re-computes the offers; an enchanted item has none, and
	// then no PrepareItemEnchantEvent comes, so the loan is checked on the next tick, once the levels are spent.
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onEnchant(EnchantItemEvent e) {
		Player player = e.getEnchanter();
		if (plugin.loan.hasLoan(player) && e.getView() instanceof EnchantmentView view) {
			EnchantmentOffer offer = view.getOffers()[e.whichButton()];
			int cost = offer == null ? 0 : offer.getCost();
			plugin.loan.lower(player, Exp.getExpAtLevel(Math.max(cost, e.whichButton() + 1)));
		}
		checkLater(player);
	}

	// Taking the item out (or the lapis, or moving it around) fires no enchanting event when it leaves no offers
	@EventHandler(priority = EventPriority.MONITOR)
	public void onClick(InventoryClickEvent e) {
		if (e.getWhoClicked() instanceof Player player) {
			checkLater(player);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onDrag(InventoryDragEvent e) {
		if (e.getWhoClicked() instanceof Player player) {
			checkLater(player);
		}
	}

	// Next tick, when the click or the enchantment is done: settles unless the table still shows an offer for an item.
	// Closed meanwhile: closing settles it (LoanListener)
	private void checkLater(Player player) {
		if (!plugin.loan.hasLoan(player)) {
			return;
		}
		plugin.getServer().getScheduler().runTask(plugin, () -> {
			if (player.isOnline() && plugin.loan.hasLoan(player) && !showsOffers(player)) {
				plugin.loan.settle(player, true);
			}
		});
	}

	private static boolean showsOffers(Player player) {
		if (!(player.getOpenInventory() instanceof EnchantmentView view)) {
			return false;
		}
		ItemStack item = view.getTopInventory().getItem(0);
		if (item == null || item.getType().isAir()) {
			return false;
		}
		for (EnchantmentOffer offer : view.getOffers()) {
			if (offer != null && offer.getCost() > 0) {
				return true;
			}
		}
		return false;
	}
}
