package vontus.magicbottle.util;

import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.text.NumberFormat;
import java.util.Locale;

public class Utils {

	public static String roundDouble(double number) {
		Double round = Math.round(number * 10) / 10d;
		return NumberFormat.getNumberInstance(Locale.US).format(round);
	}

	public static String roundInt(int number) {
		Double round = Math.round(number * 10) / 10d;
		return NumberFormat.getNumberInstance(Locale.US).format(round);
	}
	
	public static Material getMaterial(ItemStack is) {
		if (is == null)
			return Material.AIR;
		else
			return is.getType();
	}

	// Damage points of a tool or armor piece (0 if undamaged). Read from the data component, which doesn't clone the
	// item meta: this is called from hot events.
	public static int getDamage(ItemStack is) {
		return is.getDataOrDefault(DataComponentTypes.DAMAGE, 0);
	}

	public static void setDamage(ItemStack is, int damage) {
		is.setData(DataComponentTypes.DAMAGE, damage);
	}
}
