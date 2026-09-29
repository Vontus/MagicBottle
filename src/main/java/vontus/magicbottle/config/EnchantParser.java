package vontus.magicbottle.config;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

import java.text.ParseException;
import java.util.Locale;

public class EnchantParser {
	private static final String ANY_ENCHANTMENT = "ANY";

	private final Enchantment ench;
	private final boolean anyEnchant;

	private EnchantParser(Enchantment e) {
		ench = e;
		anyEnchant = false;
	}

	private EnchantParser(boolean anyEnchant) {
		ench = null;
		this.anyEnchant = anyEnchant;
	}

	public boolean canRepair(ItemStack item) {
		return anyEnchant || (ench != null && item.containsEnchantment(ench));
	}

	// Accepts ANY or an enchantment key, e.g. MENDING or minecraft:mending
	public static EnchantParser parseForBukkit(String enchantString) throws ParseException {
		if (ANY_ENCHANTMENT.equals(enchantString)) {
			return new EnchantParser(true);
		}
		NamespacedKey key = enchantString == null ? null : NamespacedKey.fromString(enchantString.toLowerCase(Locale.ROOT));
		Enchantment e = key == null ? null : Registry.ENCHANTMENT.get(key);
		if (e == null) {
			throw new ParseException("The enchantment '" + enchantString + "' is not supported", 0);
		}
		return new EnchantParser(e);
	}
}
