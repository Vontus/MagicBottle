package vontus.magicbottle.lantern;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import vontus.magicbottle.MagicBottle;

/**
 * A Magic Lantern that is placed in the world. A lantern has no tile entity, so everything about it lives here and in
 * the PDC of its chunk (see {@link Lanterns}): its position, the exp it holds (the only place where that exp lives) and
 * the item it was placed as, with 0 exp, so what doesn't change (upgrades, size, name, future keys) is kept as it was.
 */
final class PlacedLantern {
	final World world;
	final int x;
	final int y;
	final int z;
	// The item with 0 exp, as serialized (the stored form, see Lanterns)
	final byte[] item;
	// Only read for what the item says about itself (its capacity), never changed
	final MagicBottle template;
	int exp;
	// Set when it is taken out of the registry, so whoever still holds it (orbs on their way) lets go of it
	boolean removed;
	// Last tick it played its absorb sound, so a farm's worth of orbs isn't a worth of sounds
	long lastSoundTick = -1;

	PlacedLantern(World world, int x, int y, int z, byte[] item, MagicBottle template, int exp) {
		this.world = world;
		this.x = x;
		this.y = y;
		this.z = z;
		this.item = item;
		this.template = template;
		this.exp = exp;
	}

	long blockKey() {
		return BlockPos.pack(x, y, z);
	}

	Block block() {
		return world.getBlockAt(x, y, z);
	}

	Location center() {
		return new Location(world, x + 0.5, y + 0.5, z + 0.5);
	}

	/** What it can still take, 0 if it is full or holds more than its capacity (it keeps that exp). */
	int room() {
		return Math.max(0, template.getCapacity() - exp);
	}

	boolean isFull() {
		return room() == 0;
	}
}
