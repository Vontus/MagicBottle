package vontus.magicbottle.lantern;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import vontus.magicbottle.config.Config;

import java.util.concurrent.ThreadLocalRandom;

/**
 * What a placed Magic Lantern shows. The block never changes, so the particles say how full it is (all of them go to the
 * players inside its radius, the same ones to everybody):
 * trails in the colors of XP orbs flowing into it while it has room, about 4 per second when empty down to 1.5 just before
 * it is full, none once it is full; and witch sparkles from the top, none when empty up to about 7 per second when full.
 */
final class LanternEffects {
	private static final double TRAILS_EMPTY_PER_SECOND = 4;
	private static final double TRAILS_ALMOST_FULL_PER_SECOND = 1.5;
	private static final double SPARKLES_FULL_PER_SECOND = 7;
	private static final int TRAIL_TICKS = 20;
	// The greens and yellows of XP orbs
	private static final int COLOR_FROM = 0x6EB83E;
	private static final int COLOR_TO = 0xC0C442;

	private LanternEffects() {
	}

	/** Called every tick for each lantern: each kind of particle is sent with the probability that gives its rate. */
	static void ambient(PlacedLantern lantern) {
		if (!Config.settings.effect.particles || lantern.world.getPlayerCount() == 0) {
			return;
		}
		ThreadLocalRandom random = ThreadLocalRandom.current();
		int capacity = lantern.template.getCapacity();
		double fill = capacity <= 0 ? 1 : Math.clamp((double) lantern.exp / capacity, 0, 1);
		int radius = Config.lanternRadius();

		if (!lantern.isFull() && random.nextDouble() < perTick(TRAILS_EMPTY_PER_SECOND
				+ (TRAILS_ALMOST_FULL_PER_SECOND - TRAILS_EMPTY_PER_SECOND) * fill)) {
			Location center = lantern.center();
			// From a random point about a block around, to the lantern
			Location from = center.clone().add(random.nextDouble(-1, 1), random.nextDouble(-1, 1), random.nextDouble(-1, 1));
			Color color = Color.fromRGB(lerp(COLOR_FROM, COLOR_TO, random.nextDouble()));
			Particle.TRAIL.builder().location(from).data(new Particle.Trail(center, color, TRAIL_TICKS))
					.receivers(radius, true).spawn();
		}
		if (fill > 0 && random.nextDouble() < perTick(SPARKLES_FULL_PER_SECOND * fill)) {
			Location top = lantern.center().add(random.nextDouble(-0.15, 0.15), 0.5, random.nextDouble(-0.15, 0.15));
			Particle.WITCH.builder().location(top).receivers(radius, true).spawn();
		}
	}

	/** An orb was absorbed: a sound (at most one per tick) and a small burst. */
	static void absorbed(PlacedLantern lantern) {
		long tick = lantern.world.getGameTime();
		if (Config.settings.effect.sound && lantern.lastSoundTick != tick) {
			lantern.lastSoundTick = tick;
			lantern.world.playSound(lantern.center(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.BLOCKS, 0.3f, 1.2f);
		}
		if (Config.settings.effect.particles) {
			Particle.WITCH.builder().location(lantern.center()).count(4).offset(0.2, 0.2, 0.2)
					.receivers(Config.lanternRadius(), true).spawn();
		}
	}

	private static double perTick(double perSecond) {
		return perSecond / 20;
	}

	private static int lerp(int from, int to, double t) {
		int red = (int) (((from >> 16) & 0xFF) + ((((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t));
		int green = (int) (((from >> 8) & 0xFF) + ((((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t));
		int blue = (int) ((from & 0xFF) + (((to & 0xFF) - (from & 0xFF)) * t));
		return (red << 16) | (green << 8) | blue;
	}
}
