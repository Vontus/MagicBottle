package vontus.magicbottle;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import vontus.magicbottle.cauldron.CheckItemTask;
import vontus.magicbottle.cauldron.MagicCauldron;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.config.RecipeIngredient;
import vontus.magicbottle.config.RecipesConfig;
import vontus.magicbottle.effects.Effects;
import vontus.magicbottle.util.Exp;

import java.util.HashSet;
import java.util.UUID;

public class Events implements Listener {
	private HashSet<UUID> wait;
	private Plugin plugin;

	public Events(Plugin plugin) {
		this.plugin = plugin;
		this.wait = new HashSet<>();
	}

	@EventHandler
	public void onItemMerge(ItemMergeEvent e) {
		if (CheckItemTask.itemsBeingChecked.contains(e.getEntity())) {
			e.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerSneak(PlayerToggleSneakEvent e) {
		// Easter egg :)
		if (!e.getPlayer().isSneaking()) {
			MagicCauldron mc = MagicCauldron.getCauldronAt(e.getPlayer().getLocation());
			if (mc != null && !mc.isEmpty()) {
				Effects.witchLaugh(e.getPlayer());
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent e) {
		MagicCauldron mc = MagicCauldron.getCauldronAt(e.getBlock().getLocation());
		if (mc != null) {
			mc.remove();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onItemDrop(PlayerDropItemEvent e) {
		for (RecipeIngredient ing : RecipesConfig.getRecipe(RecipesConfig.BOTTLE)) {
			if (ing.getMaterial() == e.getItemDrop().getItemStack().getType()) {
				new CheckItemTask(e.getItemDrop(), e.getPlayer()).runTaskTimer(plugin, 5, 5);
				return;
			}
		}
	}

	@EventHandler
	public void onClickInventory(InventoryClickEvent e) {
		InventoryType invType = e.getView().getType();
		if (invType == InventoryType.ANVIL || invType == InventoryType.BREWING) {
			e.setCancelled(MagicBottle.isMagicBottle(e.getCurrentItem()));
		}
	}

	@EventHandler
	public void onItemUse(PlayerItemDamageEvent e) {
		Player p = e.getPlayer();
		if (Config.repairAutoEnabled && timeOut(p)) {
			ItemStack i = e.getItem();
			if (plugin.autoEnabled.contains(p) && i.getDurability() % 2 != 0) {
				if (i.containsEnchantment(MagicBottle.repairEnchantment)) {
					MagicBottle mb = MagicBottle.getUsableMBInToolsbar(p);
					if (mb != null && !e.isCancelled()) {
						i.setDurability((short) (i.getDurability() + e.getDamage()));
						mb.repair(i);
						e.setCancelled(true);
						p.updateInventory();
					}
				}
			}
		}
	}

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent e) {
		plugin.autoEnabled.remove(e.getPlayer());
	}

	@EventHandler
	public void onPlayerKicked(PlayerKickEvent e) {
		plugin.autoEnabled.remove(e.getPlayer());
	}

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Action act = event.getAction();
		ItemStack item = event.getItem();

		if (MagicBottle.isMagicBottle(item)) {
			MagicBottle mb = new MagicBottle(item);
			if (item.getAmount() == 1 && timeOut(player)) {
				if (act == Action.LEFT_CLICK_AIR || act == Action.LEFT_CLICK_BLOCK) {
					onInteractDeposit(mb, player);
				} else if (act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK) {
					onInteractWithdraw(mb, player);
				}
			}
			event.setCancelled(true);
			player.updateInventory();
		} else if (item != null && item.getType() == MagicBottle.materialEmpty) {
			Block block = event.getClickedBlock();
			if (block != null) {
				MagicCauldron magicCauldron = MagicCauldron.getCauldronAt(block.getLocation());
				if (magicCauldron != null) {
					magicCauldron.tryCreateBottle(player, item);
					event.setCancelled(true);
					player.updateInventory();
				}
			}
		}
	}

	private void onInteractDeposit(MagicBottle bottle, Player p) {
		if (Exp.getPoints(p) > 0) {
			if (p.hasPermission(Config.permDeposit)) {
				int round = p.isSneaking() ? 1 : 0;
				int targetPlayerLevel = Exp.floorLevel(p, round);
				int expToDeposit = Exp.getExpToLevel(p, targetPlayerLevel) * -1;

				bottle.deposit(p, expToDeposit);
			} else
				p.sendMessage(Messages.msgUnauthorizedToDeposit);
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
				p.sendMessage(Messages.msgUnauthorizedToWithdraw);
			}
		}
	}

	private boolean timeOut(Player p) {
		if (!wait.contains(p.getUniqueId())) {
			wait.add(p.getUniqueId());
			new BukkitRunnable() {
				@Override
				public void run() {
					wait.remove(p.getUniqueId());
				}
			}.runTaskLater(this.plugin, 3);
			return true;
		} else {
			return false;
		}
	}

	private boolean chargeNewBottleMoney(Player p) {
		if (Config.costMoneyCraftNewBottle != 0 && !p.hasPermission(Config.permCraftCostExempt)) {
			return plugin.econ.withdrawPlayer(p, Config.costMoneyCraftNewBottle).transactionSuccess();
		} else {
			return true;
		}
	}
}
