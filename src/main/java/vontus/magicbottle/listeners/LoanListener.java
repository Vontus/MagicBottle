package vontus.magicbottle.listeners;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vontus.magicbottle.XpLoan;

// The exp a bottle lends (XpLoan) goes back as soon as the player can no longer use it, whatever the workstation: the
// player closes it, dies or leaves. What a crash or a stop left in the player goes back when they join.
public class LoanListener implements Listener {
	private final XpLoan loan;

	public LoanListener(XpLoan loan) {
		this.loan = loan;
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		loan.settle(e.getPlayer(), false);
	}

	// First of the handlers, before anything else reads the player's exp
	@EventHandler(priority = EventPriority.LOWEST)
	public void onQuit(PlayerQuitEvent e) {
		loan.settle(e.getPlayer(), false);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onKick(PlayerKickEvent e) {
		loan.settle(e.getPlayer(), false);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onClose(InventoryCloseEvent e) {
		if (e.getPlayer() instanceof Player player) {
			loan.settle(player, true);
		}
	}

	// A lethal hit is the last chance before the game computes what the player drops and loses: the damage event comes
	// before the death. A totem of undying makes it a false alarm, which only returns the loan early.
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDamage(EntityDamageEvent e) {
		if (e.getEntity() instanceof Player player && loan.hasLoan(player)
				&& player.getHealth() - e.getFinalDamage() <= 0) {
			loan.settle(player, false);
		}
	}

	// In case the death comes without a damage event the plugin saw (other plugins kill players too). What the player
	// drops is computed from their level, so it is lowered to what the level without the loan gives (vanilla: 7 per level,
	// up to 100).
	@EventHandler(priority = EventPriority.LOWEST)
	public void onDeath(PlayerDeathEvent e) {
		Player player = e.getEntity();
		if (loan.hasLoan(player)) {
			loan.settle(player, false);
			e.setDroppedExp(Math.min(e.getDroppedExp(), Math.min(player.getLevel() * 7, 100)));
		}
	}
}
