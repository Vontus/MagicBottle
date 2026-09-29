package vontus.magicbottle.util;

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
}
