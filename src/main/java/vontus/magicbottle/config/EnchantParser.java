package vontus.magicbottle.config;

import org.bukkit.enchantments.Enchantment;

import java.text.ParseException;

public class EnchantParser {
	private static final Enchantment POWER = Enchantment.ARROW_DAMAGE;
	private static final Enchantment FLAME = Enchantment.ARROW_FIRE;
	private static final Enchantment INFINITY = Enchantment.ARROW_INFINITE;
	private static final Enchantment PUNCH = Enchantment.ARROW_KNOCKBACK;
	private static final Enchantment SHARPNESS = Enchantment.DAMAGE_ALL;
	private static final Enchantment BANE_OF_THE_ARTHROPODS = Enchantment.DAMAGE_ARTHROPODS;
	private static final Enchantment SMITE = Enchantment.DAMAGE_UNDEAD;
	private static final Enchantment DEPTH_STRIDER = Enchantment.DEPTH_STRIDER;
	private static final Enchantment EFFICIENCY = Enchantment.DIG_SPEED;
	private static final Enchantment UNBREAKING = Enchantment.DURABILITY;
	private static final Enchantment FIRE_ASPECT = Enchantment.FIRE_ASPECT;
	private static final Enchantment FROST_WALKER = Enchantment.FROST_WALKER;
	private static final Enchantment KNOCKBACK = Enchantment.KNOCKBACK;
	private static final Enchantment FORTUNE = Enchantment.LOOT_BONUS_BLOCKS;
	private static final Enchantment LOOTING = Enchantment.LOOT_BONUS_MOBS;
	private static final Enchantment LUCK_OF_THE_SEA = Enchantment.LUCK;
	private static final Enchantment LURE = Enchantment.LURE;
	private static final Enchantment MENDING = Enchantment.MENDING;
	private static final Enchantment WATER_BREATHING = Enchantment.OXYGEN;
	private static final Enchantment PROTECTION = Enchantment.PROTECTION_ENVIRONMENTAL;
	private static final Enchantment BLAST_PROTECTION = Enchantment.PROTECTION_EXPLOSIONS;
	private static final Enchantment FEATHER_FALLING = Enchantment.PROTECTION_FALL;
	private static final Enchantment FIRE_PROTECTION = Enchantment.PROTECTION_FIRE;
	private static final Enchantment PROJECTILE_PROTECTION = Enchantment.PROTECTION_PROJECTILE;
	private static final Enchantment SILK_TOUCH = Enchantment.SILK_TOUCH;
	private static final Enchantment THORNS = Enchantment.THORNS;
	private static final Enchantment AQUA_AFFINITY = Enchantment.WATER_WORKER;

	public static Enchantment parseForBukkit(String enchantString) throws ParseException {
		switch (enchantString) {
			case "POWER":
				return EnchantParser.POWER;
			case "FLAME":
				return EnchantParser.FLAME;
			case "INFINITY":
				return EnchantParser.INFINITY;
			case "PUNCH":
				return EnchantParser.PUNCH;
			case "SHARPNESS":
				return EnchantParser.SHARPNESS;
			case "BANE_OF_THE_ARTHROPODS":
				return EnchantParser.BANE_OF_THE_ARTHROPODS;
			case "SMITE":
				return EnchantParser.SMITE;
			case "DEPTH_STRIDER":
				return EnchantParser.DEPTH_STRIDER;
			case "EFFICIENCY":
				return EnchantParser.EFFICIENCY;
			case "UNBREAKING":
				return EnchantParser.UNBREAKING;
			case "FIRE_ASPECT":
				return EnchantParser.FIRE_ASPECT;
			case "FROST_WALKER":
				return EnchantParser.FROST_WALKER;
			case "KNOCKBACK":
				return EnchantParser.KNOCKBACK;
			case "FORTUNE":
				return EnchantParser.FORTUNE;
			case "LOOTING":
				return EnchantParser.LOOTING;
			case "LUCK_OF_THE_SEA":
				return EnchantParser.LUCK_OF_THE_SEA;
			case "LURE":
				return EnchantParser.LURE;
			case "MENDING":
				return EnchantParser.MENDING;
			case "WATER_BREATHING":
				return EnchantParser.WATER_BREATHING;
			case "PROTECTION":
				return EnchantParser.PROTECTION;
			case "BLAST_PROTECTION":
				return EnchantParser.BLAST_PROTECTION;
			case "FEATHER_FALLING":
				return EnchantParser.FEATHER_FALLING;
			case "FIRE_PROTECTION":
				return EnchantParser.FIRE_PROTECTION;
			case "PROJECTILE_PROTECTION":
				return EnchantParser.PROJECTILE_PROTECTION;
			case "SILK_TOUCH":
				return EnchantParser.SILK_TOUCH;
			case "THORNS":
				return EnchantParser.THORNS;
			case "AQUA_AFFINITY":
				return EnchantParser.AQUA_AFFINITY;
			default:
				throw new ParseException("The enchantment '" + enchantString + "' is not supported", 0);
		}
	}
}
