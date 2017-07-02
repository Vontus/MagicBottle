package vontus.magicbottle;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import vontus.magicbottle.config.Config;

public class PlayEffect {

	public static void fillBottle(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1);
		if (Config.effectParticles) {
			ParticleEffect.SPELL_WITCH.display(0.1f, 0.1f, 0.1f, 0.1f, 50, player.getLocation(), 50);
		}
	}

	public static void pourBottle(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 0.9f);
		if (Config.effectParticles) {
			Location l = player.getLocation();
			l.setY(l.getY() + 2);
			ParticleEffect.ENCHANTMENT_TABLE.display(0.2f, 0.2f, 0.2f, 1, 50, l, 50);
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
