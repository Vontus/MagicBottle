package vontus.magicbottle.config;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

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
	private static final Enchantment KNOCKBACK = Enchantment.KNOCKBACK;
	private static final Enchantment FORTUNE = Enchantment.LOOT_BONUS_BLOCKS;
	private static final Enchantment LOOTING = Enchantment.LOOT_BONUS_MOBS;
	private static final Enchantment LUCK_OF_THE_SEA = Enchantment.LUCK;
	private static final Enchantment LURE = Enchantment.LURE;
	private static final Enchantment WATER_BREATHING = Enchantment.OXYGEN;
	private static final Enchantment PROTECTION = Enchantment.PROTECTION_ENVIRONMENTAL;
	private static final Enchantment BLAST_PROTECTION = Enchantment.PROTECTION_EXPLOSIONS;
	private static final Enchantment FEATHER_FALLING = Enchantment.PROTECTION_FALL;
	private static final Enchantment FIRE_PROTECTION = Enchantment.PROTECTION_FIRE;
	private static final Enchantment PROJECTILE_PROTECTION = Enchantment.PROTECTION_PROJECTILE;
	private static final Enchantment SILK_TOUCH = Enchantment.SILK_TOUCH;
	private static final Enchantment THORNS = Enchantment.THORNS;
	private static final Enchantment AQUA_AFFINITY = Enchantment.WATER_WORKER;

	private static final String ANY_ENCHANTMENT = "ANY";

	private final Enchantment ench;
	private final boolean anyEnchant;

	private EnchantParser(Enchantment e) {
		ench = e;
		anyEnchant = false;
	}

	private EnchantParser(boolean anyEnchant) {
		ench = null;
		this.anyEnchant = anyEnchant;
	}

	public boolean canRepair(ItemStack item) {
		return anyEnchant || (ench != null && item.containsEnchantment(ench));
	}

	public static EnchantParser parseForBukkit(String enchantString) throws ParseException {
		EnchantParser ep;
		switch (enchantString) {
			case ANY_ENCHANTMENT:
				ep = new EnchantParser(true);
				break;
			case "POWER":
				ep = new EnchantParser(EnchantParser.POWER);
				break;
			case "FLAME":
				ep = new EnchantParser( EnchantParser.FLAME);
				break;
			case "INFINITY":
				ep = new EnchantParser( EnchantParser.INFINITY);
				break;
			case "PUNCH":
				ep = new EnchantParser( EnchantParser.PUNCH);
				break;
			case "SHARPNESS":
				ep = new EnchantParser( EnchantParser.SHARPNESS);
				break;
			case "BANE_OF_THE_ARTHROPODS":
				ep = new EnchantParser( EnchantParser.BANE_OF_THE_ARTHROPODS);
				break;
			case "SMITE":
				ep = new EnchantParser( EnchantParser.SMITE);
				break;
			case "DEPTH_STRIDER":
				ep = new EnchantParser( EnchantParser.DEPTH_STRIDER);
				break;
			case "EFFICIENCY":
				ep = new EnchantParser( EnchantParser.EFFICIENCY);
				break;
			case "UNBREAKING":
				ep = new EnchantParser( EnchantParser.UNBREAKING);
				break;
			case "FIRE_ASPECT":
				ep = new EnchantParser( EnchantParser.FIRE_ASPECT);
				break;
			case "KNOCKBACK":
				ep = new EnchantParser( EnchantParser.KNOCKBACK);
				break;
			case "FORTUNE":
				ep = new EnchantParser( EnchantParser.FORTUNE);
				break;
			case "LOOTING":
				ep = new EnchantParser( EnchantParser.LOOTING);
				break;
			case "LUCK_OF_THE_SEA":
				ep = new EnchantParser( EnchantParser.LUCK_OF_THE_SEA);
				break;
			case "LURE":
				ep = new EnchantParser( EnchantParser.LURE);
				break;
			case "WATER_BREATHING":
				ep = new EnchantParser( EnchantParser.WATER_BREATHING);
				break;
			case "PROTECTION":
				ep = new EnchantParser( EnchantParser.PROTECTION);
				break;
			case "BLAST_PROTECTION":
				ep = new EnchantParser( EnchantParser.BLAST_PROTECTION);
				break;
			case "FEATHER_FALLING":
				ep = new EnchantParser( EnchantParser.FEATHER_FALLING);
				break;
			case "FIRE_PROTECTION":
				ep = new EnchantParser( EnchantParser.FIRE_PROTECTION);
				break;
			case "PROJECTILE_PROTECTION":
				ep = new EnchantParser( EnchantParser.PROJECTILE_PROTECTION);
				break;
			case "SILK_TOUCH":
				ep = new EnchantParser( EnchantParser.SILK_TOUCH);
				break;
			case "THORNS":
				ep = new EnchantParser( EnchantParser.THORNS);
				break;
			case "AQUA_AFFINITY":
				ep = new EnchantParser(EnchantParser.AQUA_AFFINITY);
				break;
			default:
				throw new ParseException("The enchantment '" + enchantString + "' is not supported", 0);
		}
		return ep;
	}
}
