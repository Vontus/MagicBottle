package vontus.magicbottle.effects;

import java.util.Objects;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import vontus.magicbottle.config.Config;

public class SoundEffect {

    // Player inherits getLocation from OfflinePlayer, where it is nullable, but a player that is online always has one
    private static Location at(Player player) {
        return Objects.requireNonNull(player.getLocation());
    }

    // Played at the player with an explicit source, so players can lower it with the slider of that category instead of
    // only with the master volume
    private static void play(Player player, org.bukkit.Sound sound, float volume, float pitch) {
        player.playSound(Sound.sound(sound, Sound.Source.PLAYER, volume, pitch), Sound.Emitter.self());
    }

    public static void fillBottle(Player player) {
        if (Config.settings.effect.sound) play(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1);
        if (Config.settings.effect.particles) {
            player.spawnParticle(Particle.WITCH, at(player), 50, 0.1f, 0.1f, 0.1f);
        }
    }

    public static void pourBottle(Player player) {
        if (Config.settings.effect.sound) play(player, org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.9f);
        if (Config.settings.effect.particles) {
            Location l = at(player);
            l.setY(l.getY() + 2);
            player.spawnParticle(Particle.ENCHANT, l, 50, 0.2f, 0.2f, 0.2f);
        }
    }

    public static void forbidden(Player player) {
        if (Config.settings.effect.sound) play(player, org.bukkit.Sound.BLOCK_ANVIL_PLACE, 0.2f, 1);
    }

    public static void merge(Player player) {
        if (Config.settings.effect.sound) play(player, org.bukkit.Sound.BLOCK_ANVIL_USE, 0.5f, 1);
    }

    public static void newBottle(Player player) {
        if (Config.settings.effect.sound) play(player, org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.1f);
    }
}
