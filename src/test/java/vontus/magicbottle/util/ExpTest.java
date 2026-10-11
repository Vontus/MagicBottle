package vontus.magicbottle.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class ExpTest {
    /** A player with only the experience of Bukkit's API: the level and the progress inside it, as Minecraft keeps them. */
    private static final class FakePlayer {
        int level;
        int progressPoints;
        int totalExperience;

        Player proxy() {
            return (Player) Proxy.newProxyInstance(
                    Player.class.getClassLoader(),
                    new Class<?>[] {Player.class},
                    (proxy, method, args) -> handle(method.getName(), args));
        }

        @Nullable
        Object handle(String method, Object[] args) {
            return switch (method) {
                case "getLevel" -> level;
                case "getExp" -> progressPoints / (float) Exp.getExpToLvlUp(level);
                case "setLevel" -> {
                    level = (int) args[0];
                    yield null;
                }
                case "setExp" -> {
                    progressPoints = Math.round((float) args[0] * Exp.getExpToLvlUp(level));
                    yield null;
                }
                case "setTotalExperience" -> {
                    totalExperience = (int) args[0];
                    yield null;
                }
                case "giveExp" -> {
                    progressPoints += (int) args[0];
                    while (progressPoints >= Exp.getExpToLvlUp(level)) {
                        progressPoints -= Exp.getExpToLvlUp(level);
                        level++;
                    }
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method);
            };
        }
    }

    @Test
    void expToLevelUpMatchesVanilla() {
        assertEquals(7, Exp.getExpToLvlUp(0));
        assertEquals(37, Exp.getExpToLvlUp(15));
        assertEquals(42, Exp.getExpToLvlUp(16));
        assertEquals(112, Exp.getExpToLvlUp(30));
        assertEquals(121, Exp.getExpToLvlUp(31));
    }

    @Test
    void totalExpAtLevelMatchesVanilla() {
        assertEquals(0, Exp.getExpAtLevel(0));
        assertEquals(7, Exp.getExpAtLevel(1));
        assertEquals(160, Exp.getExpAtLevel(10));
        assertEquals(315, Exp.getExpAtLevel(15));
        assertEquals(352, Exp.getExpAtLevel(16));
        assertEquals(550, Exp.getExpAtLevel(20));
        assertEquals(1395, Exp.getExpAtLevel(30));
        assertEquals(1507, Exp.getExpAtLevel(31));
        assertEquals(2920, Exp.getExpAtLevel(40));
        assertEquals(5345, Exp.getExpAtLevel(50));
    }

    @Test
    void totalExpIsCappedAtAnInt() {
        assertEquals(Integer.MAX_VALUE, Exp.getExpAtLevel(1_000_000));
    }

    @Test
    void levelFromExpInvertsExpAtLevel() {
        for (int level = 0; level <= 500; level++) {
            assertEquals(level, Exp.getLevelFromExp(Exp.getExpAtLevel(level)), 1e-6, "level " + level);
        }
    }

    @Test
    void levelFromExpIsFractionalInsideALevel() {
        double level = Exp.getLevelFromExp(Exp.getExpAtLevel(20) + Exp.getExpToLvlUp(20) / 2.0);
        assertTrue(level > 20.4 && level < 20.6, "level " + level);
    }

    @Test
    void setPointsThenGetPointsRoundTrips() {
        for (int points : new int[] {0, 1, 6, 7, 8, 315, 352, 1395, 1507, 5345, 123_456}) {
            Player player = new FakePlayer().proxy();
            Exp.setPoints(player, points);
            assertEquals(points, Exp.getPoints(player), "points " + points);
        }
    }

    @Test
    void setPointsLandsOnTheRightLevel() {
        FakePlayer fake = new FakePlayer();
        Exp.setPoints(fake.proxy(), Exp.getExpAtLevel(30) + 5);
        assertEquals(30, fake.level);
        assertEquals(5, fake.progressPoints);
    }

    @Test
    void givePointsAndTakePointsAreInverse() {
        Player player = new FakePlayer().proxy();
        Exp.setPoints(player, 1000);
        Exp.givePoints(player, 777);
        assertEquals(1777, Exp.getPoints(player));
        Exp.takePoints(player, 777);
        assertEquals(1000, Exp.getPoints(player));
    }

    @Test
    void givePointsSaturatesAtAnInt() {
        Player player = new FakePlayer().proxy();
        Exp.setPoints(player, Integer.MAX_VALUE - 5);
        Exp.givePoints(player, 100);
        assertEquals(Integer.MAX_VALUE, Exp.getPoints(player));
    }

    @Test
    void setPointsRejectsNegative() {
        Player player = new FakePlayer().proxy();
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Exp.setPoints(player, -1));
    }
}
