package vontus.magicbottle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.util.Exp;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A whole bottle as one command word, the counterpart of vanilla's {@code item[components]}: the level, optionally
 * followed by the upgrades in brackets separated by commas ({@code 30}, {@code 30[collect,autorepair]}) and, after a
 * space, the optional count.
 */
public class BottleArgument implements CustomArgumentType<BottleArgument.Spec, String> {
	// As vanilla /give: 100 stacks of 64
	private static final int MAX_COUNT = 6400;

	/** What was written: the level and the upgrades, which are a set. */
	public record Spec(int level, Set<Upgrade> upgrades, int count) {
		/** Builds the bottle through {@link MagicBottle}, ignoring the upgrade permissions and settings (admin). */
		public ItemStack createItem() {
			MagicBottle bottle = new MagicBottle(Exp.getExpAtLevel(level));
			upgrades.forEach(bottle::addUpgrade);
			return bottle.getItem();
		}
	}

	@Override
	public Spec parse(StringReader reader) throws CommandSyntaxException {
		int start = reader.getCursor();
		while (reader.canRead() && reader.peek() != ' ') {
			reader.skip();
		}
		String word = reader.getString().substring(start, reader.getCursor());

		// The count follows after a space; it is part of this argument because the client only accepts the brackets
		// and commas of the word in a greedy string, which must be the last argument
		int count = 1;
		if (reader.canRead()) {
			reader.skip();
			String countText = reader.getRemaining();
			try {
				count = Integer.parseInt(countText);
			} catch (NumberFormatException e) {
				throw error("Expected a count, found '" + countText + "'");
			}
			if (count < 1 || count > MAX_COUNT) {
				throw error("The count must be between 1 and " + MAX_COUNT);
			}
			reader.setCursor(reader.getTotalLength());
		}

		int bracket = word.indexOf('[');
		String levelText = bracket < 0 ? word : word.substring(0, bracket);
		int level;
		try {
			level = Integer.parseInt(levelText);
		} catch (NumberFormatException e) {
			throw error("Expected a level, found '" + levelText + "'");
		}
		if (level < 0 || level > Config.maxLevel) {
			throw error("The level must be between 0 and " + Config.maxLevel);
		}

		Set<Upgrade> upgrades = EnumSet.noneOf(Upgrade.class);
		if (bracket >= 0) {
			if (!word.endsWith("]")) {
				throw error("Expected ']' at the end of the upgrades");
			}
			for (String id : word.substring(bracket + 1, word.length() - 1).split(",", -1)) {
				Upgrade upgrade = Upgrade.fromId(id);
				if (upgrade == null) {
					throw error("Unknown upgrade '" + id + "'");
				}
				if (!upgrades.add(upgrade)) {
					throw error("Repeated upgrade '" + id + "'");
				}
			}
		}
		return new Spec(level, upgrades, count);
	}

	private static CommandSyntaxException error(String message) {
		return new SimpleCommandExceptionType(MessageComponentSerializer.message().serialize(Component.text(message)))
				.create();
	}

	@Override
	public ArgumentType<String> getNativeType() {
		return StringArgumentType.greedyString();
	}

	/** After '[' and after each comma, suggests the upgrades not written yet. */
	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> ctx, SuggestionsBuilder builder) {
		String typed = builder.getRemaining();
		int bracket = typed.indexOf('[');
		if (bracket >= 0 && !typed.endsWith("]") && typed.indexOf(' ') < 0) {
			int last = Math.max(bracket, typed.lastIndexOf(','));
			String prefix = typed.substring(0, last + 1);
			String written = typed.substring(bracket + 1);
			for (Upgrade upgrade : Upgrade.values()) {
				if (!(',' + written + ',').contains("," + upgrade.id() + ",")
						&& upgrade.id().startsWith(typed.substring(last + 1))) {
					builder.suggest(prefix + upgrade.id());
				}
			}
		}
		return builder.buildFuture();
	}
}
