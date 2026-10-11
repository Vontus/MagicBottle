package vontus.magicbottle.lantern;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.config.Config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Magic Lanterns that are placed in the loaded chunks, and their persistence.
 *
 * Storage: the PDC of the chunk, key {@code magicbottle:lanterns}, a list of containers with {@code pos} (the packed block
 * position, {@link BlockPos#pack}), {@code exp} (the exp it holds) and {@code item} (the item with 0 exp, as
 * {@link ItemStack#serializeAsBytes()}, so the server's DataFixer keeps it up to date across versions). These keys, like the
 * ones of the bottle, are never renamed. An entry this version can't read is kept as it is when the chunk is written.
 *
 * The index in memory is filled when a chunk loads and dropped when it unloads. A chunk is written when a lantern in it
 * changes (right away when it is placed, broken or emptied, within a few ticks when it absorbs orbs, see {@link #flush})
 * and when it unloads, so the exp is never only in memory for long.
 *
 * The block is validated whenever the lantern is used: if it is not a lantern anymore (a command, WorldEdit, another
 * plugin) the entry is dropped and its exp comes out as the item (see {@link #dropStale}), never lost.
 */
final class Lanterns {
	private final Plugin plugin;
	private final NamespacedKey key;
	private final NamespacedKey keyPos;
	private final NamespacedKey keyExp;
	private final NamespacedKey keyItem;
	private final Map<UUID, Map<Long, ChunkLanterns>> chunks = new HashMap<>();
	// The lanterns of all the loaded chunks, to iterate them
	private final Set<PlacedLantern> all = new LinkedHashSet<>();
	private final Set<ChunkLanterns> dirty = new LinkedHashSet<>();

	Lanterns(Plugin plugin) {
		this.plugin = plugin;
		key = new NamespacedKey(plugin, "lanterns");
		keyPos = new NamespacedKey(plugin, "pos");
		keyExp = new NamespacedKey(plugin, "exp");
		keyItem = new NamespacedKey(plugin, "item");
	}

	private static final class ChunkLanterns {
		final World world;
		final int x;
		final int z;
		final Map<Long, PlacedLantern> byPosition = new HashMap<>();
		// The entries that couldn't be read, written back untouched
		final List<PersistentDataContainer> unreadable = new ArrayList<>();

		ChunkLanterns(World world, int x, int z) {
			this.world = world;
			this.x = x;
			this.z = z;
		}
	}

	boolean isEmpty() {
		return all.isEmpty();
	}

	/** A copy, so lanterns can be removed while going through it. */
	List<PlacedLantern> all() {
		return new ArrayList<>(all);
	}

	/** The lantern at the block, or null if it isn't one. */
	PlacedLantern get(Block block) {
		if (all.isEmpty() || block.getType() != Material.LANTERN) {
			return null;
		}
		return getEntry(block);
	}

	// Like get, without needing the block to still be a lantern
	private PlacedLantern getEntry(Block block) {
		Map<Long, ChunkLanterns> inWorld = chunks.get(block.getWorld().getUID());
		ChunkLanterns chunk = inWorld == null ? null : inWorld.get(Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4));
		return chunk == null ? null : chunk.byPosition.get(BlockPos.pack(block));
	}

	/**
	 * The first lantern with room that has the location in range, or null. Only looks at the chunks around the location,
	 * where the lanterns are indexed.
	 */
	PlacedLantern findFor(Location at) {
		if (all.isEmpty()) {
			return null;
		}
		Map<Long, ChunkLanterns> inWorld = chunks.get(at.getWorld().getUID());
		if (inWorld == null) {
			return null;
		}
		double radius = Config.lanternRadius();
		int range = (int) Math.ceil(radius / 16);
		int cx = at.getBlockX() >> 4;
		int cz = at.getBlockZ() >> 4;
		for (int dx = -range; dx <= range; dx++) {
			for (int dz = -range; dz <= range; dz++) {
				ChunkLanterns chunk = inWorld.get(Chunk.getChunkKey(cx + dx, cz + dz));
				if (chunk == null) {
					continue;
				}
				for (PlacedLantern lantern : chunk.byPosition.values()) {
					if (lantern.center().distanceSquared(at) <= radius * radius && !lantern.isFull()) {
						return lantern;
					}
				}
			}
		}
		return null;
	}

	/** Indexes the lanterns stored in the chunk; called when it loads. */
	void load(Chunk chunk) {
		unload(chunk, true);
		PersistentDataContainer pdc = chunk.getPersistentDataContainer();
		List<PersistentDataContainer> entries = pdc.get(key, PersistentDataType.LIST.dataContainers());
		if (entries == null) {
			return;
		}
		ChunkLanterns loaded = new ChunkLanterns(chunk.getWorld(), chunk.getX(), chunk.getZ());
		for (PersistentDataContainer entry : entries) {
			PlacedLantern lantern = read(chunk.getWorld(), entry);
			if (lantern == null) {
				loaded.unreadable.add(entry);
				plugin.getLogger().warning("A Magic Lantern in chunk " + chunk.getX() + ", " + chunk.getZ() + " of "
						+ chunk.getWorld().getName() + " couldn't be read; it is kept as it is.");
			} else {
				loaded.byPosition.put(lantern.blockKey(), lantern);
				all.add(lantern);
			}
		}
		chunks.computeIfAbsent(chunk.getWorld().getUID(), id -> new HashMap<>())
				.put(Chunk.getChunkKey(chunk.getX(), chunk.getZ()), loaded);
	}

	private PlacedLantern read(World world, PersistentDataContainer entry) {
		Long pos = entry.get(keyPos, PersistentDataType.LONG);
		Integer exp = entry.get(keyExp, PersistentDataType.INTEGER);
		byte[] bytes = entry.get(keyItem, PersistentDataType.BYTE_ARRAY);
		if (pos == null || exp == null || bytes == null) {
			return null;
		}
		MagicBottle template = templateOf(bytes);
		if (template == null) {
			return null;
		}
		return new PlacedLantern(world, BlockPos.x(pos), BlockPos.y(pos), BlockPos.z(pos), bytes,
				template, exp);
	}

	private static MagicBottle templateOf(byte[] bytes) {
		try {
			ItemStack item = ItemStack.deserializeBytes(bytes);
			return MagicBottle.isMagicBottle(item) ? new MagicBottle(item) : null;
		} catch (RuntimeException e) {
			return null;
		}
	}

	/** Writes the chunk if it has changes, and drops its lanterns from the index; called when it unloads. */
	void unload(Chunk chunk) {
		unload(chunk, true);
	}

	private void unload(Chunk chunk, boolean write) {
		Map<Long, ChunkLanterns> inWorld = chunks.get(chunk.getWorld().getUID());
		ChunkLanterns old = inWorld == null ? null : inWorld.remove(Chunk.getChunkKey(chunk.getX(), chunk.getZ()));
		if (old == null) {
			return;
		}
		if (dirty.remove(old) && write) {
			write(chunk, old);
		}
		for (PlacedLantern lantern : old.byPosition.values()) {
			lantern.removed = true;
			all.remove(lantern);
		}
	}

	/** Registers a lantern just placed, with the exp of the item. */
	void place(Block block, MagicBottle item) {
		// Nothing should be here: an entry that is would be one whose block went away unnoticed, so its exp comes out
		PlacedLantern stale = getEntry(block);
		if (stale != null) {
			dropStale(stale);
		}
		int exp = item.getExp();
		MagicBottle template = new MagicBottle(item.getItem().clone());
		template.setExp(0);
		template.getItem().setAmount(1);
		byte[] bytes = template.getItem().serializeAsBytes();
		PlacedLantern lantern = new PlacedLantern(block.getWorld(), block.getX(), block.getY(), block.getZ(), bytes,
				template, exp);
		ChunkLanterns chunk = chunks.computeIfAbsent(block.getWorld().getUID(), id -> new HashMap<>())
				.computeIfAbsent(Chunk.getChunkKey(block.getX() >> 4, block.getZ() >> 4),
						id -> new ChunkLanterns(block.getWorld(), block.getX() >> 4, block.getZ() >> 4));
		chunk.byPosition.put(lantern.blockKey(), lantern);
		all.add(lantern);
		markDirty(lantern);
		flush();
	}

	/** Removes the lantern from the registry and drops it where it was, as the item with the exp it holds. */
	void breakAndDrop(PlacedLantern lantern) {
		ItemStack item = toItem(lantern);
		lantern.removed = true;
		all.remove(lantern);
		ChunkLanterns chunk = chunkOf(lantern);
		if (chunk != null) {
			chunk.byPosition.remove(lantern.blockKey());
			dirty.add(chunk);
		}
		flush();
		lantern.world.dropItemNaturally(lantern.center(), item);
	}

	/** The block isn't a lantern anymore: the exp of the entry comes out as the item instead of being lost. */
	void dropStale(PlacedLantern lantern) {
		plugin.getLogger().warning("The Magic Lantern at " + lantern.world.getName() + " " + lantern.x + " " + lantern.y + " "
				+ lantern.z + " is gone; dropping it with its " + lantern.exp + " exp.");
		breakAndDrop(lantern);
	}

	/**
	 * The item of the lantern with the exp it holds now. If the stored item can't be read (it should always be), the
	 * exp is not lost: a plain Magic Lantern holds it.
	 */
	private static ItemStack toItem(PlacedLantern lantern) {
		MagicBottle bottle = templateOf(lantern.item);
		if (bottle == null) {
			bottle = new MagicBottle(0);
			bottle.addUpgrade(Upgrade.LANTERN);
		}
		bottle.setExp(lantern.exp);
		return bottle.getItem();
	}

	void markDirty(PlacedLantern lantern) {
		ChunkLanterns chunk = chunkOf(lantern);
		if (chunk != null) {
			dirty.add(chunk);
		}
	}

	private ChunkLanterns chunkOf(PlacedLantern lantern) {
		Map<Long, ChunkLanterns> inWorld = chunks.get(lantern.world.getUID());
		return inWorld == null ? null : inWorld.get(Chunk.getChunkKey(lantern.x >> 4, lantern.z >> 4));
	}

	/** Writes the chunks that changed. Chunks that unloaded were written when they did. */
	void flush() {
		if (dirty.isEmpty()) {
			return;
		}
		for (ChunkLanterns chunk : new ArrayList<>(dirty)) {
			Chunk loaded = chunk.world.isChunkLoaded(chunk.x, chunk.z) ? chunk.world.getChunkAt(chunk.x, chunk.z, false) : null;
			if (loaded != null) {
				write(loaded, chunk);
			}
		}
		dirty.clear();
	}

	private void write(Chunk loaded, ChunkLanterns chunk) {
		PersistentDataContainer pdc = loaded.getPersistentDataContainer();
		if (chunk.byPosition.isEmpty() && chunk.unreadable.isEmpty()) {
			pdc.remove(key);
			return;
		}
		List<PersistentDataContainer> entries = new ArrayList<>(chunk.unreadable);
		for (PlacedLantern lantern : chunk.byPosition.values()) {
			PersistentDataContainer entry = pdc.getAdapterContext().newPersistentDataContainer();
			entry.set(keyPos, PersistentDataType.LONG, lantern.blockKey());
			entry.set(keyExp, PersistentDataType.INTEGER, lantern.exp);
			entry.set(keyItem, PersistentDataType.BYTE_ARRAY, lantern.item);
			entries.add(entry);
		}
		pdc.set(key, PersistentDataType.LIST.dataContainers(), entries);
	}
}
