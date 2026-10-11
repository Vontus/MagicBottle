package vontus.magicbottle.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import vontus.magicbottle.util.DepositMath.Collected;

class DepositMathTest {
    private static final int[] PERCENTAGES = {0, 1, 7, 10, 33, 50, 75, 99};

    /** Always draws the same number, so a test decides the roll. */
    private record Fixed(int value) implements RandomGenerator {
        @Override
        public long nextLong() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }

    /** Fails if it is used: a cost that is a whole number of points must not roll. */
    private static final class NoRandom implements RandomGenerator {
        @Override
        public long nextLong() {
            throw new AssertionError("the cost is exact, it must not be random");
        }

        @Override
        public int nextInt(int bound) {
            throw new AssertionError("the cost is exact, it must not be random");
        }
    }

    @Test
    void gainWithoutLimitRoundsTheCostToTheNearestPoint() {
        assertEquals(100, DepositMath.gainWithoutLimit(100, 0));
        assertEquals(0, DepositMath.gainWithoutLimit(100, 100));
        assertEquals(50, DepositMath.gainWithoutLimit(100, 50));
        assertEquals(1, DepositMath.gainWithoutLimit(3, 50));
        assertEquals(Integer.MAX_VALUE, DepositMath.gainWithoutLimit(Integer.MAX_VALUE, 0));
    }

    @Test
    void depositGainIsLimitedByTheRoom() {
        assertEquals(90, DepositMath.depositGain(100, 10, 1000));
        assertEquals(40, DepositMath.depositGain(100, 10, 40));
        assertEquals(0, DepositMath.depositGain(100, 10, 0));
        assertEquals(0, DepositMath.depositGain(100, 10, -25));
        assertEquals(0, DepositMath.depositGain(100, 100, 1000));
    }

    @Test
    void pointsForGainIsTheFewestThatGiveIt() {
        for (int percentage : PERCENTAGES) {
            for (int points = 0; points <= 300; points++) {
                int gain = (int) DepositMath.gainWithoutLimit(points, percentage);
                int paid = DepositMath.pointsForGain(gain, points, percentage);
                String what = percentage + "% of " + points;
                assertTrue(paid <= points, what);
                assertEquals(gain, DepositMath.gainWithoutLimit(paid, percentage), what);
                if (paid > 0) {
                    assertTrue(DepositMath.gainWithoutLimit(paid - 1, percentage) < gain, what);
                }
            }
        }
    }

    @Test
    void collectCostHasTheExactMean() {
        // Rolling every possible number once, the total cost is exactly points * percentage / 100 per roll
        for (int percentage : PERCENTAGES) {
            for (int points : new int[] {1, 2, 3, 9, 10, 37, 100, 1234}) {
                long total = 0;
                for (int roll = 0; roll < 100; roll++) {
                    total += DepositMath.collectCost(points, percentage, new Fixed(roll));
                }
                assertEquals((long) points * percentage, total, percentage + "% of " + points);
            }
        }
    }

    @Test
    void collectCostIsNeverRandomWhenExact() {
        RandomGenerator noRandom = new NoRandom();
        assertEquals(0, DepositMath.collectCost(37, 0, noRandom));
        assertEquals(37, DepositMath.collectCost(37, 100, noRandom));
        assertEquals(5, DepositMath.collectCost(10, 50, noRandom));
        assertEquals(20, DepositMath.collectCost(200, 10, noRandom));
    }

    @Test
    void collectCostRoundsDownOrUp() {
        // 1 point at 30% costs 0.3 points: 1 point with 30% probability (rolls 0..29)
        assertEquals(1, DepositMath.collectCost(1, 30, new Fixed(29)));
        assertEquals(0, DepositMath.collectCost(1, 30, new Fixed(30)));
    }

    @Test
    void collectTakesTheWholeOrbWhenItFits() {
        assertEquals(new Collected(8, 10), DepositMath.collect(10, 1000, 20, new Fixed(0)));
    }

    @Test
    void collectWithoutCostStoresEverything() {
        assertEquals(new Collected(10, 10), DepositMath.collect(10, 1000, 0, new NoRandom()));
    }

    @Test
    void collectFillsTheBottleExactly() {
        for (int percentage : PERCENTAGES) {
            for (int room = 1; room <= 40; room++) {
                Collected collected = DepositMath.collect(60, room, percentage, new Fixed(0));
                String what = percentage + "% room " + room;
                assertTrue(collected.gain() <= room, what);
                if (collected.gain() == room && collected.taken() < 60) {
                    // It took the fewest points that give the room, with the deterministic cost
                    assertEquals(room, DepositMath.gainWithoutLimit(collected.taken(), percentage), what);
                    assertTrue(DepositMath.gainWithoutLimit(collected.taken() - 1, percentage) < room, what);
                }
            }
        }
    }

    @Test
    void collectTakesOnlyWhatFitsFromABigOrb() {
        // 100 points at 0% into a room of 30: takes 30 and leaves the other 70 to the player
        assertEquals(new Collected(30, 30), DepositMath.collect(100, 30, 0, new NoRandom()));
        // At 50%: 60 points pay 30 exp
        assertEquals(new Collected(30, 60), DepositMath.collect(100, 30, 50, new Fixed(0)));
    }

    @Test
    void collectRefusesWhatCannotBeStored() {
        Collected nothing = new Collected(0, 0);
        assertEquals(nothing, DepositMath.collect(10, 100, 100, new NoRandom()));
        assertEquals(nothing, DepositMath.collect(10, 0, 10, new NoRandom()));
        assertEquals(nothing, DepositMath.collect(10, -5, 10, new NoRandom()));
        assertEquals(nothing, DepositMath.collect(0, 100, 10, new NoRandom()));
        assertEquals(nothing, DepositMath.collect(-3, 100, 10, new NoRandom()));
    }
}
