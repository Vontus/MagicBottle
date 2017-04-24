package vontus.magicbottle;

import java.lang.reflect.Field;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.enchantments.EnchantmentTarget;
import org.bukkit.enchantments.EnchantmentWrapper;
import org.bukkit.inventory.ItemStack;
// https://github.com/Sothatsit/UsefulSnippets/blob/master/UsefulSnippets/src/me/sothatsit/usefulsnippets/EnchantGlow.java
public class EnchantGlow extends EnchantmentWrapper
{
	
	private static Enchantment glow;
	
	public EnchantGlow()
	{
		super(8883);
	}
	
	@Override
	public boolean canEnchantItem(ItemStack item)
	{
		return true;
	}
	
	@Override
	public boolean conflictsWith(Enchantment other)
	{
		return false;
	}
	
	@Override
	public EnchantmentTarget getItemTarget()
	{
		return null;
	}
	
	@Override
	public int getMaxLevel()
	{
		return 10;
	}
	
	@Override
	public String getName()
	{
		return "Glow";
	}
	
	@Override
	public int getStartLevel()
	{
		return 1;
	}
	
	public static Enchantment getGlow()
	{
		if ( glow != null )
			return glow;
		
		try
		{
			Field f = Enchantment.class.getDeclaredField("acceptingNew");
			f.setAccessible(true);
			f.set(null , true);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		
		glow = new EnchantGlow();
		try {
			Enchantment.registerEnchantment(glow);
		} catch (IllegalArgumentException e) {
			Plugin.logger.severe("Can't register bottle enchantment. This can be ignored if XPBottle has been reloaded with an external plugin.");
		}
		return glow;
	}
	
	public static void addGlow(ItemStack item)
	{
		Enchantment glow = getGlow();
		
		item.addEnchantment(glow , 1);
	}
	
}
