package vontus.magicbottle;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

import java.util.ArrayList;

public class MagicBottle {
	public static final Material materialFilled = Material.DRAGONS_BREATH;
	public static final Material materialEmpty = Material.GLASS_BOTTLE;
	private static final int XP_LINE = 1;
	private static final int DURABILITY_POINTS_PER_XP = 2;
	private ItemStack item;
	private Integer exp;

	MagicBottle(int exp) {
		this.exp = exp;
		recreate();
	}

	MagicBottle(ItemStack expContainer) {
		item = expContainer;
		exp = calculateExp(expContainer);
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
					int xpToUse = repairable / 2 + remainder;
					exp -= xpToUse;
					i.setDurability((short) (i.getDurability() - repairable - remainder));
					recreate();
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
		meta.addEnchant(Config.bottleEnchantment, 1, true);
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

	private static int calculateExp(ItemStack item) {
		int exp;
		try {
			exp = Integer.valueOf(ChatColor.stripColor((item.getItemMeta().getLore().get(XP_LINE).trim())).replace(",", ""));
		} catch (Exception exception) {
			exp = 0;
		}
		return exp;
	}

	public static boolean isMagicBottle(ItemStack item) {
		return  item != null &&
				item.containsEnchantment(Config.bottleEnchantment) &&
				(item.getType() == materialFilled || item.getType() == materialEmpty)
				;
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
			meta.addEnchant(Config.bottleEnchantment, 1, true);
			is.setItemMeta(meta);
		} else {
			is = new MagicBottle(0).getItem();
		}
		
		return is;
	}
}
