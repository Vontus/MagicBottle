package vontus.magicbottle;

import com.mojang.brigadier.Message;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/** A list of upgrades written as comma-separated ids ({@code collect,autorepair}), shared by the commands that take one. */
final class UpgradeList {
	private UpgradeList() {
	}

	/** The upgrades of the list; an unknown or repeated id is an error. */
	static Set<Upgrade> parse(String text) throws CommandSyntaxException {
		Set<Upgrade> upgrades = EnumSet.noneOf(Upgrade.class);
		for (String id : text.split(",", -1)) {
			Upgrade upgrade = Upgrade.fromId(id);
			if (upgrade == null) {
				throw error("Unknown upgrade '" + id + "'");
			}
			if (!upgrades.add(upgrade)) {
				throw error("Repeated upgrade '" + id + "'");
			}
		}
		return upgrades;
	}

	/**
	 * Suggests how to continue the list: the candidates not written yet after the last comma, then ',' (if a candidate
	 * is left) or the end. {@code head} is what precedes the list in the argument and is kept in every suggestion;
	 * {@code end} is what closes it (null if nothing does, so the list just ends).
	 */
	static void suggest(SuggestionsBuilder builder, String head, String list, Set<Upgrade> candidates, String end) {
		int tokenStart = list.lastIndexOf(',') + 1;
		String prefix = head + list.substring(0, tokenStart);
		String partial = list.substring(tokenStart);
		Set<String> written = Set.copyOf(Arrays.asList(list.split(",", -1)));
		boolean unused = false;
		for (Upgrade upgrade : candidates) {
			if (!written.contains(upgrade.id())) {
				unused = true;
				if (upgrade.id().startsWith(partial)) {
					builder.suggest(prefix + upgrade.id());
				}
			}
		}
		if (Upgrade.fromId(partial) != null) {
			if (end != null) {
				builder.suggest(head + list + end, tip("End of the upgrades"));
			}
			if (unused) {
				builder.suggest(head + list + ",", tip("Another upgrade"));
			}
		}
	}

	static CommandSyntaxException error(String message) {
		return new SimpleCommandExceptionType(tip(message)).create();
	}

	static Message tip(String text) {
		return MessageComponentSerializer.message().serialize(Component.text(text));
	}
}
