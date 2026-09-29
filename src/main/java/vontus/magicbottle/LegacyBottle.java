package vontus.magicbottle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Bottles made by 1.5.x were marked with a hidden Efficiency enchantment and kept their exp in lore line 1.
 * Everything about that format lives here: legacy bottles are rewritten in the current format as soon as the
 * plugin gets hold of them, so the rest of the code only deals with the current one.
 */
class LegacyBottle {
	private static final Enchantment MARKER = Enchantment.EFFICIENCY;
	private static final int XP_LINE = 1;

	static boolean isLegacyBottle(ItemStack item) {
		return !MagicBottle.hasBottleMarker(item) &&
				item.containsEnchantment(MARKER) &&
				parseExp(item) != null;
	}

	static void migrateIfLegacy(ItemStack item) {
		if (isLegacyBottle(item)) {
			int exp = parseExp(item);
			ItemMeta meta = item.getItemMeta();
			meta.removeEnchant(MARKER);
			meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
			item.setItemMeta(meta);
			MagicBottle.rewrite(item, exp);
		}
	}

	static void migrateInventory(Inventory inv) {
		for (ItemStack item : inv) {
			if (MagicBottle.isMagicBottle(item)) {
				migrateIfLegacy(item);
			}
		}
	}

	private static Integer parseExp(ItemStack item) {
		try {
			// The lore was written with legacy color codes; only its text matters
			Component line = item.lore().get(XP_LINE);
			return Integer.parseInt(PlainTextComponentSerializer.plainText().serialize(line).trim().replace(",", ""));
		} catch (Exception e) {
			return null;
		}
	}
}
