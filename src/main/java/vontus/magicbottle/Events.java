package vontus.magicbottle;

import java.util.HashSet;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.CraftingInventory;
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

	@EventHandler
	public void onInteract(PlayerInteractEvent event) {
		Player player = event.getPlayer();
		Action act = event.getAction();
		ItemStack item = event.getItem();

		if (MagicBottle.isMagicBottle(item)) {
			if (item.getAmount() == 1 && !wait.contains(player.getUniqueId())) {
				if (act == Action.LEFT_CLICK_AIR || act == Action.LEFT_CLICK_BLOCK) {
					onInteractFill(event);
				} else if (act == Action.RIGHT_CLICK_AIR || act == Action.RIGHT_CLICK_BLOCK) {
					onInteractPour(event);
				}
				timeOut(player);
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
				ItemStack firstIngredient = getFirstIngredient(event.getInventory());
				if (MagicBottle.isMagicBottle(firstIngredient)) {
					if (result.isEmpty()) {
						onPrepareRecipePour(event);
					} else {
						onPrepareRecipeFill(event);
					}
				} else if (MagicBottle.isWcBottle(firstIngredient)) {
					prepareWcBottleCraft(event);
				} else {
					if (!isEmptyBottleRecipe(event.getInventory())) {
						event.getInventory().setResult(null);
					}
				}
			}
		}
	}

	private void prepareWcBottleCraft(PrepareItemCraftEvent event) {
		ItemStack firstItem = getFirstIngredient(event.getInventory());
		MagicBottle mb = MagicBottle.fromWcBottle(firstItem);
		event.getInventory().setResult(mb.getItem());
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

	private void onInteractFill(PlayerInteractEvent e) {
		Player player = e.getPlayer();
		MagicBottle bottle = new MagicBottle(e.getItem());

		if (Exp.getPoints(player) > 0) {
			if (player.hasPermission(Config.authorizationFill)) {
				int round = player.isSneaking() ? 1 : 0;
				int targetPlayerLevel = Exp.floorLevel(player, round);
				int expToDeposit = Exp.getExpToLevel(player, targetPlayerLevel) * -1;

				bottle.deposit(player, expToDeposit);
			} else
				player.sendMessage(Messages.msgUnauthorizedToDeposit);
		}
	}

	private void onInteractPour(PlayerInteractEvent e) {
		Player player = e.getPlayer();
		MagicBottle bottle = new MagicBottle(e.getItem());
		
		if (bottle.getExp() > 0) {
			if (player.hasPermission(Config.authorizationPour)) {
				int round = player.isSneaking() ? 1 : 0;
				int targetPlayerLevel = Exp.ceilingLevel(player, round);
				int expToWithdraw = Exp.getExpToLevel(player, targetPlayerLevel);

				bottle.withdraw(player, expToWithdraw);
			} else {
				player.sendMessage(Messages.msgUnauthorizedToWithdraw);
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

	private void timeOut(Player p) {
		wait.add(p.getUniqueId());
	    new BukkitRunnable(){
	
	        @Override
			public void run() {
	        	wait.remove(p.getUniqueId());
	        }
	    }.runTaskLater(this.plugin, 2);
	}
	
	private boolean isEmptyBottleRecipe(CraftingInventory inv) {
		for (int i = 0; i < 9; i++) {
			ItemStack item = inv.getMatrix()[i];
			if (item != null && !item.getType().equals(Config.getBottleRecipeIngredient(i + 1))) {
				return false;
			}
		}
		return true;
	}
}
