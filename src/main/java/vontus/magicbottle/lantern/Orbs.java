package vontus.magicbottle.lantern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.config.Config;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The orbs the lanterns have claimed, and how they get to them. An orb is claimed by the first lantern with room that finds
 * it (see {@link #scan}, and the events that claim it on the spot, see LanternListener). From then on it is the lantern's:
 * players can't attract or pick it up, it has no gravity and no physics, and it flies to the lantern along a path of our
 * own, through blocks (setting its velocity isn't enough: when an orb overlaps a block its own tick pushes it out, so it
 * would never go through a wall). Its exp is added to the lantern when it arrives, up to the room it has then; what doesn't
 * fit goes on as an orb. An orb that is let go (the lantern is full, gone, or it takes too long) gets its gravity back.
 *
 * The cost depends on the number of lanterns (a lantern with no orbs around costs one entity query every few ticks) and on
 * the orbs in flight, and it is nothing at all when there are no lanterns and no orbs in flight.
 */
final class Orbs {
	// Blocks per tick the orbs fly at, and how much of the way to that velocity they make up each tick
	private static final double SPEED = 0.4;
	private static final double BLEND = 0.25;
	// An orb this close to the center of the lantern is absorbed (squared, in blocks)
	private static final double ABSORB_DISTANCE_SQUARED = 0.7 * 0.7;
	// Ticks after which an orb that hasn't arrived is let go (the normal trip takes less than 30)
	private static final int MAX_TICKS = 100;

	private final Lanterns lanterns;
	// Marks the orbs we have changed, so the ones left behind (a crash, an unload) are fixed when they load again
	private final NamespacedKey keyClaimed;
	private final Map<UUID, Claim> claims = new HashMap<>();

	private static final class Claim {
		final ExperienceOrb orb;
		final PlacedLantern lantern;
		final Vector velocity;
		int age;

		Claim(ExperienceOrb orb, PlacedLantern lantern) {
			this.orb = orb;
			this.lantern = lantern;
			this.velocity = new Vector();
		}
	}

	Orbs(Plugin plugin, Lanterns lanterns) {
		this.lanterns = lanterns;
		keyClaimed = new NamespacedKey(plugin, "claimed");
	}

	boolean isActive() {
		return !claims.isEmpty();
	}

	boolean isClaimed(ExperienceOrb orb) {
		return !claims.isEmpty() && claims.containsKey(orb.getUniqueId());
	}

	/**
	 * Claims the orb for the lantern. Returns whether the orb is claimed (by any lantern) after this: false if the lantern
	 * can't take it.
	 */
	boolean claim(ExperienceOrb orb, PlacedLantern lantern) {
		if (claims.containsKey(orb.getUniqueId())) {
			return true;
		}
		if (!orb.isValid() || orb.getExperience() <= 0 || lantern.removed || lantern.isFull()) {
			return false;
		}
		orb.setGravity(false);
		orb.setNoPhysics(true);
		orb.getPersistentDataContainer().set(keyClaimed, PersistentDataType.BYTE, (byte) 1);
		claims.put(orb.getUniqueId(), new Claim(orb, lantern));
		return true;
	}

	/** Gives the orb back its gravity if it was one of ours that was left behind (it loaded with the mark and no claim). */
	void restoreIfLeftBehind(ExperienceOrb orb) {
		if (!claims.containsKey(orb.getUniqueId()) && orb.getPersistentDataContainer().has(keyClaimed, PersistentDataType.BYTE)) {
			release(orb);
		}
	}

	/** Lets the orb go, if it is claimed, e.g. because it is about to unload. */
	void releaseIfClaimed(ExperienceOrb orb) {
		Claim claim = claims.remove(orb.getUniqueId());
		if (claim != null) {
			release(orb);
		}
	}

	void releaseAll() {
		for (Claim claim : claims.values()) {
			release(claim.orb);
		}
		claims.clear();
	}

	private void release(ExperienceOrb orb) {
		orb.setGravity(true);
		orb.setNoPhysics(false);
		orb.getPersistentDataContainer().remove(keyClaimed);
	}

	/**
	 * Every few ticks: checks the block of each lantern and claims the orbs around the ones that have room. This is what
	 * finds orbs, because spawn events miss the ones that vanilla merges into an existing orb without any event.
	 */
	void scan() {
		boolean enabled = Config.isUpgradeEnabled(Upgrade.LANTERN);
		double radius = Config.lanternRadius();
		for (PlacedLantern lantern : lanterns.all()) {
			// Validate the block whenever the lantern is used: it may have been removed without an event we know of
			if (lantern.block().getType() != Material.LANTERN) {
				lanterns.dropStale(lantern);
				continue;
			}
			if (!enabled || lantern.isFull()) {
				continue;
			}
			Collection<ExperienceOrb> orbs = lantern.world.getNearbyEntitiesByType(ExperienceOrb.class, lantern.center(), radius,
					orb -> !claims.containsKey(orb.getUniqueId()));
			for (ExperienceOrb orb : orbs) {
				claim(orb, lantern);
			}
		}
	}

	/** Every tick, after the entities have ticked: moves the claimed orbs along their path and absorbs the ones that arrive. */
	void move() {
		if (claims.isEmpty()) {
			return;
		}
		boolean enabled = Config.isUpgradeEnabled(Upgrade.LANTERN);
		Location position = new Location(null, 0, 0, 0);
		Iterator<Claim> it = claims.values().iterator();
		while (it.hasNext()) {
			Claim claim = it.next();
			ExperienceOrb orb = claim.orb;
			PlacedLantern lantern = claim.lantern;
			if (!orb.isValid()) {
				// It is gone: absorbed into another orb (which keeps its exp), or its chunk unloaded
				it.remove();
				continue;
			}
			if (!enabled || lantern.removed || lantern.isFull() || ++claim.age > MAX_TICKS || orb.getWorld() != lantern.world) {
				release(orb);
				it.remove();
				continue;
			}
			orb.getLocation(position);
			double dx = lantern.x + 0.5 - position.getX();
			double dy = lantern.y + 0.5 - position.getY();
			double dz = lantern.z + 0.5 - position.getZ();
			double distanceSquared = dx * dx + dy * dy + dz * dz;
			if (distanceSquared < ABSORB_DISTANCE_SQUARED) {
				it.remove();
				absorb(orb, lantern);
				continue;
			}
			// Blend the velocity towards the lantern, without going past it
			double distance = Math.sqrt(distanceSquared);
			Vector velocity = claim.velocity;
			velocity.setX(velocity.getX() + (dx / distance * SPEED - velocity.getX()) * BLEND);
			velocity.setY(velocity.getY() + (dy / distance * SPEED - velocity.getY()) * BLEND);
			velocity.setZ(velocity.getZ() + (dz / distance * SPEED - velocity.getZ()) * BLEND);
			double step = Math.min(velocity.length(), distance);
			double scale = velocity.lengthSquared() == 0 ? 0 : step / velocity.length();
			position.add(velocity.getX() * scale, velocity.getY() * scale, velocity.getZ() * scale);
			position.setWorld(lantern.world);
			orb.teleport(position);
			orb.setVelocity(velocity);
		}
	}

	/**
	 * Adds the exp of the orb to the lantern, up to its room. What doesn't fit stays in the world: a single orb keeps what is
	 * left (its value is lowered), a merged one gives up as many units as fit, and the unit that is cut in two goes on as a
	 * new orb.
	 */
	private void absorb(ExperienceOrb orb, PlacedLantern lantern) {
		int unit = orb.getExperience();
		int count = Math.max(1, orb.getCount());
		long total = (long) unit * count;
		int absorbed = (int) Math.min(total, lantern.room());
		if (absorbed <= 0) {
			release(orb);
			return;
		}
		lantern.exp += absorbed;
		lanterns.markDirty(lantern);
		if (absorbed == total) {
			orb.remove();
		} else {
			int whole = absorbed / unit;
			int cut = absorbed % unit;
			int left = count - whole;
			if (cut == 0) {
				orb.setCount(left);
			} else {
				int rest = unit - cut;
				left--;
				if (left == 0) {
					orb.setExperience(rest);
					orb.setCount(1);
				} else {
					orb.setCount(left);
					orb.getWorld().spawn(orb.getLocation(), ExperienceOrb.class, other -> other.setExperience(rest));
				}
			}
			release(orb);
		}
		LanternEffects.absorbed(lantern);
	}
}
