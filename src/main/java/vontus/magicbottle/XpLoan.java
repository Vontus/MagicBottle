package vontus.magicbottle;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import vontus.magicbottle.util.Exp;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.ObjIntConsumer;

/**
 * Exp a bottle lends to a player for a workstation that only lets them use what their real level affords (the anvil). The
 * points live in the player while the workstation is open, so the loan is recorded in the player's persistent data (a
 * crash or restart returns them when the player joins) and settled by {@link #settle}, as soon as the player can no
 * longer use it (see LoanListener).
 * <p>
 * The rule that makes the amount exact: the player is raised to exactly the points of the level the workstation needs, so
 * {@code owed} is the difference. The workstation spends levels, which are taken from the player's points. On settling,
 * the bottle gets back {@code min(owed, the player's points now)}, which is {@code owed} minus what was spent beyond the
 * exp the player had of their own (own exp is spent first). Never more than the player has, so whatever other plugins or
 * orbs did to the player's exp cannot create exp, and the player keeps any exp gained meanwhile. The points go back to
 * the bottle that lent them if it is still in its slot, or else to another one; if no bottle has room for them, the
 * player simply keeps them: exp is never lost. No deposit cost is charged: the points were the bottle's. The player's exp
 * is set silently both ways (no level up sound): they gain nothing.
 */
public final class XpLoan {
	private final NamespacedKey key;
	// The players with a loan this session, so hot events don't read the player's data
	private final Map<UUID, Loan> loans = new HashMap<>();

	// The slot of the bottle that lent the points, and what the workstation tells the player the bottle paid
	private record Loan(int slot, ObjIntConsumer<Player> report) {
	}

	XpLoan(Plugin plugin) {
		key = new NamespacedKey(plugin, "borrowed");
	}

	public boolean hasLoan(Player player) {
		return loans.containsKey(player.getUniqueId());
	}

	/**
	 * Raises the player's points to {@code target} with exp from a bottle, to be returned by {@link #settle}, which passes
	 * what the bottle paid, if anything, to {@code report}. Any earlier loan must have been settled. Returns whether the
	 * player has at least the target points afterwards (taking nothing if no bottle covers the difference).
	 */
	public boolean borrow(Player player, int target, ObjIntConsumer<Player> report) {
		int points = Exp.getPoints(player);
		int need = target - points;
		if (need <= 0) {
			return true;
		}
		if (owed(player) > 0) {
			return false;
		}
		int slot = MagicBottle.lend(player, need);
		if (slot < 0) {
			return false;
		}
		Exp.setPointsSilently(player, points + need);
		player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, need);
		loans.put(player.getUniqueId(), new Loan(slot, report));
		return true;
	}

	/**
	 * Gives back to a bottle what the player owes and still has (see the class comment). Safe to call any time, with or
	 * without a loan, and independent of the config and permissions. {@code report} tells the player how much of the
	 * bottle was spent (not when they are leaving).
	 */
	public void settle(Player player, boolean report) {
		Loan loan = loans.remove(player.getUniqueId());
		int owed = owed(player);
		if (owed <= 0) {
			return;
		}
		int points = Exp.getPoints(player);
		int back = Math.min(owed, points);
		// A loan left by a crash or a stop, settled on join, doesn't know the bottle
		if (back > 0 && MagicBottle.repay(player, back, loan == null ? -1 : loan.slot())) {
			Exp.setPointsSilently(player, points - back);
		}
		player.getPersistentDataContainer().remove(key);
		// What the workstation took from the loan, not what failed to go back (the player keeps those points)
		if (report && loan != null && owed > back) {
			loan.report().accept(player, owed - back);
		}
	}

	/**
	 * Lowers the loan so the player has {@code target} points, or their own exp if that is more (then the whole loan is
	 * returned), giving the difference back to the bottle at once. For a workstation that turns out to need a lower level
	 * than the one the loan was made for (the enchanting table, whose offers are paid for the highest one before the
	 * player picks), right before it spends the levels. If no bottle can take the points back, nothing changes.
	 */
	public void lower(Player player, int target) {
		Loan loan = loans.get(player.getUniqueId());
		int owed = owed(player);
		int points = Exp.getPoints(player);
		int back = Math.min(owed, points - Math.max(points - owed, target));
		if (loan == null || back <= 0 || !MagicBottle.repay(player, back, loan.slot())) {
			return;
		}
		Exp.setPointsSilently(player, points - back);
		if (owed > back) {
			player.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, owed - back);
		} else {
			loans.remove(player.getUniqueId());
			player.getPersistentDataContainer().remove(key);
		}
	}

	/** The player's points without the loan: what they have of their own. */
	public int ownPoints(Player player) {
		return Math.max(0, Exp.getPoints(player) - owed(player));
	}

	private int owed(Player player) {
		return player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, 0);
	}
}
