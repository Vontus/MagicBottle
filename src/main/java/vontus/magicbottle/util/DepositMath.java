package vontus.magicbottle.util;

import java.util.random.RandomGenerator;

/**
 * The integer math of depositing exp in a bottle, apart from the player and the bottle so it can be tested. The cost is
 * a percentage (0..100) of the points paid; what is left is the gain of the bottle.
 */
public final class DepositMath {
    private DepositMath() {}

    /** What {@link #collect} decided: the exp the bottle gains and the points it takes from the orb. */
    public record Collected(int gain, int taken) {}

    /** The points minus their cost, rounded to the nearest point; it is deterministic, so previews can use it. */
    public static long gainWithoutLimit(int points, int percentage) {
        return points - ((long) points * percentage + 50) / 100;
    }

    /** The exp a deposit of the points would give: what the cost leaves, up to the room of the bottle. */
    public static int depositGain(int points, int percentage, int room) {
        return Math.clamp(gainWithoutLimit(points, percentage), 0, Math.max(0, room));
    }

    /**
     * The fewest points, up to max, whose gain after the cost is at least the given one. The gain grows by 0 or 1 with
     * each point paid, so it's exactly that gain if max reaches it.
     */
    public static int pointsForGain(int gain, int max, int percentage) {
        int low = 0;
        int high = max;
        while (low < high) {
            int mid = low + (high - low) / 2;
            if (gainWithoutLimit(mid, percentage) >= gain) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }

    /**
     * The cost in percent of points, rounded so that its mean is exactly points * percentage / 100: the part that
     * doesn't make a whole point is paid with that probability (like Unbreaking). Integer math, so a cost that is a
     * whole number of points is never random.
     */
    public static int collectCost(int points, int percentage, RandomGenerator random) {
        long scaled = (long) points * percentage;
        int cost = (int) (scaled / 100);
        if (scaled % 100 > 0 && random.nextInt(100) < scaled % 100) {
            cost++;
        }
        return cost;
    }

    /**
     * A bottle with the given room takes an orb of points. It takes all of them (the gain is what the random cost
     * leaves) or, if the gain doesn't fit, the fewest points that fill the room exactly, with the deterministic cost.
     * Nothing is taken if the points or the room are 0 or less, or the cost is 100% (nothing could be stored).
     */
    public static Collected collect(int points, int room, int percentage, RandomGenerator random) {
        if (points <= 0 || room <= 0 || percentage >= 100) {
            return new Collected(0, 0);
        }
        int taken = points;
        int gain = points - collectCost(points, percentage, random);
        if (gain > room) {
            gain = room;
            taken = pointsForGain(room, points, percentage);
        }
        return new Collected(Math.max(gain, 0), taken);
    }
}
