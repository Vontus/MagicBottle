package vontus.magicbottle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.CustomArgumentType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * A comma-separated list of upgrades to add to or remove from the bottle in the executor's main hand. It is a greedy
 * string for the client, which would paint the commas of a word red, and it suggests only what the bottle lacks (when
 * adding) or has (when removing).
 */
public class UpgradeListArgument implements CustomArgumentType<Set<Upgrade>, String> {
	private final boolean adding;

	public UpgradeListArgument(boolean adding) {
		this.adding = adding;
	}

	@Override
	public Set<Upgrade> parse(StringReader reader) throws CommandSyntaxException {
		String text = reader.getRemaining();
		reader.setCursor(reader.getTotalLength());
		return UpgradeList.parse(text);
	}

	@Override
	public ArgumentType<String> getNativeType() {
		return StringArgumentType.greedyString();
	}

	@Override
	public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> ctx, SuggestionsBuilder builder) {
		if (ctx.getSource() instanceof CommandSourceStack source && source.getExecutor() instanceof Player player) {
			ItemStack held = player.getInventory().getItemInMainHand();
			if (MagicBottle.isMagicBottle(held)) {
				MagicBottle bottle = new MagicBottle(held);
				Set<Upgrade> candidates = EnumSet.noneOf(Upgrade.class);
				for (Upgrade upgrade : Upgrade.values()) {
					if (bottle.hasUpgrade(upgrade) != adding) {
						candidates.add(upgrade);
					}
				}
				UpgradeList.suggest(builder, "", builder.getRemaining(), candidates, null);
			}
		}
		return builder.buildFuture();
	}
}
