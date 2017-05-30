package vontus.magicbottle;

import java.util.HashSet;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import vontus.magicbottle.Commands;
import vontus.magicbottle.Events;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

public class Plugin extends JavaPlugin {
	public static Logger logger;
	public HashSet<Player> autoEnabled = new HashSet<>();

	@Override
	public void onEnable() {
		logger = getLogger();
		loadConfig();
		new Recipes(this);
		this.getServer().getPluginManager().registerEvents(new Events(this), this);
		this.getCommand("magicbottle").setExecutor(new Commands(this));
	}

	public void loadConfig() {
		this.reloadConfig();

		this.saveDefaultConfig();
		new Config(this);
		new Messages(this);
	}
}
