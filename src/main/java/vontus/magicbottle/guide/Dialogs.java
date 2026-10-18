package vontus.magicbottle.guide;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Small helpers over Paper's dialog API for a column of buttons under a body. They know nothing about bottles. */
final class Dialogs {
    private static final int BUTTON_WIDTH = 200;
    private static final int TEXT_WIDTH = 300;
    private static final int ITEM_TEXT_WIDTH = 250;

    // A callback lives 30 minutes (the default is 12 hours) and can be used any number of times (the default is
    // once), so a guide left open keeps working
    private static final ClickCallback.Options CALLBACK = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(30))
            .build();

    private Dialogs() {}

    /** A button that runs the action on the player who clicks it. */
    static ActionButton button(Component label, Consumer<Player> action) {
        return ActionButton.builder(label)
                .width(BUTTON_WIDTH)
                .action(DialogAction.customClick(
                        (view, audience) -> {
                            if (audience instanceof Player player) {
                                action.accept(player);
                            }
                        },
                        CALLBACK))
                .build();
    }

    /** A button that opens the URL (the client asks the player to confirm). */
    static ActionButton linkButton(Component label, String url) {
        return ActionButton.builder(label)
                .width(BUTTON_WIDTH)
                .action(DialogAction.staticAction(ClickEvent.openUrl(url)))
                .build();
    }

    static DialogBody text(Component text) {
        return DialogBody.plainMessage(text, TEXT_WIDTH);
    }

    /** The item with its tooltip, and a description to its right. */
    static DialogBody item(ItemStack item, Component description) {
        return DialogBody.item(item)
                .description(DialogBody.plainMessage(description, ITEM_TEXT_WIDTH))
                .build();
    }

    /** Shows a dialog that does not pause the game (it would in a single player world) and closes with Escape. */
    static void show(Player player, Component title, List<DialogBody> body, List<ActionButton> buttons) {
        player.showDialog(Dialog.create(factory -> factory.empty()
                .base(DialogBase.builder(title)
                        .body(body)
                        .canCloseWithEscape(true)
                        .pause(false)
                        .afterAction(DialogBase.DialogAfterAction.NONE)
                        .build())
                .type(DialogType.multiAction(buttons).columns(1).build())));
    }
}
