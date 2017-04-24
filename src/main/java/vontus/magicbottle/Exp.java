package vontus.magicbottle;

import org.bukkit.entity.Player;

// From EssentialsX
public class Exp {
	// This method is used to update both the recorded total experience and
	// displayed total experience.
	// We reset both types to prevent issues.
	public static void setPoints(final Player player, final int exp) {
		if (exp < 0) {
			throw new IllegalArgumentException("Experience is negative!");
		}
		player.setExp(0);
		player.setLevel(0);
		player.setTotalExperience(0);

		// This following code is technically redundant now, as bukkit now
		// calculates levels more or less correctly
		// At larger numbers however... player.getExp(3000), only seems to give
		// 2999, putting the below calculations off.
		int amount = exp;
		while (amount > 0) {
			final int expToLevel = getExpAtLevel(player);
			amount -= expToLevel;
			if (amount >= 0) {
				// give until next level
				player.giveExp(expToLevel);
			} else {
				// give the rest
				amount += expToLevel;
				player.giveExp(amount);
				amount = 0;
			}
		}
	}

	private static Integer getExpAtLevel(final Player player) {
		return getExpToLvlUp(player.getLevel());
	}

	// new Exp Math from 1.8
	public static Integer getExpToLvlUp(final int fromLevel) {
		if (fromLevel <= 15) {
			return (2 * fromLevel) + 7;
		}
		if ((fromLevel >= 16) && (fromLevel <= 30)) {
			return (5 * fromLevel) - 38;
		}
		return (9 * fromLevel) - 158;

	}

	public static Double getLevelFromExp(double exp) {
		if (exp < 7)
			return exp / 7;
		else if (exp <= 352)
			return quadraticEquationGreatestRoot(1, 6, -exp);
		else if (exp <= 1507)
			return quadraticEquationGreatestRoot(2.5, -40.5, 360 - exp);
		else
			return quadraticEquationGreatestRoot(4.5, -162.5, 2220 - exp);
	}

	// This method is required because the bukkit player.getTotalExperience()
	// method, shows exp that has been 'spent'.
	// Without this people would be able to use exp and then still sell it.
	public static Integer getPoints(final Player player) {
		int exp = Math.round(getExpAtLevel(player) * player.getExp());
		int currentLevel = player.getLevel();

		exp += getExpAtLevel(currentLevel);

		if (exp < 0) {
			exp = Integer.MAX_VALUE;
		}
		return exp;
	}

	public static Integer getExpAtLevel(int level) {
		int exp = 0;
		while (level > 0) {
			level--;
			exp += getExpToLvlUp(level);
		}
		return exp;
	}

	public static Integer getExpToLevel(final Player player, int level) {
		return getExpAtLevel(level) - getPoints(player);
	}
	
	public static int floorLevel(final Player player, int round) {
		round = (int) Math.pow(10, round);
		double level = getLevelFromExp(getPoints(player));
		return (int) ((Math.ceil(level) - round) / round) * round;
	}
	
	public static int ceilingLevel(final Player player, int round) {
		round = (int) Math.pow(10, round);
		double level = getLevelFromExp(getPoints(player));
		return (int) ((Math.floor(level) + round) / round) * round;
	}

	private static Double quadraticEquationGreatestRoot(double a, double b, double c) {
		double root1, root2; // This is now a double, too.
		root1 = (-b + Math.sqrt(Math.pow(b, 2) - 4 * a * c)) / (2 * a);
		root2 = (-b - Math.sqrt(Math.pow(b, 2) - 4 * a * c)) / (2 * a);
		return Math.max(root1, root2);
	}
}
