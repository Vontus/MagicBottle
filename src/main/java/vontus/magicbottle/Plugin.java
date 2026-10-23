package vontus.magicbottle;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.util.List;
import java.util.logging.Logger;
import org.bstats.bukkit.Metrics;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import vontus.magicbottle.commands.Commands;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.guide.RecipeMenu;
import vontus.magicbottle.guide.RecipeMenuListener;
import vontus.magicbottle.listeners.AnvilListener;
import vontus.magicbottle.listeners.BottleInteractListener;
import vontus.magicbottle.listeners.ClickCooldown;
import vontus.magicbottle.listeners.CollectListener;
import vontus.magicbottle.listeners.CraftingGridListener;
import vontus.magicbottle.listeners.EnchantListener;
import vontus.magicbottle.listeners.InventoryListener;
import vontus.magicbottle.listeners.LoanListener;
import vontus.magicbottle.listeners.PlayerListener;
import vontus.magicbottle.listeners.RepairListener;
import vontus.magicbottle.listeners.SmithingListener;
import vontus.magicbottle.listeners.SoulboundListener;

public final class Plugin extends JavaPlugin {
    private static final int BSTATS_ID = 1183;

    @SuppressWarnings("NullAway.Init") // set first thing in onEnable
    public static Logger logger;

    public final DebouncedFeedback feedback = new DebouncedFeedback(this);
    public final DebouncedFeedback.Channel autoRepairFeedback = feedback.channel(
            () -> Messages.texts.messages.repair.autoSpent, () -> Config.settings.upgrades.autoRepair.feedback);
    public final DebouncedFeedback.Channel collectFeedback = feedback.channel(
            () -> Messages.texts.messages.collect.stored, () -> Config.settings.upgrades.collect.feedback);
    public final XpLoan loan = new XpLoan(this);
    public final Recipes recipes = new Recipes(this);

    @Override
    public void onEnable() {
        logger = getLogger();
        MagicBottle.init(this);
        loadConfig();
        if (Config.invalidUpgrade) {
            logger.severe(
                    "Disabling MagicBottle: fix the invalid upgrade ingredient in config.yml (see the error above) and restart the server.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        recipes.register(false);
        ClickCooldown cooldown = new ClickCooldown(this);
        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new AnvilListener(this), this);
        pm.registerEvents(new CollectListener(this), this);
        pm.registerEvents(new BottleInteractListener(cooldown), this);
        pm.registerEvents(new CraftingGridListener(this), this);
        pm.registerEvents(new EnchantListener(this), this);
        pm.registerEvents(new InventoryListener(), this);
        pm.registerEvents(new LoanListener(loan), this);
        pm.registerEvents(new RecipeMenuListener(), this);
        pm.registerEvents(new RepairListener(this), this);
        pm.registerEvents(new SmithingListener(this), this);
        pm.registerEvents(new SoulboundListener(), this);
        pm.registerEvents(new PlayerListener(this, cooldown), this);
        Commands commands = new Commands(this);
        this.getLifecycleManager()
                .registerEventHandler(
                        LifecycleEvents.COMMANDS,
                        event -> event.registrar().register(commands.build(), "Main plugin command", List.of("mb")));
        MetricsCharts.register(new Metrics(this, BSTATS_ID));
    }

    @Override
    public void onDisable() {
        // Its click handlers go away with the plugin, so nobody may be left holding the menu open. Borrowed points go
        // back
        // to the bottles too, since the handlers that return them go away as well.
        for (Player p : getServer().getOnlinePlayers()) {
            loan.settle(p, false);
            if (RecipeMenu.isRecipeMenu(p.getOpenInventory().getTopInventory())) {
                p.closeInventory();
            }
        }
    }

    public void loadConfig() {
        Config.load(this);
        Messages.load(this);
    }
}
