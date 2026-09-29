package vontus.magicbottle;

import org.bstats.bukkit.Metrics;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

import java.util.HashSet;
import java.util.logging.Logger;

public class Plugin extends JavaPlugin {
	public static Logger logger;
	public HashSet<Player> autoEnabled = new HashSet<>();

	@Override
	public void onEnable() {
		logger = getLogger();
		MagicBottle.init(this);
		loadConfig();
		new Recipes(this);
		this.getServer().getPluginManager().registerEvents(new Events(this), this);
		this.getCommand("magicbottle").setExecutor(new Commands(this));
		new Metrics(this);
	}

	public void loadConfig() {
		this.reloadConfig();

		this.saveDefaultConfig();
		Config.load(this);
		Messages.load(this);
	}
}
