package vontus.magicbottle;

import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import vontus.magicbottle.cauldron.MagicCauldron;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.config.RecipesConfig;

import java.util.HashSet;
import java.util.logging.Logger;

public class Plugin extends JavaPlugin {
	public static Plugin plugin;
	public static Logger logger;
	public HashSet<Player> autoEnabled = new HashSet<>();
	public Metrics metrics;
	Economy econ = null;

	@Override
	public void onEnable() {
		super.onDisable();
		Plugin.plugin = this;
		logger = getLogger();
		setupEconomy();
		loadConfig();
		this.getServer().getPluginManager().registerEvents(new Events(this), this);
		this.getCommand("magicbottle").setExecutor(new Commands(this));
		metrics = new Metrics(this);
	}

	@Override
	public void onDisable() {
		super.onDisable();
		MagicCauldron.removeAll();
	}

	public void loadConfig() {
		this.reloadConfig();

		this.saveDefaultConfig();
		new Config(this);
		new Messages(this);
		new RecipesConfig(this);
		
		if (Config.costMoneyCraftNewBottle != 0 && econ == null) {
			logger.warning("Vault is required to set economy costs. Add Vault or set the recipe cost to 0 to disable this warning.");
			Config.costMoneyCraftNewBottle = 0;
		}
	}

	private boolean setupEconomy() {
		if (getServer().getPluginManager().getPlugin("Vault") == null) {
			return false;
		}
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null) {
			return false;
		}
		econ = rsp.getProvider();
		return econ != null;
	}
}
