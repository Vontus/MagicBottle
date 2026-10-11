package vontus.magicbottle.lantern;

import org.bukkit.block.Block;

/** A block position packed in a long like vanilla's BlockPos does: 26 bits for x and z, 12 for y. */
final class BlockPos {
	private BlockPos() {
	}

	static long pack(int x, int y, int z) {
		return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
	}

	static long pack(Block block) {
		return pack(block.getX(), block.getY(), block.getZ());
	}

	static int x(long packed) {
		return (int) (packed >> 38);
	}

	static int y(long packed) {
		return (int) (packed << 52 >> 52);
	}

	static int z(long packed) {
		return (int) (packed << 26 >> 38);
	}
}
