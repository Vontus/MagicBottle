package vontus.magicbottle.cauldron;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Cauldron;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.effects.Effects;
import vontus.magicbottle.util.TaskStatus;

import java.util.ArrayList;
import java.util.Iterator;


public class MagicCauldron {
	private Block block;
	private ArrayList<ItemStack> items;
	private CauldronParticlesTask particlesTask;

	private static ArrayList<MagicCauldron> cauldronList = new ArrayList<>();

	private MagicCauldron() { }

	private MagicCauldron(Block block) {
		this.block = block;
		this.items = new ArrayList<>();
	}

	public boolean addItem(ItemStack is) {
		if (!isComplete()) {
			ItemStack is2 = is.clone();
			is.setAmount(is.getAmount() - 1);
			is2.setAmount(1);
			items.add(is2);
			startParticles();
			if (isComplete()) {
				Effects.newBottle(block.getLocation());
			}
			return true;
		}
		return false;
	}

	private void startParticles() {
		if (particlesTask == null) {
			particlesTask = new CauldronParticlesTask(this);
		}
		if (particlesTask.getStatus() != TaskStatus.RUNNING) {
			particlesTask.runTaskTimer(Plugin.plugin, 1, 1);
		}
	}

	public Location getLocation() {
		return block.getLocation();
	}

	public boolean isComplete() {
		// TODO
		return items.size() >= 4;
	}

	public boolean isEmpty() {
		return items.isEmpty();
	}

	public void prepareRemove() {
		dropItems();
		cancelParticles();
	}

	public void remove() {
		prepareRemove();
		MagicCauldron.cauldronList.remove(this);
	}

	private void cancelParticles() {
		if (particlesTask != null && particlesTask.getStatus() == TaskStatus.RUNNING) {
			particlesTask.cancel();
		}
	}

	private void dropItems() {
		for (ItemStack is : items) {
			block.getWorld().dropItemNaturally(block.getLocation(), is);
		}
		items.clear();
	}

	public static void removeAll() {
		Iterator<MagicCauldron> i = cauldronList.iterator();
		while (i.hasNext()) {
			i.next().prepareRemove();
			i.remove();
		}
	}

	private static boolean isValidMagicCauldron(Block block) {
		if (block.getType() == Material.CAULDRON) {
			Block lower = block.getWorld().getBlockAt(block.getLocation().add(0, -1, 0));
			if (lower.getType() == Material.FIRE || lower.getType() == Material.LAVA) {
				Cauldron c = (Cauldron) block.getState().getData();
				return c.isFull();
			}
		}
		return false;
	}

	public static MagicCauldron getCauldronAt(Location location) {
		for (MagicCauldron cau : cauldronList) {
			if (location.getBlock().equals(cau.block)) {
				return cau;
			}
		}
		MagicCauldron mc = null;
		if (isValidMagicCauldron(location.getBlock())) {
			mc = new MagicCauldron(location.getBlock());
			cauldronList.add(mc);
		}
		return mc;
	}
}
