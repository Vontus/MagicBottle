package vontus.magicbottle.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.inventory.RecipeChoice;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.TypeSerializer;

import java.lang.reflect.Type;
import java.util.Locale;

/**
 * An ingredient of the new bottle recipe: an item ID (blaze_powder, BLAZE_POWDER or minecraft:blaze_powder) or an
 * item tag (#minecraft:planks). A wrong value doesn't fail the whole file: it is kept with its {@link #error}, so
 * the config can still load and only the recipe is disabled.
 */
final class Ingredient {
	static final TypeSerializer<Ingredient> SERIALIZER = new TypeSerializer<>() {
		@Override
		public Ingredient deserialize(Type type, ConfigurationNode node) {
			return parse(node.getString());
		}

		@Override
		public void serialize(Type type, Ingredient ingredient, ConfigurationNode node) {
			node.raw(ingredient == null ? null : ingredient.text);
		}
	};

	final String text;
	// null if the text is invalid
	final RecipeChoice choice;
	// null if the text is valid
	final String error;

	private Ingredient(String text, RecipeChoice choice, String error) {
		this.text = text;
		this.choice = choice;
		this.error = error;
	}

	static Ingredient parse(String text) {
		if (text == null || text.isBlank()) {
			return invalid(text, "it has no item");
		}
		text = text.trim();
		if (text.startsWith("#")) {
			NamespacedKey key = NamespacedKey.fromString(text.substring(1).toLowerCase(Locale.ROOT));
			Tag<Material> tag = key == null ? null : Bukkit.getTag(Tag.REGISTRY_ITEMS, key, Material.class);
			if (tag == null || tag.getValues().isEmpty()) {
				return invalid(text, "unknown item tag '" + text + "'");
			}
			return new Ingredient(text, new RecipeChoice.MaterialChoice(tag), null);
		}
		Material m = Material.matchMaterial(text);
		if (m != null && m.isAir()) {
			return invalid(text, "it can't be AIR, use a space in the shape for an empty slot");
		}
		if (m == null || !m.isItem()) {
			return invalid(text, "unknown item '" + text + "'");
		}
		return new Ingredient(text, new RecipeChoice.MaterialChoice(m), null);
	}

	private static Ingredient invalid(String text, String error) {
		return new Ingredient(text, null, error);
	}
}
