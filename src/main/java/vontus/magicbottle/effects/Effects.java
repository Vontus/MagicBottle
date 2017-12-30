package vontus.magicbottle.effects;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import vontus.magicbottle.config.Config;

public class Effects {

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

	public static void newBottle(Location location) {
		if (Config.effectSound)
			location.getWorld().playSound(location, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 0.1f);
	}

	public static void addIngredientToCauldron(Location location) {
		if (Config.effectSound)
			location.getWorld().playSound(location, Sound.ENTITY_WITCH_DRINK, 10, 0.5f);
	}

	public static void activeCauldron(Location location) {
		if (Config.effectParticles) {
			Location loc = location.add(0.5, 0.5, 0.5);
			ParticleEffect.SPELL_MOB.display(0, 0, 0, 1, 1, loc, 50);
		}
	}

	public static void cauldronComplete(Location location) {
		if (Config.effectParticles) {
			Location loc = location.add(0.5, 0.5, 0.5);
			ParticleEffect.SPELL_WITCH.display(0.2f, 0.1f, 0.2f, 0, 1, loc, 50);
		}
	}

	public static void witchLaugh(Player p) {
		if (Config.effectSound) {
			p.playSound(p.getLocation(), Sound.ENTITY_WITCH_AMBIENT, 0.5f, 1);
		}
	}
}
