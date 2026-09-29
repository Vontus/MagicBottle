package vontus.magicbottle;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

import java.util.ArrayList;

public class MagicBottle {
	public static final Material materialFilled = Material.DRAGON_BREATH;
	public static final Material materialEmpty = Material.GLASS_BOTTLE;
	private static final int DURABILITY_POINTS_PER_XP = 2;
	// Bottles made by 1.5.x were marked with a hidden Efficiency enchantment and kept their exp in this lore line
	private static final Enchantment LEGACY_ENCHANTMENT = Enchantment.EFFICIENCY;
	private static final int LEGACY_XP_LINE = 1;
	private static NamespacedKey keyBottle;
	private static NamespacedKey keyExp;
	private ItemStack item;
	private Integer exp;

	static void init(Plugin plugin) {
		keyBottle = new NamespacedKey(plugin, "bottle");
		keyExp = new NamespacedKey(plugin, "exp");
	}

	MagicBottle(int exp) {
		this.exp = exp;
		recreate();
	}

	MagicBottle(ItemStack expContainer) {
		item = expContainer;
		exp = calculateExp(expContainer);
		if (isLegacyBottle(expContainer)) {
			// Legacy bottle: rewrite it in the current format
			recreate();
		}
	}
	
	private void recreate() {
		Material mat;
		if (exp > 0) {
			mat = materialFilled;
		} else {
			mat = materialEmpty;
		}
		
		if (item == null) {
			item = new ItemStack(mat);
		} else {
			if (item.getType() != mat)
				item.setType(mat);
		}
		
		print();
	}

	public ItemStack getItem() {
		return item;
	}
	
	public void setExp(int exp) {
		this.exp = exp;
		recreate();
	}

	public double getLevel() {
		return Exp.getLevelFromExp(exp);
	}

	public Integer getExp() {
		return exp;
	}
	
	public boolean isEmpty() {
		return exp <= 0;
	}
	
	public void deposit(Player player, int points) {
		points = getMaxFillablePoints(player, points);
		
		if (points > 0) {
			int expCost = getCost(player, points);
			exp += points - expCost;
			Exp.setPoints(player, Exp.getPoints(player) - points);
			recreate();
			SoundEffect.fillBottle(player);
		} else {
			int maxLevels = Config.getMaxLevelsFor(player);
			player.sendMessage(Messages.msgMaxLevelReached.replace("[level]", Integer.toString(maxLevels)));
			SoundEffect.forbidden(player);
		}
	}
	
	private int getCost(Player player, int points) {
		if (player.hasPermission(Config.permDepositCostExempt)) {
			return 0;
		} else {
			return (int) Math.round(points * Config.costPercentageDeposit);
		}
	}
	
	public int withdraw(Player player, int points) {
		points = Math.min(exp, points);
		exp -= points;
		Exp.givePoints(player, points);
		recreate();
		SoundEffect.pourBottle(player);
		return points;
	}
	
	public int repair(PlayerInventory inv, boolean fullRepair) {
		int usedXP = 0;
		usedXP += repairNoRecreate(inv.getItemInMainHand(), fullRepair);
		usedXP += repairNoRecreate(inv.getItemInOffHand(), fullRepair);
		for (int i = 0; i < inv.getSize(); i++) {
			usedXP += repairNoRecreate(inv.getItem(i), fullRepair);
		}
		recreate();
		return usedXP;
	}
	
	public int repair(ItemStack i, boolean fullRepair) {
		int usedXP = repairNoRecreate(i, fullRepair);
		if (usedXP > 0) {
			recreate();
		}
		return usedXP;
	}

	private int repairNoRecreate(ItemStack i, boolean fullRepair) {
		if (Utils.getMaterial(i) != Material.AIR) {
			if (Config.canRepair(i)) {
				short usedDurability = i.getDurability();
				if (usedDurability >= DURABILITY_POINTS_PER_XP || fullRepair) {
					int repairable = Math.min(exp * DURABILITY_POINTS_PER_XP, usedDurability);
					int remainder = fullRepair ? repairable % 2 : 0;
					int xpToUse = (int)Math.floor(repairable / 2) + remainder;
					exp -= xpToUse;
					i.setDurability((short) (i.getDurability() - repairable - remainder));
					return xpToUse;
				}
			}
		}
		return 0;
	}

	private String getXpBar() {
		int barParts = 18; //To match Minecraft's xp bar parts
		double level = getLevel();
		long integerPart = (long) level;
		double decimalPart = level - integerPart;
		int coloredNumber = (int) (decimalPart * barParts);
		
		StringBuilder bar = new StringBuilder();
		for (int i = 0; i < barParts; i++) {
			if (i < coloredNumber) {
				bar.append(Messages.bottleFilledBarColor);
			} else {
				bar.append(Messages.bottleEmptyBarColor);
			}
			bar.append("|");
		}
		return ChatColor.translateAlternateColorCodes('&', bar.toString());
	}

	private void print() {
		ArrayList<String> tag = new ArrayList<>();
		tag.add(0, Messages.bottleLevelText);
		tag.add(1, String.valueOf(Messages.bottleLevelFormat) + Utils.roundDouble(getExp()));
		
		for (String line : Messages.bottleLore) {
			line = replaceVariables(line);
			tag.add(line);
		}
		
		ItemMeta meta = item.getItemMeta();
		String name = replaceVariables(Messages.bottleName);
		meta.setDisplayName(name);
		meta.setLore(tag);
		markAsBottle(meta);
		meta.getPersistentDataContainer().set(keyExp, PersistentDataType.INTEGER, exp);
		meta.removeEnchant(LEGACY_ENCHANTMENT);
		meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
		item.setItemMeta(meta);
	}
	
	private String replaceVariables(String line) {
		String level = Utils.roundInt((int)getLevel());
		String points = Utils.roundDouble(getExp());
		line = replaceStaticVariables(line);
		
		return line.replace(Messages.levelReplacer, level)
				.replace(Messages.xpPointsReplacer, points)
				.replace(Messages.xpBarReplacer, getXpBar());
	}
	
	private static String replaceStaticVariables(String line) {
		return line.replace(Messages.moneyReplacer, Double.toString(Config.costMoneyCraftNewBottle));
	}
	
	public Integer getMaxFillablePoints(Player p, int points) {
		int maxPoints = Config.getMaxFillPointsFor(p);

		if (exp + points >= maxPoints)
			points = maxPoints - exp;
		
		return points;
	}

	private static void markAsBottle(ItemMeta meta) {
		meta.getPersistentDataContainer().set(keyBottle, PersistentDataType.BYTE, (byte) 1);
		meta.setEnchantmentGlintOverride(true);
	}

	private static int calculateExp(ItemStack item) {
		PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
		Integer stored = pdc.get(keyExp, PersistentDataType.INTEGER);
		if (stored != null) {
			return stored;
		}
		Integer legacyExp = parseLegacyExp(item);
		return legacyExp != null ? legacyExp : 0;
	}

	private static Integer parseLegacyExp(ItemStack item) {
		try {
			String line = item.getItemMeta().getLore().get(LEGACY_XP_LINE);
			return Integer.parseInt(ChatColor.stripColor(line).trim().replace(",", ""));
		} catch (Exception e) {
			return null;
		}
	}

	private static boolean hasBottleMarker(ItemStack item) {
		return item.getItemMeta().getPersistentDataContainer().has(keyBottle, PersistentDataType.BYTE);
	}

	private static boolean isLegacyBottle(ItemStack item) {
		return !hasBottleMarker(item) &&
				item.containsEnchantment(LEGACY_ENCHANTMENT) &&
				parseLegacyExp(item) != null;
	}

	public static boolean isMagicBottle(ItemStack item) {
		return item != null &&
				(item.getType() == materialFilled || item.getType() == materialEmpty) &&
				(hasBottleMarker(item) || isLegacyBottle(item));
	}
	
	public static void migrateLegacyBottles(Inventory inv) {
		for (ItemStack item : inv) {
			if (item != null && isMagicBottle(item) && isLegacyBottle(item)) {
				new MagicBottle(item);
			}
		}
	}

	public static boolean isUsableMagicBottle(ItemStack item) {
		if (isMagicBottle(item) && item.getAmount() == 1) {
			MagicBottle mb = new MagicBottle(item);
			return !mb.isEmpty();
		} else {
			return false;
		}
	}
	
	public static MagicBottle getUsableMBInInventory(Inventory inv) {
		for (ItemStack item : inv) {
			if (isUsableMagicBottle(item)) {
				MagicBottle mb = new MagicBottle(item);
				if (!mb.isEmpty())
					return mb;
			}
		}
		return null;
	}
	
	public static ItemStack getPreMagicBottle() {
		ItemStack is;
		if (Config.costCraftNewBottleChangeLore) {
			is = new ItemStack(materialEmpty);
			ItemMeta meta = is.getItemMeta();
			meta.setDisplayName(replaceStaticVariables(Messages.newBottleName));
			ArrayList<String> lore = new ArrayList<>();

			for (String line : Messages.newBottleLore) {
				line = replaceStaticVariables(line);
				lore.add(line);
			}
			meta.setLore(lore);
			markAsBottle(meta);

			is.setItemMeta(meta);
		} else {
			is = new MagicBottle(0).getItem();
		}
		
		return is;
	}
}
