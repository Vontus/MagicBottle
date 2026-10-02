package vontus.magicbottle.config;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.Locale;

/**
 * The enchantment an item needs to be repaired: ANY or an enchantment key, e.g. MENDING or minecraft:mending.
 * A wrong value doesn't fail the whole file: it is kept with its {@link #error}, so the config can still load and
 * only repairing is disabled.
 */
public class EnchantParser {
	static final TypeSerializer<EnchantParser> SERIALIZER = new TypeSerializer<>() {
		@Override
		public EnchantParser deserialize(Type type, ConfigurationNode node) {
			return parse(node.getString());
		}

		@Override
		public void serialize(Type type, EnchantParser parser, ConfigurationNode node) {
			node.raw(parser == null ? null : parser.text);
		}
	};

	private static final String ANY_ENCHANTMENT = "ANY";

	private final String text;
	private final Enchantment ench;
	private final boolean anyEnchant;
	// null if the text is valid
	final String error;

	private EnchantParser(String text, Enchantment ench, boolean anyEnchant, String error) {
		this.text = text;
		this.ench = ench;
		this.anyEnchant = anyEnchant;
		this.error = error;
	}

	public static EnchantParser parse(String text) {
		if (ANY_ENCHANTMENT.equals(text)) {
			return new EnchantParser(text, null, true, null);
		}
		NamespacedKey key = text == null ? null : NamespacedKey.fromString(text.toLowerCase(Locale.ROOT));
		Enchantment e = key == null ? null : Registry.ENCHANTMENT.get(key);
		if (e == null) {
			return new EnchantParser(text, null, false, "The enchantment '" + text + "' is not supported");
		}
		return new EnchantParser(text, e, false, null);
	}

	public boolean canRepair(ItemStack item) {
		return anyEnchant || (ench != null && item.containsEnchantment(ench));
	}
}
