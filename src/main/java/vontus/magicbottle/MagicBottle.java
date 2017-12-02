package vontus.magicbottle;

import java.util.ArrayList;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.EnchantGlow;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

public class MagicBottle {
	public static Material materialFilled = Material.DRAGONS_BREATH;
	public static Material materialEmpty = Material.GLASS_BOTTLE;
	public static Enchantment repairEnchantment = Enchantment.MENDING;
	private ItemStack item;
	private Integer exp;

	public MagicBottle(int exp) {
		this.exp = exp;
		recreate();
	}

	public MagicBottle(ItemStack expContainer) {
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
	
	public int repair(PlayerInventory inv) {
		int usedXP = 0;
		usedXP += repairNoRecreate(inv.getItemInMainHand());
		usedXP += repairNoRecreate(inv.getItemInOffHand());
		for (int i = 0; i < inv.getSize(); i++) {
			usedXP += repairNoRecreate(inv.getItem(i));
		}
		recreate();
		return usedXP;
	}
	
	public int repair(ItemStack i) {
		int usedXP = repairNoRecreate(i);
		if (usedXP > 0) {
			recreate();
		}
		return usedXP;
	}
	
	private int repairNoRecreate(ItemStack i) {
		if (Utils.getMaterial(i) != Material.AIR) {
			if (repairEnchantment == null || i.containsEnchantment(repairEnchantment)) {
				short usedDurability = i.getDurability();
				if (usedDurability >= 2) {
					int repairable = Math.min(exp, usedDurability / 2) * 2;
					exp -= repairable / 2;
					i.setDurability((short) (i.getDurability() - repairable));
					recreate();
					return repairable / 2;
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
		
		String bar = "";
		for (int i = 0; i < barParts; i++) {
			if (i < coloredNumber) {
				bar += Messages.bottleFilledBarColor;
			} else {
				bar += Messages.bottleEmptyBarColor;
			}
			bar += "|";
		}
		return ChatColor.translateAlternateColorCodes('&', bar);
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
		meta.addEnchant(EnchantGlow.getGlow(), 1, true);
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
			exp = Integer.valueOf(ChatColor.stripColor((item.getItemMeta().getLore().get(1).trim())).replace(",", ""));
		} catch (Exception exception) {
			exp = 0;
		}
		return exp;
	}

	public static boolean isMagicBottle(ItemStack item) {
		return item != null && item.containsEnchantment(EnchantGlow.getGlow())
				//&& (item.getType() == materialFilled || item.getType() == materialEmtpy)
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
	
	public static MagicBottle getUsableMBInToolsbar(Player p) {
		for (int i = 0; i < 9; i++) {
			ItemStack item = p.getInventory().getItem(i);
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
			meta.addEnchant(EnchantGlow.getGlow(), 1, true);
			is.setItemMeta(meta);
		} else {
			is = new MagicBottle(0).getItem();
		}
		
		return is;
	}
}
