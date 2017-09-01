package vontus.magicbottle;

import java.util.HashSet;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

public class Events implements Listener {
	private HashSet<UUID> wait;
	private Plugin plugin;

	public Events(Plugin plugin) {
		this.plugin = plugin;
		this.wait = new HashSet<>();
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onClickInventory(InventoryClickEvent e) {
		InventoryType invType = e.getView().getType();
		if (invType == InventoryType.ANVIL || invType == InventoryType.BREWING) {
			e.setCancelled(MagicBottle.isMagicBottle(e.getCurrentItem()));
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
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
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPrepareCraft(PrepareItemCraftEvent event) {
		if (event.getRecipe() != null) {
			ItemStack r = event.getRecipe().getResult();
			if (MagicBottle.isMagicBottle(r)) {
				MagicBottle result = new MagicBottle(r);
				if (MagicBottle.isMagicBottle(getFirstIngredient(event.getInventory()))) {
					if (result.isEmpty()) {
						onPrepareRecipeWithdraw(event);
					} else {
						onPrepareRecipeDeposit(event);
					}
				} else {
					if (!isEmptyBottleRecipe(event.getInventory())) {
						event.getInventory().setResult(null);
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onCraft(CraftItemEvent e) {
		if (MagicBottle.isMagicBottle(e.getRecipe().getResult())) {
			MagicBottle result = new MagicBottle(e.getRecipe().getResult());
			if (MagicBottle.isMagicBottle(getFirstIngredient(e.getInventory()))) {
				if (result.isEmpty()) {
					onRecipeWithdraw(e);
				} else {
					onRecipeDeposit(e);
				}
			} else {
				if (MagicBottle.isMagicBottle(e.getRecipe().getResult())) {
					if (isEmptyBottleRecipe(e.getInventory())) {
						Player player = (Player) e.getView().getPlayer();
						if (player.hasPermission(Config.permCraft)) {
							if (chargeNewBottleMoney(player)) {
								e.getInventory().setResult(new MagicBottle(0).getItem());
								SoundEffect.newBottle(player);
							} else {
								SoundEffect.forbidden(player);
								player.sendMessage(Messages.msgNotEnoughMoney.replace(Messages.moneyReplacer,
										Double.toString(Config.costMoneyCraftNewBottle)));
								e.setCancelled(true);
							}
						} else {
							player.sendMessage(Messages.msgUnauthorizedToCraft);
							e.setCancelled(true);
						}
					}
				}
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onItemUse(PlayerItemDamageEvent e) {
		Player p = e.getPlayer();
		if (Config.repairAutoEnabled && timeOut(p)) {
			ItemStack i = e.getItem();
			if (plugin.autoEnabled.contains(p) && i.containsEnchantment(Enchantment.MENDING)
					&& i.getDurability() % 2 != 0) {
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

	@EventHandler
	public void onPlayerLeave(PlayerQuitEvent e) {
		plugin.autoEnabled.remove(e.getPlayer());
	}

	@EventHandler
	public void onPlayerKicked(PlayerKickEvent e) {
		plugin.autoEnabled.remove(e.getPlayer());
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

	private void onPrepareRecipeWithdraw(PrepareItemCraftEvent e) {
		Player player = (Player) e.getView().getPlayer();
		if (!Config.recipePour || !player.hasPermission(Config.permWithdraw)) {
			e.getInventory().setResult(null);
		}
	}

	private void onPrepareRecipeDeposit(PrepareItemCraftEvent e) {
		Player player = (Player) e.getView().getPlayer();
		if (Config.recipeFill && player.hasPermission(Config.permDeposit) && Exp.getPoints(player) > 0) {
			MagicBottle bottle = new MagicBottle(0);
			int playerPoints = Exp.getPoints(player);
			int expCost = (int) Math.round(playerPoints * Config.costPercentageDeposit);
			int maxPoints = bottle.getMaxFillablePoints(player, playerPoints - expCost);
			bottle.setExp(maxPoints);
			e.getInventory().setResult(bottle.getItem());
		} else {
			e.getInventory().setResult(null);
		}
	}

	private void onRecipeWithdraw(CraftItemEvent e) {
		Player player = (Player) e.getView().getPlayer();
		ItemStack i = getFirstIngredient(e.getInventory());
		if (i.getAmount() == 1 && Config.recipePour && player.hasPermission(Config.permWithdraw)) {
			MagicBottle bottle = new MagicBottle(i);
			bottle.withdraw(player, bottle.getExp());
			e.getInventory().setResult(new MagicBottle(0).getItem());
		} else {
			e.setCancelled(true);
		}
	}

	private void onRecipeDeposit(CraftItemEvent e) {
		Player player = (Player) e.getView().getPlayer();
		ItemStack ingr = getFirstIngredient(e.getInventory());
		if (ingr.getAmount() == 1 && Exp.getPoints(player) > 0 && player.hasPermission(Config.permDeposit)
				&& Config.recipeFill) {
			MagicBottle bottle = new MagicBottle(0);
			bottle.deposit(player, Exp.getPoints(player));
			e.getInventory().setResult(bottle.getItem());
		} else {
			e.setCancelled(true);
		}
	}

	private ItemStack getFirstIngredient(CraftingInventory inv) {
		for (ItemStack i : inv.getMatrix()) {
			if (Utils.getMaterial(i) != Material.AIR) {
				return i;
			}
		}
		return null;
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

	private boolean isEmptyBottleRecipe(CraftingInventory inv) {
		for (int i = 0; i < 9; i++) {
			ItemStack item = inv.getMatrix()[i];
			Material m = Config.getBottleRecipeIngredient(i + 1);
			if (!Utils.getMaterial(item).equals(m) || MagicBottle.isMagicBottle(item)) {
				return false;
			}
		}
		return true;
	}

	private boolean chargeNewBottleMoney(Player p) {
		if (Config.costMoneyCraftNewBottle != 0 && !p.hasPermission(Config.permCraftCostExempt)) {
			return plugin.econ.withdrawPlayer(p, Config.costMoneyCraftNewBottle).transactionSuccess();
		} else {
			return true;
		}
	}
}
