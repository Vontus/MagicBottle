# MagicBottle
An easy-to-use Minecraft Bukkit plugin for storing player experience in glass bottles.
[Link to Spigot](https://www.spigotmc.org/resources/magicbottle.40039/)
## Usage
1. Craft a MagicBottle using the recipe below (it can be changed in the plugin config)
>![recipe](http://i.imgur.com/WBsIxcD.png)
2. Transfer your experience
	1. Withdraw:
		- Pressing Right click: withdraws 1 level.
		- Pressing Shift + Right click: withdraws 10 levels.
		- Placing the bottle in a crafting grid: withdraws all the experience from the bottle.
	2. Deposit:
		- Pressing Left click: deposits 1 level.
		- Pressing Shift + Left click: deposits 10 levels.
		- Placing an empty MagicBottle in a crafting grid: saves all your experience in the bottle.
## Install
1. Add the plugin jar in your plugins folder.
2. Start the server.
3. TA-DA!
## Configuration
You can find the default configuration [here](https://github.com/Vontus/MagicBottle/blob/master/src/main/resources/config.yml).
Everything you need to know is documented in it.
## Permissions
- **magicbottle.action.craft**
	- Allows you to craft MagicBottles.
	- Players have this permission by default.
- **magicbottle.action.deposit**
	- Allows you to deposit experience in MagicBottles.
	- Players have this permission by default.
- **magicbottle.action.withdraw**
	- Allows you to withdraw experience from MagicBottles.
	- Players have this permission by default.
- **magicbottle.command.give**
	- Admin command. Gives you or another player a number of bottles of a certain level.
- **magicbottle.command.reload**
	- Admin command. Reloads the plugin config.
- **magicbottle.maxlevel.(name)**
	- This allows you to set different permissions for different maximum bottle levels. You must set the permissions in the plugin config.
- **magicbottle.maxlevel.unlimited**
	- A player that has this permission is allowed to save up to 20,000 levels in a MagicBottle.
## Commands
- **/magicbottle**
	- Aliases: mb, magicb, mbottle
	
