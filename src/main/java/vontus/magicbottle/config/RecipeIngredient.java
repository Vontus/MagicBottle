package vontus.magicbottle.config;

import org.bukkit.Material;

public class RecipeIngredient implements Cloneable {
	private Material material;
	private int amount;

	public RecipeIngredient(Material material, int amount) {
		this.material = material;
		this.amount = amount;
	}

	public RecipeIngredient() {
		this.material = null;
		this.amount = 0;
	}

	public void setMaterial(Material material) {
		this.material = material;
	}

	public void setAmount(int amount) {
		this.amount = amount;
	}

	public Material getMaterial() {
		return material;
	}

	public int getAmount() {
		return amount;
	}
}
