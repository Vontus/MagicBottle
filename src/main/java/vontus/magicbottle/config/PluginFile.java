package vontus.magicbottle.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.util.logging.Level;

public class PluginFile {

	private final String fileName;
	private final JavaPlugin plugin;

	private File file;
	private FileConfiguration configFile;

	public PluginFile(JavaPlugin plugin, String fileName) {

		if (plugin == null)
			throw new IllegalArgumentException("plugin cannot be null");
		this.plugin = plugin;
		this.fileName = fileName;
		File dataFolder = plugin.getDataFolder();
		if (dataFolder == null)
			throw new IllegalStateException();
		this.file = new File(plugin.getDataFolder(), fileName);

		saveDefaultConfig();
	}

	public void save() {
		if (configFile != null && file != null) {
			try {
				getConfig().save(file);
			} catch (IOException ex) {
				plugin.getLogger().log(Level.SEVERE, "Could not save config to " + file, ex);
			}
		}
	}

	public void reloadConfig() {
		configFile = YamlConfiguration.loadConfiguration(file);

		// Look for defaults in the jar
		InputStream defConfigStream = plugin.getResource(fileName);
		if (defConfigStream != null) {
			YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream));
			configFile.setDefaults(defConfig);
		}
	}

	public FileConfiguration getConfig() {
		if (configFile == null) {
			this.reloadConfig();
		}
		return configFile;
	}

	public void saveDefaultConfig() {
		if (!file.exists()) {
			InputStream inJarFile = plugin.getResource(fileName);
			if (inJarFile != null)
				this.plugin.saveResource(fileName, false);
		}
	}

	public void loadDefaults(String fileName) {
		Reader defConfigStream = null;
		InputStream input = plugin.getResource(fileName);
		if (input != null) {
			try {
				defConfigStream = new InputStreamReader(plugin.getResource(fileName), "UTF8");
				if (defConfigStream != null) {
					YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(defConfigStream);
					configFile.setDefaults(defConfig);
					defConfigStream.close();
				}
			} catch (IOException e) {
				plugin.getLogger().severe("Could not read config file " + fileName + " in the plugin jar.");
				e.printStackTrace();
			}
		} else {
			YamlConfiguration defConfig = YamlConfiguration
					.loadConfiguration(new File(plugin.getDataFolder() + fileName));
			configFile.setDefaults(defConfig);
		}
	}
}
