package vontus.magicbottle;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.listeners.AnvilListener;
import vontus.magicbottle.listeners.BottleInteractListener;
import vontus.magicbottle.listeners.ClickCooldown;
import vontus.magicbottle.listeners.CraftingGridListener;
import vontus.magicbottle.listeners.InventoryListener;
import vontus.magicbottle.listeners.PlayerListener;
import vontus.magicbottle.listeners.RepairListener;

import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

public class Plugin extends JavaPlugin {
	private static final int BSTATS_ID = 1183;

	public static Logger logger;
	public HashSet<Player> autoEnabled = new HashSet<>();
	public AutoRepairFeedback autoRepairFeedback;

	@Override
	public void onEnable() {
		logger = getLogger();
		autoRepairFeedback = new AutoRepairFeedback(this);
		MagicBottle.init(this);
		loadConfig();
		new Recipes(this);
		ClickCooldown cooldown = new ClickCooldown(this);
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvents(new AnvilListener(this), this);
		pm.registerEvents(new BottleInteractListener(cooldown), this);
		pm.registerEvents(new CraftingGridListener(this), this);
		pm.registerEvents(new InventoryListener(), this);
		pm.registerEvents(new RepairListener(this), this);
		pm.registerEvents(new PlayerListener(this, cooldown), this);
		Commands commands = new Commands(this);
		this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
				event.registrar().register(commands.build(), "Main plugin command", List.of("mb", "magicb", "mbottle")));
		new Metrics(this, BSTATS_ID);
	}

	@Override
	public void onDisable() {
		// Its click handlers go away with the plugin, so nobody may be left holding the menu open
		for (Player p : getServer().getOnlinePlayers()) {
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
