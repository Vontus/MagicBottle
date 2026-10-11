package vontus.magicbottle.lantern;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import com.destroystokyo.paper.event.server.ServerTickEndEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.listeners.ClickCooldown;

import java.util.List;

/**
 * The Magic Lanterns that are placed: placing and breaking them, keeping them from being destroyed without their exp
 * coming out, taking their exp with a bottle, and the orbs they claim. See {@link Lanterns} for how they are stored and
 * {@link Orbs} for how the orbs get to them.
 *
 * Whatever removes a lantern gives its exp back: breaking it, losing its support and the cases the plugin can't see (the
 * entry is validated when used) drop the item with the exp; explosions, pistons and entities changing the block are
 * cancelled for them instead. Everything here checks the cheap conditions first (the item's material, whether there is any
 * lantern at all) because most of these events are very frequent.
 */
public class LanternListener implements Listener {
	// Ticks between the scans for orbs around the lanterns
	private static final int SCAN_TICKS = 5;

	private final Plugin plugin;
	private final ClickCooldown cooldown;
	private final Lanterns lanterns;
	private final Orbs orbs;

	public LanternListener(Plugin plugin, ClickCooldown cooldown) {
		this.plugin = plugin;
		this.cooldown = cooldown;
		lanterns = new Lanterns(plugin);
		orbs = new Orbs(plugin, lanterns);
	}

	/** Indexes the chunks that were loaded before the plugin was, and fixes the orbs it left behind. Called on enable. */
	public void start() {
		for (World world : plugin.getServer().getWorlds()) {
			for (Chunk chunk : world.getLoadedChunks()) {
				lanterns.load(chunk);
			}
			for (ExperienceOrb orb : world.getEntitiesByClass(ExperienceOrb.class)) {
				orbs.restoreIfLeftBehind(orb);
			}
		}
	}

	/** Lets go of the orbs in flight and writes what changed. Called on disable. */
	public void shutdown() {
		orbs.releaseAll();
		lanterns.flush();
	}

	/** Whether the block is a placed Magic Lantern. */
	public boolean isPlaced(Block block) {
		return lanterns.get(block) != null;
	}

	// ----- Chunks and orbs

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkLoad(ChunkLoadEvent e) {
		lanterns.load(e.getChunk());
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChunkUnload(ChunkUnloadEvent e) {
		lanterns.unload(e.getChunk());
	}

	// The orbs an unloading chunk takes with it are saved as they are: they must not keep the flags we gave them
	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntitiesUnload(EntitiesUnloadEvent e) {
		if (orbs.isActive()) {
			for (Entity entity : e.getEntities()) {
				if (entity instanceof ExperienceOrb orb) {
					orbs.releaseIfClaimed(orb);
				}
			}
		}
	}

	// ...and if one was saved that way anyway (a crash), it is fixed when it loads
	@EventHandler(priority = EventPriority.MONITOR)
	public void onEntitiesLoad(EntitiesLoadEvent e) {
		for (Entity entity : e.getEntities()) {
			if (entity instanceof ExperienceOrb orb) {
				orbs.restoreIfLeftBehind(orb);
			}
		}
	}

	// The one repeating job: after the entities of each tick, move the orbs on their way (their path is ours, a velocity
	// alone doesn't get them through blocks), and every few ticks look for new ones. Does nothing without lanterns.
	@EventHandler
	public void onTickEnd(ServerTickEndEvent e) {
		if (lanterns.isEmpty() && !orbs.isActive()) {
			return;
		}
		orbs.move();
		if (e.getTickNumber() % SCAN_TICKS == 0) {
			orbs.scan();
			lanterns.flush();
		}
		for (PlacedLantern lantern : lanterns.all()) {
			LanternEffects.ambient(lantern);
		}
	}

	// An orb choosing a player: a lantern with room in range takes it first, and a claimed orb never follows anybody
	@EventHandler(priority = EventPriority.HIGH)
	public void onOrbTarget(EntityTargetLivingEntityEvent e) {
		if (e.getEntity() instanceof ExperienceOrb orb && e.getTarget() instanceof Player && claimOnTheSpot(orb, e.isCancelled())) {
			e.setCancelled(true);
		}
	}

	// A player about to pick up an orb: same thing, so a player next to where the orbs appear doesn't get them between scans
	@EventHandler(priority = EventPriority.HIGH)
	public void onOrbPickup(PlayerPickupExperienceEvent e) {
		if (claimOnTheSpot(e.getExperienceOrb(), e.isCancelled())) {
			e.setCancelled(true);
		}
	}

	// Whether the orb is claimed now, by a lantern before or on the spot (never if the event was already cancelled)
	private boolean claimOnTheSpot(ExperienceOrb orb, boolean cancelled) {
		if (orbs.isClaimed(orb)) {
			return true;
		}
		if (cancelled || lanterns.isEmpty() || !Config.isUpgradeEnabled(Upgrade.LANTERN)) {
			return false;
		}
		PlacedLantern lantern = lanterns.findFor(orb.getLocation());
		return lantern != null && orbs.claim(orb, lantern);
	}

	// ----- Placing

	// A Magic Lantern is only placed if the upgrade is enabled and the player may: otherwise the block would go down as a
	// plain lantern and its exp would be lost, so the placement is cancelled and the item stays in their hand
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlaceCheck(BlockPlaceEvent e) {
		MagicBottle lantern = asLantern(e.getItemInHand());
		if (lantern != null && (!Config.isUpgradeEnabled(Upgrade.LANTERN) || !e.getPlayer().hasPermission(Upgrade.LANTERN.permission()))) {
			e.setCancelled(true);
			e.getPlayer().sendMessage(Messages.render(Messages.texts.messages.lantern.placeDenied));
		}
	}

	// Last, so it only registers what was really placed (other plugins may have denied it)
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onPlace(BlockPlaceEvent e) {
		MagicBottle lantern = asLantern(e.getItemInHand());
		if (lantern != null && e.canBuild() && e.getBlockPlaced().getType() == Material.LANTERN) {
			lanterns.place(e.getBlockPlaced(), lantern);
		}
	}

	// The bottle of the item if it is a Magic Lantern, null if not. Checks the material first: it is the cheapest.
	private static MagicBottle asLantern(ItemStack item) {
		if (item.getType() != MagicBottle.lanternMaterial || !MagicBottle.isMagicBottle(item)) {
			return null;
		}
		MagicBottle bottle = new MagicBottle(item.clone());
		return bottle.isLantern() ? bottle : null;
	}

	// A dispenser could put the block down without any event we listen to, so no Magic Lantern item comes out of one
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onDispense(BlockDispenseEvent e) {
		if (asLantern(e.getItem()) != null) {
			e.setCancelled(true);
		}
	}

	// ----- Breaking: the lantern drops with its exp

	// Last, and only if it went through: the lantern is gone from the registry and drops as the item, the loot table's plain
	// lantern is turned off. Creative players get the drop too, so no exp is ever lost.
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onBreak(BlockBreakEvent e) {
		PlacedLantern lantern = lanterns.get(e.getBlock());
		if (lantern != null) {
			e.setDropItems(false);
			lanterns.breakAndDrop(lantern);
		}
	}

	// A lantern that loses its support (or is destroyed by physics) pops off
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onDestroy(BlockDestroyEvent e) {
		PlacedLantern lantern = lanterns.get(e.getBlock());
		if (lantern != null) {
			e.setWillDrop(false);
			lanterns.breakAndDrop(lantern);
		}
	}

	// ----- Protection: what would remove the block without a drop is cancelled for the lanterns

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent e) {
		protect(e.blockList());
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onBlockExplode(BlockExplodeEvent e) {
		protect(e.blockList());
	}

	private void protect(List<Block> blocks) {
		if (!lanterns.isEmpty()) {
			blocks.removeIf(block -> lanterns.get(block) != null);
		}
	}

	// A lantern that is pushed breaks (it is destroyed by pistons), so a piston that would push one doesn't move
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPistonExtend(BlockPistonExtendEvent e) {
		if (hasLantern(e.getBlocks())) {
			e.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onPistonRetract(BlockPistonRetractEvent e) {
		if (hasLantern(e.getBlocks())) {
			e.setCancelled(true);
		}
	}

	private boolean hasLantern(List<Block> blocks) {
		return !lanterns.isEmpty() && blocks.stream().anyMatch(block -> lanterns.get(block) != null);
	}

	// Endermen, withers and the like changing the block: it stays
	@EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
	public void onEntityChangeBlock(EntityChangeBlockEvent e) {
		if (e.getTo() != Material.LANTERN && lanterns.get(e.getBlock()) != null) {
			e.setCancelled(true);
		}
	}

	// ----- Taking the exp

	// Right click with a MagicBottle: the exp of the lantern goes into the bottle as a deposit (the capacity of the bottle
	// and the deposit cost apply), and what doesn't fit stays. A Magic Lantern in hand places another one instead.
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInteract(PlayerInteractEvent e) {
		if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() == null || e.useInteractedBlock() == Event.Result.DENY) {
			return;
		}
		ItemStack item = e.getItem();
		Block block = e.getClickedBlock();
		if (item == null || block == null || !MagicBottle.isMagicBottle(item)) {
			return;
		}
		PlacedLantern lantern = lanterns.get(block);
		if (lantern == null || new MagicBottle(item.clone()).isLantern()) {
			return;
		}
		e.setCancelled(true);
		Player player = e.getPlayer();
		player.updateInventory();
		if (item.getAmount() != 1 || !cooldown.throttle(player)) {
			return;
		}
		takeExp(lantern, new MagicBottle(item), player);
	}

	private void takeExp(PlacedLantern lantern, MagicBottle bottle, Player player) {
		if (lantern.exp <= 0) {
			player.sendMessage(Messages.render(Messages.texts.messages.lantern.empty));
			SoundEffect.forbidden(player);
			return;
		}
		if (!player.hasPermission(Config.permDeposit)) {
			player.sendMessage(Messages.render(Messages.texts.messages.unauthorized.deposit));
			return;
		}
		int before = lantern.exp;
		// Like the orbs a player picks up with the collect upgrade: only what the bottle takes pays the cost, and it fills exactly
		int left = bottle.collect(player, before, gained -> { });
		if (left == before) {
			if (bottle.hasRoom()) {
				player.sendMessage(Messages.render(Messages.texts.messages.nothingToStore));
			} else {
				player.sendMessage(Messages.render(Messages.texts.messages.maxLevelReached,
						Placeholder.unparsed("level", Integer.toString(bottle.getCapacityLevel()))));
			}
			SoundEffect.forbidden(player);
			return;
		}
		lantern.exp = left;
		lanterns.markDirty(lantern);
		lanterns.flush();
		SoundEffect.fillBottle(player);
	}
}
