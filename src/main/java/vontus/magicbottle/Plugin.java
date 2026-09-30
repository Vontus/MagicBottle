package vontus.magicbottle;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

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
		this.getServer().getPluginManager().registerEvents(new Events(this), this);
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
		this.reloadConfig();

		this.saveDefaultConfig();
		Config.load(this);
		Messages.load(this);
	}
}
