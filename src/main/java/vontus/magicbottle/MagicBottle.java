package vontus.magicbottle;

import net.kyori.adventure.text.Component;
import org.jspecify.annotations.Nullable;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
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
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public final class MagicBottle {
	// Every bottle is dragon's breath, one of the most inert items (its only use is brewing, which is blocked), while
	// vanilla fills glass bottles with water or honey, dispensers included. Empty bottles only look like glass bottles.
	public static final Material material = Material.DRAGON_BREATH;
	// The offhand in PlayerInventory#getItem
	private static final int OFFHAND_SLOT = 40;
	private static final NamespacedKey modelEmpty = NamespacedKey.minecraft("glass_bottle");
	private static final int DURABILITY_POINTS_PER_XP = 2;
	@SuppressWarnings("NullAway.Init") // created by init(), which onEnable calls first
	private static NamespacedKey keyBottle;
	@SuppressWarnings("NullAway.Init")
	private static NamespacedKey keyExp;
	@SuppressWarnings("NullAway.Init")
	private static NamespacedKey keyUpgrades;
	@SuppressWarnings("NullAway.Init")
	private static NamespacedKey keySize;
	private ItemStack item;
	// How many plain bottles this one is worth (see getSize); at least 1, as stored (getSize clamps it to the maximum)
	private int size = 1;
	private Integer exp;
	// Ids, not Upgrade values: ids this version doesn't know (added by a newer one) are kept when the bottle is rewritten
	private Set<String> upgrades = new LinkedHashSet<>();

	static void init(Plugin plugin) {
		keyBottle = new NamespacedKey(plugin, "bottle");
		keyExp = new NamespacedKey(plugin, "exp");
		keyUpgrades = new NamespacedKey(plugin, "upgrades");
		keySize = new NamespacedKey(plugin, "size");
	}

	public MagicBottle(int exp) {
		this(exp, List.of());
	}

	// A new bottle with the upgrades already in it, so the item is printed once
	public MagicBottle(int exp, Collection<Upgrade> upgrades) {
		this.exp = exp;
		upgrades.forEach(upgrade -> this.upgrades.add(upgrade.id()));
		recreate();
	}

	public MagicBottle(ItemStack expContainer) {
		LegacyBottle.migrateIfLegacy(expContainer);
		item = expContainer;
		exp = calculateExp(expContainer);
		upgrades = readUpgrades(expContainer);
		size = readSize(expContainer);
	}

	private MagicBottle(ItemStack expContainer, int exp) {
		item = expContainer;
		this.exp = exp;
		upgrades = readUpgrades(expContainer);
		size = readSize(expContainer);
		recreate();
	}

	// An empty bottle of the given size with the given upgrades (see merge)
	private MagicBottle(int size, Set<String> upgrades) {
		this.exp = 0;
		this.size = size;
		this.upgrades = upgrades;
		recreate();
	}

	// Writes the given exp into an existing item in the current bottle format
	static void rewrite(ItemStack item, int exp) {
		new MagicBottle(item, exp);
	}

	// ItemStack#setType is deprecated in favor of withType, which returns a copy. The conversion has to mutate the stack
	// in place: it is the one that lives in the player's inventory (see LegacyBottle.migrateInventory).
	// TODO: once 2.0.0 is installed on the only server that uses the plugin and its bottles are migrated, remove
	// LegacyBottle and the setType branch below; then this suppression is no longer needed.
	@SuppressWarnings("deprecation")
	private void recreate() {
		if (item == null) {
			item = new ItemStack(material);
		} else if (item.getType() != material) {
			// Empty 1.5.x bottles were glass bottles (LegacyBottle)
			item.setType(material);
		}

		print();
	}

	// Rewrites the single bottles of the inventory that were made before bottles stopped stacking, so they get the limit.
	// Stacks stay as they are (see print).
	public static void limitStackSize(Inventory inv) {
		for (ItemStack item : inv) {
			if (isMagicBottle(item) && item.getAmount() == 1 && item.getMaxStackSize() != 1) {
				new MagicBottle(item).recreate();
			}
		}
	}

	// A copy of a bottle without the bottle markers, so it can't be used as one: for display only
	static ItemStack createDisplayItem(int exp, Upgrade... upgrades) {
		MagicBottle bottle = new MagicBottle(exp, List.of(upgrades));
		ItemStack item = bottle.getItem();
		item.editMeta(meta -> {
			meta.getPersistentDataContainer().remove(keyBottle);
			meta.getPersistentDataContainer().remove(keyExp);
			meta.getPersistentDataContainer().remove(keyUpgrades);
			meta.getPersistentDataContainer().remove(keySize);
		});
		return item;
	}

	/**
	 * The size of the bottle: how many plain bottles' worth of exp it can hold, 1 for a bottle that was never merged
	 * (the key is missing). Never above {@link #getMaxSize()}, so a size edited into an item, or left over from a
	 * higher base capacity in the config, can't go over the hard cap.
	 */
	public int getSize() {
		return Math.clamp(size, 1, getMaxSize());
	}

	/**
	 * The largest size that fits under the hard cap ({@link Config#maxLevel} levels) with the current base capacity.
	 * A merge that would go over it has no result, so no exp is consumed for capacity that would be thrown away.
	 */
	public static int getMaxSize() {
		return (int) ((Exp.getExpAtLevelExact(Config.maxLevel + 1L) - 1) / getBasePoints());
	}

	/**
	 * The most exp this bottle takes in deposits: the points of {@code size} plain bottles (the base capacity in
	 * {@code config.yml}) rounded down to a whole level, so a bottle never has a fractional maximum level. It is
	 * calculated from the size every time, never stored, so the rounding doesn't accumulate across merges and changing
	 * the base capacity rescales every bottle. It needs no player or permission, so it works for any bottle, e.g. one
	 * in a placed block. A bottle can hold more than this (see {@link #isFull()}): that exp is never taken away.
	 */
	public int getCapacity() {
		return Exp.getExpAtLevel(getCapacityLevel());
	}

	/** {@link #getCapacity()} as a whole level. */
	public int getCapacityLevel() {
		return Math.min(Exp.getLevelFloor(getSize() * getBasePoints()), Config.maxLevel);
	}

	/** Whether it holds its capacity or more. */
	public boolean isFull() {
		return exp >= getCapacity();
	}

	private static long getBasePoints() {
		return Exp.getExpAtLevelExact(Math.clamp(Config.settings.capacity, 1, Config.maxLevel));
	}

	/**
	 * The empty bottle that merging the two gives: the sizes added and the union of the upgrades (the ids this version
	 * doesn't know too). Null if either isn't full or the size would go over {@link #getMaxSize()}.
	 */
	public static @Nullable MagicBottle merge(MagicBottle first, MagicBottle second) {
		long size = (long) first.getSize() + second.getSize();
		if (!first.isFull() || !second.isFull() || size > getMaxSize()) {
			return null;
		}
		Set<String> upgrades = new LinkedHashSet<>(first.upgrades);
		upgrades.addAll(second.upgrades);
		return new MagicBottle((int) size, upgrades);
	}

	public boolean hasUpgrade(Upgrade upgrade) {
		return upgrades.contains(upgrade.id());
	}

	public void addUpgrade(Upgrade upgrade) {
		upgrades.add(upgrade.id());
		recreate();
	}

	public void removeUpgrade(Upgrade upgrade) {
		upgrades.remove(upgrade.id());
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
		if (!hasRoom()) {
			player.sendMessage(Messages.render(Messages.texts.messages.maxLevelReached,
					Placeholder.unparsed("level", Integer.toString(getCapacityLevel()))));
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

	public boolean hasRoom() {
		return getRoom() > 0;
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
		int room = getRoom();
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
		return Math.clamp(getGainWithoutLimit(player, points), 0, getRoom());
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
				int usedDurability = Utils.getDamage(i);
				if (usedDurability >= DURABILITY_POINTS_PER_XP || fullRepair) {
					int repairable = Math.min(exp * DURABILITY_POINTS_PER_XP, usedDurability);
					int remainder = fullRepair ? repairable % 2 : 0;
					int xpToUse = repairable / 2 + remainder;
					exp -= xpToUse;
					Utils.setDamage(i, usedDurability - repairable);
					return xpToUse;
				}
			}
		}
		return 0;
	}

	// The fill of the bottle (exp / capacity), rounded down: it is only complete when the bottle is full
	private Component getXpBar() {
		int barParts = 18; //To match Minecraft's xp bar parts
		int filledParts = isFull() ? barParts : (int) (Math.max(0, exp) * (long) barParts / getCapacity());

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
		if (getSize() > 1) {
			lore.add(Messages.renderItemText(Messages.texts.bottleText.capacityTitle, placeholders));
			lore.add(Messages.renderItemText(Messages.texts.bottleText.capacity, placeholders));
		}

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
		String name = isFull() ? Messages.texts.bottleText.nameFull : Messages.texts.bottleText.name;
		meta.displayName(Messages.renderItemText(name, placeholders));
		meta.lore(lore);
		// null keeps dragon's breath's own model
		meta.setItemModel(isEmpty() ? modelEmpty : null);
		// Bottles don't stack (identical full ones would fit 64 in a slot, and merging frees slots). A stack that already
		// exists keeps stacking: an item with more than its max stack size may fail validation. It gets the limit once
		// split, whenever the bottle is rewritten (limitStackSize does it for the inventories of a joining player).
		if (item.getAmount() == 1) {
			meta.setMaxStackSize(1);
		}
		markAsBottle(meta);
		meta.getPersistentDataContainer().set(keyExp, PersistentDataType.INTEGER, exp);
		// A missing key means 1: bottles that were never merged are written as they always were
		if (size > 1) {
			meta.getPersistentDataContainer().set(keySize, PersistentDataType.INTEGER, size);
		} else {
			meta.getPersistentDataContainer().remove(keySize);
		}
		if (upgrades.isEmpty()) {
			meta.getPersistentDataContainer().remove(keyUpgrades);
		} else {
			meta.getPersistentDataContainer().set(keyUpgrades, PersistentDataType.LIST.strings(), List.copyOf(upgrades));
		}
		item.setItemMeta(meta);
	}
	
	private TagResolver placeholders() {
		return TagResolver.resolver(
				Placeholder.unparsed("level", Utils.roundInt(Exp.getLevelFloor(exp))),
				Placeholder.unparsed("max_level", Utils.roundInt(getCapacityLevel())),
				Placeholder.unparsed("size", Integer.toString(getSize())),
				Placeholder.unparsed("points", Utils.roundDouble(getExp())),
				Placeholder.unparsed("max_points", Utils.roundInt(getCapacity())),
				Placeholder.component("xpbar", getXpBar()));
	}
	
	// The exp the bottle can still take; 0 if it's full or above its capacity (it keeps what it has, see getCapacity)
	private int getRoom() {
		return Math.max(0, getCapacity() - exp);
	}

	private static void markAsBottle(ItemMeta meta) {
		meta.getPersistentDataContainer().set(keyBottle, PersistentDataType.BYTE, (byte) 1);
		meta.setEnchantmentGlintOverride(true);
	}

	private static int calculateExp(ItemStack item) {
		return item.getItemMeta().getPersistentDataContainer().getOrDefault(keyExp, PersistentDataType.INTEGER, 0);
	}

	private static int readSize(ItemStack item) {
		return Math.max(1, item.getItemMeta().getPersistentDataContainer().getOrDefault(keySize, PersistentDataType.INTEGER, 1));
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
	 * The first bottle accepted by the filter: the one in the selected slot, then the offhand, then the rest of the hotbar
	 * and, with {@code wholeInventory}, the rest of the inventory. Armor slots and stacks of bottles don't count. Every
	 * feature that looks for a bottle to use goes through here.
	 */
	public static @Nullable MagicBottle find(Player player, boolean wholeInventory, Predicate<MagicBottle> filter) {
		int slot = findSlot(player, wholeInventory, filter);
		return slot < 0 ? null : bottleAt(player, slot, filter);
	}

	// The slot of the bottle find returns, or -1
	private static int findSlot(Player player, boolean wholeInventory, Predicate<MagicBottle> filter) {
		PlayerInventory inv = player.getInventory();
		List<Integer> slots = new ArrayList<>();
		slots.add(inv.getHeldItemSlot());
		slots.add(OFFHAND_SLOT);
		for (int slot = 0; slot < (wholeInventory ? 36 : 9); slot++) {
			if (slot != inv.getHeldItemSlot()) {
				slots.add(slot);
			}
		}
		for (int slot : slots) {
			if (bottleAt(player, slot, filter) != null) {
				return slot;
			}
		}
		return -1;
	}

	// The bottle in that slot of the player's inventory if it is a single one accepted by the filter
	private static @Nullable MagicBottle bottleAt(Player player, int slot, Predicate<MagicBottle> filter) {
		ItemStack item = player.getInventory().getItem(slot);
		if (isMagicBottle(item) && item.getAmount() == 1) {
			MagicBottle mb = new MagicBottle(item);
			if (filter.test(mb)) {
				return mb;
			}
		}
		return null;
	}

	/**
	 * The first bottle with the upgrade and accepted by the filter, looking only at the hotbar and the offhand: moving
	 * a bottle out of them switches its upgrades off.
	 */
	public static @Nullable MagicBottle findWithUpgrade(Player player, Upgrade upgrade, Predicate<MagicBottle> filter) {
		return find(player, false, mb -> mb.hasUpgrade(upgrade) && filter.test(mb));
	}

	/**
	 * Takes exactly the points out of the first bottle (see {@link #find}) that has them, for {@link XpLoan}: the caller gives
	 * the points to the player and records them. Returns the slot of that bottle, or -1 if none had them (nothing changes).
	 */
	static int lend(Player player, int points) {
		Predicate<MagicBottle> covers = mb -> mb.exp >= points;
		int slot = findSlot(player, true, covers);
		MagicBottle bottle = slot < 0 ? null : bottleAt(player, slot, covers);
		if (bottle == null) {
			return -1;
		}
		bottle.exp -= points;
		bottle.recreate();
		return slot;
	}

	/**
	 * Puts points back, with no deposit cost (they are points {@link #lend} took), into the bottle in {@code slot} if it is
	 * still there with room for all of them (the one that lent them; -1 if unknown), or else into the first bottle (see
	 * {@link #find}) with room. Returns whether a bottle took them; if not, nothing changes.
	 */
	static boolean repay(Player player, int points, int slot) {
		Predicate<MagicBottle> fits = mb -> mb.getRoom() >= points;
		MagicBottle bottle = slot < 0 ? null : bottleAt(player, slot, fits);
		if (bottle == null) {
			bottle = find(player, true, fits);
		}
		if (bottle == null) {
			return false;
		}
		bottle.exp += points;
		bottle.recreate();
		return true;
	}
}
