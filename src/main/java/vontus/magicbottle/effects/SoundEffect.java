package vontus.magicbottle.effects;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import vontus.magicbottle.config.Config;

public class SoundEffect {

    // Player inherits getLocation from OfflinePlayer, where it is nullable, but a player that is online always has one
    private static Location at(Player player) {
        return Objects.requireNonNull(player.getLocation());
    }

    public static void fillBottle(Player player) {
        if (Config.settings.effect.sound) player.playSound(at(player), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1);
        if (Config.settings.effect.particles) {
            player.spawnParticle(Particle.WITCH, at(player), 50, 0.1f, 0.1f, 0.1f);
        }
    }

    public static void pourBottle(Player player) {
        if (Config.settings.effect.sound) player.playSound(at(player), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.9f);
        if (Config.settings.effect.particles) {
            Location l = at(player);
            l.setY(l.getY() + 2);
            player.spawnParticle(Particle.ENCHANT, l, 50, 0.2f, 0.2f, 0.2f);
        }
    }

    public static void forbidden(Player player) {
        if (Config.settings.effect.sound) player.playSound(at(player), Sound.BLOCK_ANVIL_PLACE, 0.2f, 1);
    }

    public static void merge(Player player) {
        if (Config.settings.effect.sound) player.playSound(at(player), Sound.BLOCK_ANVIL_USE, 0.5f, 1);
    }

    public static void newBottle(Player player) {
        if (Config.settings.effect.sound) player.playSound(at(player), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.1f);
    }
}
