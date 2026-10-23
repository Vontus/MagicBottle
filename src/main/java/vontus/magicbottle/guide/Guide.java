package vontus.magicbottle.guide;

import static vontus.magicbottle.guide.Dialogs.button;
import static vontus.magicbottle.guide.Dialogs.item;
import static vontus.magicbottle.guide.Dialogs.linkButton;
import static vontus.magicbottle.guide.Dialogs.text;

import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.Upgrade;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

/**
 * The in-game guide of /magicbottle guide: a home dialog with a button per page, and the pages, each with a button
 * back home. Which pages exist for a player is decided when the guide is opened, from the config and the player's
 * permissions, so /mb reload applies.
 */
public final class Guide {
    private enum Page {
        HAND,
        UPGRADES,
        CAPACITY,
        COSTS,
        REPAIR,
        ABOUT;

        boolean availableTo(Player player) {
            return switch (this) {
                case HAND, ABOUT -> true;
                case UPGRADES -> !upgradesFor(player).isEmpty();
                case CAPACITY -> canMerge(player);
                case COSTS -> Config.settings.spend.enabled && player.hasPermission(Config.permSpend);
                case REPAIR ->
                    (Config.repairEnabled && player.hasPermission(Config.permRepair))
                            || Config.upgradeIngredients.containsKey(Upgrade.AUTO_REPAIR);
            };
        }

        Messages.Page texts() {
            Messages.Guide guide = Messages.texts.messages.guide;
            return switch (this) {
                case HAND -> guide.hand;
                case UPGRADES -> guide.upgrades;
                case CAPACITY -> guide.capacity;
                case COSTS -> guide.costs;
                case REPAIR -> guide.repair;
                case ABOUT -> guide.about;
            };
        }
    }

    private static final String PLUGIN_URL = "https://github.com/Vontus/MagicBottle";

    private final Plugin plugin;

    public Guide(Plugin plugin) {
        this.plugin = plugin;
    }

    /** The lines of the About page, which /magicbottle about sends too. */
    public static List<Component> aboutLines(Plugin plugin) {
        return Messages.texts.messages.guide.about.text.stream()
                .map(line -> Messages.render(
                        line,
                        Placeholder.unparsed("name", plugin.getPluginMeta().getDisplayName())))
                .toList();
    }

    public void open(Player player) {
        Messages.Guide texts = Messages.texts.messages.guide;
        List<ActionButton> buttons = new ArrayList<>();
        // The recipe comes first: it is what a player without a bottle needs
        if (RecipeMenu.hasRecipe(player)) {
            buttons.add(button(Messages.render(texts.recipe), p -> {
                // The config may have changed since the guide was opened
                if (!RecipeMenu.open(plugin, p)) {
                    open(p);
                }
            }));
        }
        for (Page page : Page.values()) {
            if (page.availableTo(player)) {
                buttons.add(button(Messages.render(page.texts().title), p -> openPage(p, page)));
            }
        }
        Dialogs.show(player, Messages.render(texts.title), List.of(text(Messages.render(texts.intro))), buttons);
    }

    private void openPage(Player player, Page page) {
        // The config or the permissions may have changed since the home was shown
        if (!page.availableTo(player)) {
            open(player);
            return;
        }
        Messages.Guide texts = Messages.texts.messages.guide;
        List<DialogBody> body = new ArrayList<>();
        paragraphs(player, page).forEach(line -> body.add(text(line)));
        if (page == Page.UPGRADES) {
            addUpgradeRows(player, body);
        }
        List<ActionButton> buttons = new ArrayList<>();
        if (page == Page.ABOUT) {
            buttons.add(linkButton(Messages.render(texts.link), PLUGIN_URL));
        }
        buttons.add(button(Messages.render(texts.back), this::open));
        Dialogs.show(player, Messages.render(page.texts().title), body, buttons);
    }

    // The text of the page; some paragraphs depend on the config, so they are only there when they apply
    private List<Component> paragraphs(Player player, Page page) {
        Messages.Guide texts = Messages.texts.messages.guide;
        List<Component> paragraphs = new ArrayList<>();
        switch (page) {
            case HAND -> {
                paragraphs.add(Messages.render(texts.hand.text.getFirst()));
                int percent = MagicBottle.getCostPercentage(player);
                if (percent > 0) {
                    paragraphs.add(Messages.render(texts.handCost, Placeholder.unparsed("percent", "" + percent)));
                }
                if (Config.settings.recipe.deposit || Config.settings.recipe.withdraw) {
                    paragraphs.add(Messages.render(texts.handGrid));
                }
            }
            case CAPACITY -> {
                MagicBottle full = MagicBottle.createFull();
                MagicBottle merged = MagicBottle.merge(full, MagicBottle.createFull());
                TagResolver[] levels = {
                    Placeholder.unparsed("level", "" + full.getCapacityLevel()),
                    Placeholder.unparsed("merged_level", merged == null ? "" : "" + merged.getCapacityLevel())
                };
                texts.capacity.text.forEach(line -> paragraphs.add(Messages.render(line, levels)));
            }
            case REPAIR -> {
                texts.repair.text.forEach(line -> paragraphs.add(Messages.render(line)));
                Enchantment required = Config.settings.repair.enchantment.getRequiredEnchantment();
                if (required != null) {
                    paragraphs.add(Messages.render(
                            texts.repairEnchantment, Placeholder.component("enchantment", required.displayName(1))));
                }
            }
            case ABOUT -> paragraphs.addAll(aboutLines(plugin));
            case UPGRADES, COSTS -> page.texts().text.forEach(line -> paragraphs.add(Messages.render(line)));
        }
        return paragraphs;
    }

    // The enabled upgrades the player may apply
    private static List<Upgrade> upgradesFor(Player player) {
        return Arrays.stream(Upgrade.values())
                .filter(upgrade -> Config.upgradeIngredients.containsKey(upgrade)
                        && player.hasPermission(upgrade.applyPermission()))
                .toList();
    }

    // Whether the player can merge, and two bottles of the base capacity fit in one (they may not, with a huge
    // capacity)
    private static boolean canMerge(Player player) {
        return Config.settings.merge.enabled
                && player.hasPermission(Config.permMerge)
                && MagicBottle.merge(MagicBottle.createFull(), MagicBottle.createFull()) != null;
    }

    // The ingredient that applies each upgrade, described by the lore line of the upgrade
    private static void addUpgradeRows(Player player, List<DialogBody> body) {
        for (Upgrade upgrade : upgradesFor(player)) {
            // An ingredient that accepts several items (a tag) is shown with the first
            if (Config.upgradeIngredients.get(upgrade) instanceof RecipeChoice.MaterialChoice choice) {
                body.add(item(
                        new ItemStack(choice.getChoices().getFirst()),
                        Messages.render(Messages.texts.bottleText.upgrades.get(upgrade))));
            }
        }
    }
}
