package vontus.magicbottle.util;

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

	// Like setPoints, but setting the level and the bar directly: giving the exp level by level, as setPoints does, makes
	// vanilla play the level up sound (for nearby players too) on every multiple of 5, and a loan (XpLoan) gives nothing
	public static void setPointsSilently(final Player player, final int exp) {
		if (exp < 0) {
			throw new IllegalArgumentException("Experience is negative!");
		}
		int level = 0;
		int left = exp;
		while (left >= getExpToLvlUp(level)) {
			left -= getExpToLvlUp(level);
			level++;
		}
		player.setLevel(level);
		player.setExp((float) left / getExpToLvlUp(level));
		player.setTotalExperience(exp);
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
	
	public static void givePoints(final Player player, final int points) {
		// long, since the sum can exceed the int range
		setPoints(player, (int) Math.min((long) getPoints(player) + points, Integer.MAX_VALUE));
	}
	
	public static void takePoints(final Player player, final int points) {
		setPoints(player, getPoints(player) - points);
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

	public static Integer getExpAtLevel(int level) {
		return (int) Math.min(getExpAtLevelExact(level), Integer.MAX_VALUE);
	}

	// The points needed to reach the level, without the int limit (closed form of the sum of getExpToLvlUp)
	public static long getExpAtLevelExact(long level) {
		if (level <= 0) {
			return 0;
		} else if (level <= 16) {
			return level * level + 6 * level;
		} else if (level <= 31) {
			return level * (5 * level - 81) / 2 + 360;
		}
		return level * (9 * level - 325) / 2 + 2220;
	}

	// The whole level that the points reach. Unlike (int) getLevelFromExp it can't be one short because of a rounding
	// error in the floating point math (e.g. with exactly the points of a level).
	public static int getLevelFloor(long points) {
		if (points <= 0) {
			return 0;
		}
		long level = (long) Math.floor(getLevelFromExp((double) points));
		while (level > 0 && getExpAtLevelExact(level) > points) {
			level--;
		}
		while (getExpAtLevelExact(level + 1) <= points) {
			level++;
		}
		return (int) level;
	}

	public static Integer getExpToLevel(final Player player, int level) {
		return getExpAtLevel(level) - getPoints(player);
	}
	
	public static int floorLevel(final Player player, int round) {
		return approxLevel(player, - (int) Math.pow(10, round));
	}
	
	public static int ceilingLevel(final Player player, int round) {
		return approxLevel(player, + (int) Math.pow(10, round));
	}
	
	private static int approxLevel(final Player player, int round) {
		double level = getLevelFromExp(getPoints(player));
		return (int) Math.floor((level + round) / round) * round;
	}

	private static Double quadraticEquationGreatestRoot(double a, double b, double c) {
		double root1, root2; // This is now a double, too.
		root1 = (-b + Math.sqrt(Math.pow(b, 2) - 4 * a * c)) / (2 * a);
		root2 = (-b - Math.sqrt(Math.pow(b, 2) - 4 * a * c)) / (2 * a);
		return Math.max(root1, root2);
	}
}
