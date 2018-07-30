package vontus.magicbottle.effects;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import vontus.magicbottle.config.Config;

public class SoundEffect {

	public static void fillBottle(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1);
		if (Config.effectParticles) {
			player.spawnParticle(Particle.SPELL_WITCH, player.getLocation(), 50,0.1f, 0.1f, 0.1f);
		}
	}

	public static void pourBottle(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.9f);
		if (Config.effectParticles) {
			Location l = player.getLocation();
			l.setY(l.getY() + 2);
			player.spawnParticle(Particle.ENCHANTMENT_TABLE, l, 50,0.2f, 0.2f, 0.2f);
		}
	}
	
	public static void forbidden(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.2f, 1);
	}
	
	public static void newBottle(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.1f);
	}
}
