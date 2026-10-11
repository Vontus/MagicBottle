package vontus.magicbottle.listeners;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.util.Exp;

import java.util.Set;

// Depositing and withdrawing exp by clicking with the bottle in hand
public class BottleInteractListener implements Listener {
	// Blocks that open a menu or toggle when right-clicked with anything in hand. It is our own list (and not
	// Material#isInteractable, which also counts blocks that only react to certain items, like cauldrons or
	// composters) so a click on a block that would do nothing with a bottle still withdraws. Blocks that take
	// any item (decorated pot, jukebox, chiseled bookshelf...) are left out on purpose: they keep cancelling
	private static final Set<Material> INTERACTABLE = Set.of(
			Material.CRAFTING_TABLE, Material.CRAFTER, Material.CHEST, Material.TRAPPED_CHEST, Material.ENDER_CHEST,
			Material.BARREL, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.HOPPER,
			Material.DROPPER, Material.DISPENSER, Material.BREWING_STAND, Material.ENCHANTING_TABLE,
			Material.GRINDSTONE, Material.LOOM, Material.CARTOGRAPHY_TABLE, Material.STONECUTTER,
			Material.SMITHING_TABLE, Material.BEACON, Material.LEVER, Material.BELL, Material.NOTE_BLOCK,
			Material.REPEATER, Material.COMPARATOR, Material.DAYLIGHT_DETECTOR, Material.LECTERN);

	private final ClickCooldown cooldown;

	public BottleInteractListener(ClickCooldown cooldown) {
		this.cooldown = cooldown;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Action act = event.getAction();
		ItemStack item = event.getItem();

		if (MagicBottle.isMagicBottle(item)) {
			// A sneaking player never interacts with the block in vanilla, so only a standing one is passed on.
			// The block acts (useInteractedBlock stays as it is) and only the bottle is denied; the click
			// neither withdraws nor starts the cooldown
			if (act == Action.RIGHT_CLICK_BLOCK && !player.isSneaking() && isInteractable(event.getClickedBlock())) {
				event.setUseItemInHand(Event.Result.DENY);
				return;
			}

			MagicBottle mb = new MagicBottle(item);
			if (item.getAmount() == 1 && cooldown.throttle(player)) {
				if (act == Action.LEFT_CLICK_AIR || act == Action.LEFT_CLICK_BLOCK) {
					onInteractDeposit(mb, player);
				} else if (act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK) {
					onInteractWithdraw(mb, player);
				}
			}

			event.setCancelled(true);
			player.updateInventory();
		}
	}

	private static boolean isInteractable(Block block) {
		if (block == null) {
			return false;
		}
		Material type = block.getType();
		return INTERACTABLE.contains(type)
				|| Tag.ANVIL.isTagged(type)
				|| Tag.SHULKER_BOXES.isTagged(type)
				|| Tag.BEDS.isTagged(type)
				|| Tag.BUTTONS.isTagged(type)
				|| Tag.FENCE_GATES.isTagged(type)
				// Iron doors and trapdoors don't open by hand
				|| (Tag.DOORS.isTagged(type) && type != Material.IRON_DOOR)
				|| (Tag.TRAPDOORS.isTagged(type) && type != Material.IRON_TRAPDOOR);
	}

	private void onInteractDeposit(MagicBottle bottle, Player p) {
		if (Exp.getPoints(p) > 0) {
			if (p.hasPermission(Config.permDeposit)) {
				int round = p.isSneaking() ? 1 : 0;
				int targetPlayerLevel = Exp.floorLevel(p, round);
				int expToDeposit = Exp.getExpToLevel(p, targetPlayerLevel) * -1;

				bottle.deposit(p, expToDeposit);
			} else
				p.sendMessage(Messages.render(Messages.texts.messages.unauthorized.deposit));
		}
	}

	private void onInteractWithdraw(MagicBottle bottle, Player p) {
		if (bottle.getExp() > 0) {
			if (p.hasPermission(Config.permWithdraw)) {
				int round = p.isSneaking() ? 1 : 0;
				int targetPlayerLevel = Exp.ceilingLevel(p, round);
				int expToWithdraw = Exp.getExpToLevel(p, targetPlayerLevel);

				bottle.withdraw(p, expToWithdraw);
			} else {
				p.sendMessage(Messages.render(Messages.texts.messages.unauthorized.withdraw));
			}
		}
	}
}
