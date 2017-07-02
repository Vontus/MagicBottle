package vontus.magicbottle;

import java.util.ArrayList;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;

public class MagicBottle {
	public static Material materialFilled = Material.DRAGONS_BREATH;
	public static Material materialEmtpy = Material.GLASS_BOTTLE;
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
			mat = materialEmtpy;
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
			exp += points;
			Exp.setPoints(player, Exp.getPoints(player) - points);
			recreate();
			playEffectFill(player);
		} else {
			player.sendMessage(Messages.msgMaxLevelReached.replace("%", Integer.toString(Exp.getLevelFromExp(Config.getMaxFillPointsFor(player)).intValue())));
			playEffectForbidden(player);
		}
	}

	public void withdraw(Player player, int points) {
		if (exp < points) {
			Exp.setPoints(player, Exp.getPoints(player) + exp);
			exp = 0;
		} else {
			Exp.setPoints(player, Exp.getPoints(player) + points);
			exp -= points;
		}
		recreate();
		this.playEffectPour(player);
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
		if (i != null && i.getType() != Material.AIR && i.getAmount() == 1 && i.containsEnchantment(Enchantment.MENDING)) {
			short usedDurability = i.getDurability();
			if (usedDurability >= 2) {
				int repairable = Math.min(exp, usedDurability / 2) * 2;
				exp -= repairable / 2;
				i.setDurability((short) (i.getDurability() - repairable));
				recreate();
				return repairable / 2;
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
		
		return line.replace(Messages.levelReplacer, level)
				.replace(Messages.xpPointsReplacer, points)
				.replace(Messages.xpBarReplacer, getXpBar());
	}

	private void playEffectFill(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1);
		if (Config.effectParticles) {
			ParticleEffect.SPELL_WITCH.display(0.1f, 0.1f, 0.1f, 0.1f, 50, player.getLocation(), 50);
		}
	}
	
	private void playEffectForbidden(Player player) {
		if (Config.effectSound)
			player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.2f, 1);
	}

	private void playEffectPour(Player player) {
		if (Config.effectSound) {
			player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1);
		}
		if (Config.effectParticles) {
			Location l = player.getLocation();
			l.setY(l.getY() + 2);
			ParticleEffect.ENCHANTMENT_TABLE.display(0.2f, 0.2f, 0.2f, 1, 50, l, 50);
		}
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
}
