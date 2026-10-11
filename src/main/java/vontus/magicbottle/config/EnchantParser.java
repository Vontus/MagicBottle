package vontus.magicbottle.config;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.lang.reflect.Type;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.TypeSerializer;

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

    private final @Nullable String text;
    private final @Nullable Enchantment ench;
    private final boolean anyEnchant;
    // null if the text is valid
    final @Nullable String error;

    private EnchantParser(
            @Nullable String text, @Nullable Enchantment ench, boolean anyEnchant, @Nullable String error) {
        this.text = text;
        this.ench = ench;
        this.anyEnchant = anyEnchant;
        this.error = error;
    }

    public static EnchantParser parse(@Nullable String text) {
        if (ANY_ENCHANTMENT.equals(text)) {
            return new EnchantParser(text, null, true, null);
        }
        NamespacedKey key = text == null ? null : NamespacedKey.fromString(text.toLowerCase(Locale.ROOT));
        Enchantment e = key == null
                ? null
                : RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.ENCHANTMENT)
                        .get(key);
        if (e == null) {
            return new EnchantParser(text, null, false, "The enchantment '" + text + "' is not supported");
        }
        return new EnchantParser(text, e, false, null);
    }

    public boolean canRepair(ItemStack item) {
        return anyEnchant || (ench != null && item.containsEnchantment(ench));
    }
}
