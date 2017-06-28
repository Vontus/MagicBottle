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
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

public class Events implements Listener {
    private HashSet<UUID> wait;
    private Plugin plugin;

    public Events(Plugin plugin) {
        this.plugin = plugin;
        this.wait = new HashSet<>();
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
		Player player = event.getPlayer();
		EquipmentSlot slot = event.getHand();
		ItemStack item = null;
		
		if (slot == EquipmentSlot.HAND) {
			item = player.getInventory().getItemInMainHand();
		} else if (slot == EquipmentSlot.OFF_HAND) {
			item = player.getInventory().getItemInOffHand();
		}

		if (MagicBottle.isMagicBottle(item)) {
			MagicBottle mb = new MagicBottle(item);
			if (item.getAmount() == 1 && timeOut(player)) {
				onInteractPour(mb, player);
			}

			event.setCancelled(true);
			player.updateInventory();
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
					onInteractFill(mb, player);
				} else if (act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK) {
					onInteractPour(mb, player);
				}
			}

			event.setCancelled(true);
			player.updateInventory();
		}
	}

	@EventHandler
	public void onPrepareCraft(PrepareItemCraftEvent event) {
		if (event.getRecipe() != null) {
			ItemStack r = event.getRecipe().getResult();
			if (MagicBottle.isMagicBottle(r)) {
				MagicBottle result = new MagicBottle(r);
				if (MagicBottle.isMagicBottle(getFirstIngredient(event.getInventory()))) {
					if (result.isEmpty()) {
						onPrepareRecipePour(event);
					} else {
						onPrepareRecipeFill(event);
					}
				} else {
					if (!isEmptyBottleRecipe(event.getInventory())) {
						event.getInventory().setResult(null);
					}
				}
			}
		}
	}

	@EventHandler
	public void onCraft(CraftItemEvent e) {
		if (MagicBottle.isMagicBottle(e.getRecipe().getResult())) {
			MagicBottle result = new MagicBottle(e.getRecipe().getResult());
			if (MagicBottle.isMagicBottle(getFirstIngredient(e.getInventory()))) {
				if (result.isEmpty()) {
					onRecipePour(e);
				} else {
					onRecipeFill(e);
				}
			} else {
				if (MagicBottle.isMagicBottle(e.getRecipe().getResult())) {
					Player player = (Player) e.getView().getPlayer();
					if (!player.hasPermission(Config.authorizationCraft)) {
						e.setCancelled(true);
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

    private void onInteractFill(MagicBottle bottle, Player p) {
		if (Exp.getPoints(p) > 0) {
			if (p.hasPermission(Config.authorizationFill)) {
				int round = p.isSneaking() ? 1 : 0;
				int targetPlayerLevel = Exp.floorLevel(p, round);
				int expToDeposit = Exp.getExpToLevel(p, targetPlayerLevel) * -1;

				bottle.deposit(p, expToDeposit);
			} else
				p.sendMessage(Messages.msgUnauthorizedToDeposit);
		}
	}

	private void onInteractPour(MagicBottle bottle, Player p) {
		if (bottle.getExp() > 0) {
			if (p.hasPermission(Config.authorizationPour)) {
				int round = p.isSneaking() ? 1 : 0;
				int targetPlayerLevel = Exp.ceilingLevel(p, round);
				int expToWithdraw = Exp.getExpToLevel(p, targetPlayerLevel);

				bottle.withdraw(p, expToWithdraw);
			} else {
				p.sendMessage(Messages.msgUnauthorizedToWithdraw);
			}
		}
	}

	private void onPrepareRecipePour(PrepareItemCraftEvent e) {
		Player player = (Player) e.getView().getPlayer();
		if (Config.recipePour && player.hasPermission(Config.authorizationPour)) {
			// Leave it like that
		} else {
			e.getInventory().setResult(null);
		}
	}

	private void onPrepareRecipeFill(PrepareItemCraftEvent e) {
		Player player = (Player) e.getView().getPlayer();
		if (Config.recipeFill && player.hasPermission(Config.authorizationFill) && Exp.getPoints(player) > 0) {
			MagicBottle bottle = new MagicBottle(0);
			bottle.setExp(bottle.getMaxFillablePoints(player, Exp.getPoints(player)));
			e.getInventory().setResult(bottle.getItem());
		} else {
			e.getInventory().setResult(null);
		}
	}

	private void onRecipePour(CraftItemEvent e) {
		Player player = (Player) e.getView().getPlayer();
		ItemStack i = getFirstIngredient(e.getInventory());
		if (i.getAmount() == 1 && Config.recipePour && player.hasPermission(Config.authorizationPour)) {
			MagicBottle bottle = new MagicBottle(i);
			bottle.withdraw(player, bottle.getExp());
			e.getInventory().setResult(new MagicBottle(0).getItem());
		} else {
			e.setCancelled(true);
		}
	}

	private void onRecipeFill(CraftItemEvent e) {
		Player player = (Player) e.getView().getPlayer();
		ItemStack ingredient = getFirstIngredient(e.getInventory());
		if (ingredient.getAmount() == 1 && Exp.getPoints(player) > 0 && player.hasPermission(Config.authorizationFill) && Config.recipeFill) {
			MagicBottle bottle = new MagicBottle(0);
			bottle.deposit(player, player.getTotalExperience());
			e.getInventory().setResult(bottle.getItem());
		} else {
			e.setCancelled(true);
		}
	}

	private ItemStack getFirstIngredient(CraftingInventory inv) {
		for(ItemStack i : inv.getMatrix()) {
			if (i != null && i.getType() != Material.AIR)
				return i;
		}
		return null;
	}

	private boolean timeOut(Player p) {
		if (!wait.contains(p.getUniqueId())) {
			wait.add(p.getUniqueId());
		    new BukkitRunnable(){
		
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
}
