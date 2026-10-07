package vontus.magicbottle;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vontus.magicbottle.config.Config;
import vontus.magicbottle.config.Messages;
import vontus.magicbottle.effects.SoundEffect;
import vontus.magicbottle.util.Exp;
import vontus.magicbottle.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public class MagicBottle {
	// Every bottle is dragon's breath, one of the most inert items (its only use is brewing, which is blocked), while
	// vanilla fills glass bottles with water or honey, dispensers included. Empty bottles only look like glass bottles.
	public static final Material material = Material.DRAGON_BREATH;
	private static final NamespacedKey modelEmpty = NamespacedKey.minecraft("glass_bottle");
	private static final int DURABILITY_POINTS_PER_XP = 2;
	private static NamespacedKey keyBottle;
	private static NamespacedKey keyExp;
	private static NamespacedKey keyUpgrades;
	private ItemStack item;
	private Integer exp;
	// Ids, not Upgrade values: ids this version doesn't know (added by a newer one) are kept when the bottle is rewritten
	private Set<String> upgrades = new LinkedHashSet<>();

	static void init(Plugin plugin) {
		keyBottle = new NamespacedKey(plugin, "bottle");
		keyExp = new NamespacedKey(plugin, "exp");
		keyUpgrades = new NamespacedKey(plugin, "upgrades");
	}

	public MagicBottle(int exp) {
		this.exp = exp;
		recreate();
	}

	public MagicBottle(ItemStack expContainer) {
		LegacyBottle.migrateIfLegacy(expContainer);
		item = expContainer;
		exp = calculateExp(expContainer);
		upgrades = readUpgrades(expContainer);
	}

	private MagicBottle(ItemStack expContainer, int exp) {
		item = expContainer;
		this.exp = exp;
		upgrades = readUpgrades(expContainer);
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
			// Empty 1.5.x bottles were glass bottles (LegacyBottle)
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
			meta.getPersistentDataContainer().remove(keyUpgrades);
		});
		return item;
	}

	public boolean hasUpgrade(Upgrade upgrade) {
		return upgrades.contains(upgrade.id());
	}

	public void addUpgrade(Upgrade upgrade) {
		upgrades.add(upgrade.id());
		recreate();
	}

	// The lore line the bottle shows for the upgrade
	public Component getUpgradeLine(Upgrade upgrade) {
		return Messages.renderItemText(Messages.texts.bottleText.upgrades.get(upgrade), placeholders());
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
	
	/**
	 * Deposits up to the given points of the player's exp. The bottle gains what is left after the deposit cost, up to its
	 * room, and the player pays the fewest points that give that gain, so a deposit that reaches the limit leaves the
	 * bottle exactly full. Refused, taking nothing, if the bottle is full or the cost would leave nothing to store.
	 * Returns whether anything was deposited.
	 */
	public boolean deposit(Player player, int points) {
		if (!hasRoom(player)) {
			int maxLevels = Config.getMaxLevelsFor(player);
			player.sendMessage(Messages.render(Messages.texts.messages.maxLevelReached,
					Placeholder.unparsed("level", Integer.toString(maxLevels))));
			SoundEffect.forbidden(player);
			return false;
		}

		int gain = getDepositGain(player, points);
		if (gain <= 0) {
			player.sendMessage(Messages.render(Messages.texts.messages.nothingToStore));
			SoundEffect.forbidden(player);
			return false;
		}

		exp += gain;
		Exp.setPoints(player, Exp.getPoints(player) - getPointsForGain(player, gain, points));
		recreate();
		SoundEffect.fillBottle(player);
		return true;
	}

	public boolean hasRoom(Player player) {
		return getRoom(player) > 0;
	}

	// Whether a deposit can store anything for the player: never with a cost of 100%
	public static boolean canStore(Player player) {
		return getCostPercentage(player) < 100;
	}

	/**
	 * Stores the exp of a picked up orb. Returns {@code points} minus what the bottle took (the exp that goes to the
	 * player) and reports the exp it gained, after the deposit cost, to {@code gained}. Only the points the bottle takes
	 * pay the cost, and the cost is rounded randomly (unlike {@link #getDepositGain}, which is deterministic). If the
	 * orb doesn't fit, the bottle is filled exactly and takes the fewest points that fill it, with the deterministic cost.
	 */
	public int collect(Player player, int points, IntConsumer gained) {
		int room = getRoom(player);
		if (points <= 0 || room <= 0 || !canStore(player)) {
			return points;
		}
		int taken = points;
		int gain = points - getCollectCost(player, points);
		if (gain > room) {
			gain = room;
			taken = getPointsForGain(player, room, points);
		}
		if (gain > 0) {
			exp += gain;
			recreate();
			gained.accept(gain);
		}
		return points - taken;
	}

	// The cost in percent of points, rounded so that its mean is exactly points * percentage / 100: the part that
	// doesn't make a whole point is paid with that probability (like Unbreaking). Integer math, so a cost that is a
	// whole number of points is never random.
	private static int getCollectCost(Player player, int points) {
		int percentage = getCostPercentage(player);
		long scaled = (long) points * percentage;
		int cost = (int) (scaled / 100);
		if (scaled % 100 > 0 && ThreadLocalRandom.current().nextInt(100) < scaled % 100) {
			cost++;
		}
		return cost;
	}

	// The exp this bottle would gain if the player deposited the given points: what the cost leaves, up to its room
	public int getDepositGain(Player player, int points) {
		return Math.clamp(getGainWithoutLimit(player, points), 0, Math.max(0, getRoom(player)));
	}

	// The fewest points, up to max, whose gain after the cost is at least the given one. The gain grows by 0 or 1 with
	// each point paid, so it's exactly that gain if max reaches it.
	private static int getPointsForGain(Player player, int gain, int max) {
		int low = 0;
		int high = max;
		while (low < high) {
			int mid = low + (high - low) / 2;
			if (getGainWithoutLimit(player, mid) >= gain) {
				high = mid;
			} else {
				low = mid + 1;
			}
		}
		return low;
	}

	// The points minus their cost, rounded to the nearest point
	private static long getGainWithoutLimit(Player player, int points) {
		return points - ((long) points * getCostPercentage(player) + 50) / 100;
	}

	private static int getCostPercentage(Player player) {
		if (player.hasPermission(Config.permDepositCostExempt)) {
			return 0;
		}
		return Math.clamp(Config.settings.costs.deposit.expPercentage, 0, 100);
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

		// In the order of the enum, whatever the order they were applied in; unknown ids have no line
		List<Upgrade> applied = Arrays.stream(Upgrade.values()).filter(this::hasUpgrade).toList();
		if (!applied.isEmpty()) {
			lore.add(Messages.renderItemText(Messages.texts.bottleText.upgradesTitle, placeholders));
			for (Upgrade upgrade : applied) {
				lore.add(getUpgradeLine(upgrade));
			}
		}

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
		if (upgrades.isEmpty()) {
			meta.getPersistentDataContainer().remove(keyUpgrades);
		} else {
			meta.getPersistentDataContainer().set(keyUpgrades, PersistentDataType.LIST.strings(), List.copyOf(upgrades));
		}
		item.setItemMeta(meta);
	}
	
	private TagResolver placeholders() {
		return TagResolver.resolver(
				Placeholder.unparsed("level", Utils.roundInt((int) getLevel())),
				Placeholder.unparsed("points", Utils.roundDouble(getExp())),
				Placeholder.component("xpbar", getXpBar()));
	}
	
	// The exp the bottle can still take for the player; 0 or less if it's full or above the limit
	private int getRoom(Player p) {
		return Config.getMaxFillPointsFor(p) - exp;
	}

	private static void markAsBottle(ItemMeta meta) {
		meta.getPersistentDataContainer().set(keyBottle, PersistentDataType.BYTE, (byte) 1);
		meta.setEnchantmentGlintOverride(true);
	}

	private static int calculateExp(ItemStack item) {
		return item.getItemMeta().getPersistentDataContainer().getOrDefault(keyExp, PersistentDataType.INTEGER, 0);
	}

	private static Set<String> readUpgrades(ItemStack item) {
		List<String> ids = item.getItemMeta().getPersistentDataContainer().get(keyUpgrades, PersistentDataType.LIST.strings());
		return ids == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);
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
	
	/**
	 * The first bottle with the upgrade and accepted by the filter, looking only at the hotbar and the offhand: moving
	 * a bottle out of them switches its upgrades off. Stacks of bottles don't count. Every feature that looks for a
	 * bottle with an upgrade goes through here.
	 */
	public static MagicBottle findWithUpgrade(Player player, Upgrade upgrade, Predicate<MagicBottle> filter) {
		PlayerInventory inv = player.getInventory();
		List<ItemStack> candidates = new ArrayList<>();
		for (int slot = 0; slot < 9; slot++) {
			candidates.add(inv.getItem(slot));
		}
		candidates.add(inv.getItemInOffHand());
		for (ItemStack item : candidates) {
			if (isMagicBottle(item) && item.getAmount() == 1) {
				MagicBottle mb = new MagicBottle(item);
				if (mb.hasUpgrade(upgrade) && filter.test(mb)) {
					return mb;
				}
			}
		}
		return null;
	}
}
