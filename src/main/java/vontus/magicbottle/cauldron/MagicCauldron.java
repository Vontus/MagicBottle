package vontus.magicbottle.cauldron;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.Cauldron;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.RecipeIngredient;
import vontus.magicbottle.config.RecipesConfig;
import vontus.magicbottle.util.TaskStatus;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class MagicCauldron {
	public static final Material CAULDRON_MATERIAL = Material.CAULDRON;
	private Block block;
	private ArrayList<ItemStack> items;
	private CauldronParticlesTask particlesTask;
	private Player craftingPlayer;

	private static ArrayList<MagicCauldron> cauldronList = new ArrayList<>();

	private MagicCauldron(Block block, Player craftingPlayer) {
		this.block = block;
		this.items = new ArrayList<>();
		this.craftingPlayer = craftingPlayer;
	}

	private MagicCauldron(Block block) {
		this.block = block;
		this.items = new ArrayList<>();
		this.craftingPlayer = null;
	}

	public Player getCraftingPlayer() {
		return craftingPlayer;
	}

	public boolean addItem(ItemStack itemToAdd, Player player) {
		if (!isComplete() && player == craftingPlayer) {
			List<RecipeIngredient> leftIngredients = calculateLeftIngredients();
			for (RecipeIngredient leftIngredient : leftIngredients) {
				if (leftIngredient.getMaterial() == itemToAdd.getType()) {
					int amountToAdd = Math.min(leftIngredient.getAmount(), itemToAdd.getAmount());
					for (ItemStack i : items) {
						if (i.getType() == itemToAdd.getType()) {
							i.setAmount(i.getAmount() + amountToAdd);
							itemToAdd.setAmount(itemToAdd.getAmount() - amountToAdd);
							updateCauldron(player);
							return true;
						}
					}
					ItemStack copyToAdd = itemToAdd.clone();
					itemToAdd.setAmount(itemToAdd.getAmount() - amountToAdd);
					copyToAdd.setAmount(amountToAdd);
					items.add(copyToAdd);
					updateCauldron(player);
					return true;
				}
			}
		}
		return false;
	}

	private void updateCauldron(Player player) {
		if (craftingPlayer == null) {
			craftingPlayer = player;
		}
		updateParticles();
	}

	private List<RecipeIngredient> calculateLeftIngredients() {
		ArrayList<RecipeIngredient> leftIngredients = new ArrayList<>();
		recipeLoop:
		for (RecipeIngredient ingredient : RecipesConfig.getRecipe(RecipesConfig.BOTTLE)) {
			for (ItemStack is : items) {
				if (ingredient.getMaterial() == is.getType()) {
					if (ingredient.getAmount() > is.getAmount()) {
						leftIngredients.add(new RecipeIngredient(ingredient.getMaterial(), ingredient.getAmount() - is.getAmount()));
					}
					continue recipeLoop;
				}
			}
			leftIngredients.add(ingredient);
		}
		return leftIngredients;
	}

//	private boolean canAddItem(ItemStack is) {
//		for (RecipeIngredient ing : calculateLeftIngredients()) {
//			if (ing.getMaterial() == is.getType()) {
//				return true;
//			}
//		}
//		return false;
//	}

	private void updateParticles() {
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
		return calculateLeftIngredients().isEmpty();
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

	public boolean tryCreateBottle(Player player, ItemStack itemStack) {
		if (this.isComplete()) {
			MagicBottle mb = new MagicBottle(itemStack);
			mb.recreate();
			cancelParticles();
			items.clear();
			MagicCauldron.cauldronList.remove(this);
			return true;
		} else {
			return false;
		}
	}

	private static boolean isValidMagicCauldron(Block block) {
		if (block.getType() == CAULDRON_MATERIAL) {
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
