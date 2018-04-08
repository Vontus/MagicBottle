package vontus.magicbottle.config;

import java.text.ParseException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;

import org.bukkit.inventory.ItemStack;
import vontus.magicbottle.MagicBottle;
import vontus.magicbottle.Plugin;
import vontus.magicbottle.util.EnchantGlow;
import vontus.magicbottle.util.Exp;

public class Config {
	private static HashMap<String, Integer> maxLevelsPermission;

	private static Plugin plugin;

	public static final String permDeposit = "magicbottle.action.deposit";
	public static final String permWithdraw = "magicbottle.action.withdraw";
	public static final String permCraft = "magicbottle.action.craft";
	public static final String permGive = "magicbottle.command.give";
	public static final String permReload = "magicbottle.command.reload";
	public static final String permRepair = "magicbottle.command.repair";
	public static final String permRepairAuto = "magicbottle.command.repair.auto";
	public static final String permCraftCostExempt = "magicbottle.action.craft.cost.exempt";
	public static final String permDepositCostExempt = "magicbottle.action.deposit.cost.exempt";

	private static final String maxLevelsBasePermission = "magicbottle.maxlevel.";
	private static final String maxLevelsUnlimitedPermission = "magicbottle.maxlevel.unlimited";
	private static final String ANY_ENCHANTMENT = "ANY";

	public static boolean effectSound;
	public static boolean effectParticles;
	public static boolean recipeFill;
	public static boolean recipePour;
	public static boolean recipeNewBottleEnabled;
	public static boolean repairEnabled;
	public static boolean repairAutoEnabled;

	private static int defaultRankMaxLevel;
	public static int maxLevel = 20000;

	public static double costPercentageDeposit;
	public static double costMoneyCraftNewBottle;
	public static boolean costCraftNewBottleChangeLore;

	public static Enchantment bottleEnchantment;

	public static void load(Plugin plugin) {
		Config.plugin = plugin;
		maxLevelsPermission = new HashMap<>();

		effectSound = plugin.getConfig().getBoolean("effect.sound");
		effectParticles = plugin.getConfig().getBoolean("effect.particles");
		recipeFill = plugin.getConfig().getBoolean("recipe.deposit");
		recipePour = plugin.getConfig().getBoolean("recipe.withdraw");
		recipeNewBottleEnabled = plugin.getConfig().getBoolean("recipe.bottle.enabled");
		defaultRankMaxLevel = plugin.getConfig().getInt("max level.default");

		for (String parent : plugin.getConfig().getConfigurationSection("max level.permissions").getKeys(false)) {
			int value = plugin.getConfig().getInt("max level.permissions." + parent);
			maxLevelsPermission.put(parent, value);
		}
		
		repairEnabled = plugin.getConfig().getBoolean("repair.enabled");
		repairAutoEnabled = plugin.getConfig().getBoolean("repair.auto");
		
		costPercentageDeposit = plugin.getConfig().getDouble("costs.deposit.exp-percentage") / 100;
		costMoneyCraftNewBottle = plugin.getConfig().getDouble("costs.craft new bottle.money");
		costCraftNewBottleChangeLore = plugin.getConfig().getBoolean("costs.craft new bottle.change lore");

		if (repairEnabled || repairAutoEnabled) {
			try {
				EnchantParser.parseForBukkit(plugin.getConfig().getString("repair.enchantment"));
			} catch (ParseException e) {
				repairEnabled = false;
				repairAutoEnabled = false;
				Plugin.logger.severe(e.getMessage() + ". Repairing has been disabled.");
			}
		}

		boolean compatDisableCustomEnchantments = plugin.getConfig().getBoolean("compatibility.disable custom enchantments");
		if (compatDisableCustomEnchantments) {
			bottleEnchantment = Enchantment.DIG_SPEED;
		} else {
			bottleEnchantment = EnchantGlow.getGlow();
		}
	}

	public static boolean canRepair(ItemStack is) {
		String enchStr = plugin.getConfig().getString("repair.enchantment");
		if (enchStr.equals(ANY_ENCHANTMENT)) {
			return true;
		} else {
			try {
				Enchantment ench = EnchantParser.parseForBukkit(enchStr);
				return is.containsEnchantment(ench);
			} catch (ParseException e) {
				repairEnabled = false;
				repairAutoEnabled = false;
				Plugin.logger.severe(e.getMessage() + ". Repairing has been disabled.");
				return false;
			}
		}
	}

	public static Material getBottleRecipeIngredient(int pos) {
		return Material.getMaterial(plugin.getConfig().getString("recipe.bottle.recipe." + pos));
	}

	public static int getMaxFillPointsFor(final Player p) {
		return Exp.getExpAtLevel(getMaxLevelsFor(p));
	}
	
	public static int getMaxLevelsFor(final Player p) {
		int max = -1;

		if (p.hasPermission(maxLevelsUnlimitedPermission))
			max = Config.maxLevel;
		else
			for (Entry<String, Integer> entry : maxLevelsPermission.entrySet()) {
				if (p.hasPermission(maxLevelsBasePermission + entry.getKey())) {
					max = Math.max(max, maxLevelsPermission.get(entry.getKey()));
				}
			}

		if (max == -1)
			max = defaultRankMaxLevel;
		return max;
	}
}
