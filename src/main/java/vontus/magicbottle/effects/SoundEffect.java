package vontus.magicbottle.effects;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import vontus.magicbottle.config.Config;

public class SoundEffect {

	public static void fillBottle(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.ORB_PICKUP, 0.5f, 1);
		if (Config.effectParticles) {
			ParticleEffect.SPELL_WITCH.display(0.1f, 0.1f, 0.1f, 0.1f, 50, player.getLocation(), 50);
		}
	}

	public static void pourBottle(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.ORB_PICKUP, 0.5f, 0.9f);
		if (Config.effectParticles) {
			Location l = player.getLocation();
			l.setY(l.getY() + 2);
			ParticleEffect.ENCHANTMENT_TABLE.display(0.2f, 0.2f, 0.2f, 1, 50, l, 50);
		}
	}
	
	public static void forbidden(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.ANVIL_LAND, 0.2f, 1);
	}
	
	public static void newBottle(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.LEVEL_UP, 0.5f, 0.1f);
	}
}
