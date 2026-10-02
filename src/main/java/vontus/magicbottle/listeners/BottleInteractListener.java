package vontus.magicbottle.listeners;

import org.bukkit.entity.Player;
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

// Depositing and withdrawing exp by clicking with the bottle in hand
public class BottleInteractListener implements Listener {
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
}
