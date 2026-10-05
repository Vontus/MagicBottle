package vontus.magicbottle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

import java.util.ArrayList;
import java.util.List;

public class MagicBottle {
	// Every bottle is dragon's breath, one of the most inert items (its only use is brewing, which is blocked), while
	// vanilla fills glass bottles with water or honey, dispensers included. Empty bottles only look like glass bottles.
	public static final Material material = Material.DRAGON_BREATH;
	private static final NamespacedKey modelEmpty = NamespacedKey.minecraft("glass_bottle");
	private static final int DURABILITY_POINTS_PER_XP = 2;
	private static NamespacedKey keyBottle;
	private static NamespacedKey keyExp;
	private ItemStack item;
	private Integer exp;

	static void init(Plugin plugin) {
		keyBottle = new NamespacedKey(plugin, "bottle");
		keyExp = new NamespacedKey(plugin, "exp");
	}

	public MagicBottle(int exp) {
		this.exp = exp;
		recreate();
	}

	public MagicBottle(ItemStack expContainer) {
		LegacyBottle.migrateIfOutdated(expContainer);
		item = expContainer;
		exp = calculateExp(expContainer);
	}

	private MagicBottle(ItemStack expContainer, int exp) {
		item = expContainer;
		this.exp = exp;
		recreate();
	}

	// Writes the given exp into an existing item in the current bottle format
	static void rewrite(ItemStack item, int exp) {
		new MagicBottle(item, exp);
	}

	private void recreate() {
		if (item == null) {
			item = new ItemStack(material);
		} else if (item.getType() != material) {
			// Bottles in an older format may be glass bottles (LegacyBottle)
			item.setType(material);
		}

		print();
	}

	// A copy of an empty bottle without the bottle markers, so it can't be used as one: for display only
	static ItemStack createDisplayItem() {
		ItemStack item = new MagicBottle(0).getItem();
		item.editMeta(meta -> {
			meta.getPersistentDataContainer().remove(keyBottle);
			meta.getPersistentDataContainer().remove(keyExp);
		});
		return item;
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
			exp += getDepositGain(player, points);
			Exp.setPoints(player, Exp.getPoints(player) - points);
			recreate();
			SoundEffect.fillBottle(player);
		} else {
			int maxLevels = Config.getMaxLevelsFor(player);
			player.sendMessage(Messages.render(Messages.texts.messages.maxLevelReached,
					Placeholder.unparsed("level", Integer.toString(maxLevels))));
			SoundEffect.forbidden(player);
		}
	}
	
	// The exp this bottle would gain if the player deposited the given points (after the deposit limit and the cost)
	public int getDepositGain(Player player, int points) {
		points = getMaxFillablePoints(player, points);
		return points > 0 ? points - getCost(player, points) : 0;
	}

	private int getCost(Player player, int points) {
		if (player.hasPermission(Config.permDepositCostExempt)) {
			return 0;
		} else {
			return (int) Math.round(points * Config.costPercentageDeposit);
		}
	}
	
	public int withdraw(Player player, int points) {
		// Don't give the player more points than an int can hold
		points = Math.min(Math.min(exp, points), Integer.MAX_VALUE - Exp.getPoints(player));
		exp -= points;
		Exp.givePoints(player, points);
		recreate();
		SoundEffect.pourBottle(player);
		return points;
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
					i.setDurability((short) (i.getDurability() - repairable));
					return xpToUse;
				}
			}
		}
		return 0;
	}

	private Component getXpBar() {
		int barParts = 18; //To match Minecraft's xp bar parts
		double level = getLevel();
		long integerPart = (long) level;
		double decimalPart = level - integerPart;
		int filledParts = (int) (decimalPart * barParts);

		TextComponent.Builder bar = Component.text();
		for (int i = 0; i < barParts; i++) {
			bar.append(i < filledParts ? Messages.bottleFilledBar : Messages.bottleEmptyBar);
		}
		return bar.build();
	}

	private void print() {
		TagResolver placeholders = placeholders();
		List<Component> lore = new ArrayList<>();
		lore.add(Messages.renderItemText(Messages.texts.bottleText.experienceTitle, placeholders));
		lore.add(Messages.renderItemText(Messages.texts.bottleText.experience, placeholders));

		for (String line : Messages.texts.bottleText.lore) {
			lore.add(Messages.renderItemText(line, placeholders));
		}

		ItemMeta meta = item.getItemMeta();
		meta.displayName(Messages.renderItemText(Messages.texts.bottleText.name, placeholders));
		meta.lore(lore);
		// null keeps dragon's breath's own model
		meta.setItemModel(isEmpty() ? modelEmpty : null);
		markAsBottle(meta);
		meta.getPersistentDataContainer().set(keyExp, PersistentDataType.INTEGER, exp);
		item.setItemMeta(meta);
	}
	
	private TagResolver placeholders() {
		return TagResolver.resolver(
				Placeholder.unparsed("level", Utils.roundInt((int) getLevel())),
				Placeholder.unparsed("points", Utils.roundDouble(getExp())),
				Placeholder.component("xpbar", getXpBar()));
	}
	
	public Integer getMaxFillablePoints(Player p, int points) {
		long maxPoints = Config.getMaxFillPointsFor(p);

		// long, since exp + points can exceed the int range
		if ((long) exp + points >= maxPoints)
			points = (int) (maxPoints - exp);
		
		return points;
	}

	private static void markAsBottle(ItemMeta meta) {
		meta.getPersistentDataContainer().set(keyBottle, PersistentDataType.BYTE, (byte) 1);
		meta.setEnchantmentGlintOverride(true);
	}

	static int calculateExp(ItemStack item) {
		return item.getItemMeta().getPersistentDataContainer().getOrDefault(keyExp, PersistentDataType.INTEGER, 0);
	}

	static boolean hasBottleMarker(ItemStack item) {
		return item.getItemMeta().getPersistentDataContainer().has(keyBottle, PersistentDataType.BYTE);
	}

	public static boolean isMagicBottle(ItemStack item) {
		return item != null && item.hasItemMeta() && (hasBottleMarker(item) || LegacyBottle.isLegacyBottle(item));
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
}
