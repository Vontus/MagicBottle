# MagicBottle
An easy-to-use Minecraft Paper plugin for storing player experience in glass bottles.
[Link to Spigot](https://www.spigotmc.org/resources/magicbottle.40039/)
## Requirements
- [Paper](https://papermc.io/) 26.2. Spigot and older Minecraft versions are not supported.
- Java 25.
## Usage
1. Craft a MagicBottle using the default recipe below (it can be changed in the plugin config, and players can see it in game with `/mb recipe`)

	| | | |
	|---|---|---|
	| Blaze Powder | Ender Chest | Blaze Powder |
	| Glowstone Dust | Dragon's Breath | Glowstone Dust |
	| Gold Block | Gold Block | Gold Block |
2. Transfer your experience
	1. Withdraw:
		- Pressing Right click: withdraws 1 level.
		- Pressing Shift + Right click: withdraws 10 levels.
		- Placing the bottle in a crafting grid: withdraws all the experience from the bottle.
	2. Deposit:
		- Pressing Left click: deposits 1 level.
		- Pressing Shift + Left click: deposits 10 levels.
		- Placing an empty MagicBottle in a crafting grid: saves all your experience in the bottle.
3. Repair your tools and armor with the experience stored in a bottle, see the commands below.
## Install
1. Add the plugin jar in your plugins folder.
2. Start the server.
3. TA-DA!
## Upgrading from 1.5.x
1. Delete your `config.yml` and start the server to generate the new one, then apply your changes again. Its options have changed (see below).
2. Do the same with `messages.yml`. Messages now use MiniMessage instead of `&` color codes, so a customized 1.5.x `messages.yml` is not compatible and must be redone. If you didn't customize it, just delete it so the new one is generated.
3. Your existing bottles are migrated automatically to the new format: bottles in a player's inventory and ender chest are updated when they join, and any other bottle when it is used.
## Configuration
You can find the default configuration [here](https://github.com/Vontus/MagicBottle/blob/master/src/main/resources/config.yml).
Everything you need to know is documented in it. These are the main changes since 1.5.x:
- The recipe to craft new bottles is written like a shaped recipe in a datapack:
	- `shape`: 1 to 3 rows of up to 3 characters, all the rows with the same length. Each character is a slot of the crafting grid and a space is an empty slot.
	- `ingredients`: the item for each character of the shape, as an item ID (`blaze_powder` or `minecraft:blaze_powder`) or an item tag (`"#minecraft:planks"`, quoted because of the `#`).
	- The default recipe uses Dragon's Breath in the center. It can be disabled with `recipe.bottle.enabled`, and it needs a server restart to be changed.
- Crafting bottles no longer has a money cost and Vault is not used anymore.
- `recipe.bottle.allow crafters` lets crafter blocks craft new bottles. Crafters can't check the `magicbottle.action.craft` permission, so turn it off if you use that permission to restrict who can craft bottles.
- `recipe.deposit` and `recipe.withdraw` enable or disable filling and pouring bottles in a crafting grid.
- `repair.enchantment` accepts an enchantment ID (`MENDING` or `minecraft:mending`) or `ANY`, to repair any repairable tool or armor even if it is not enchanted.
## Messages
All the texts the plugin uses, including the name and lore of the bottles, are in `messages.yml`. They are written in [MiniMessage](https://docs.advntr.dev/minimessage/), for example `<gold>Level <level></gold>`. The placeholders that each message accepts are described in the file.
## Commands
- **/magicbottle** (aliases: mb, magicb, mbottle)
	- **/mb about**: shows the plugin version.
	- **/mb give &lt;level&gt; [amount] [player]**: gives you or another player a number of bottles of a certain level.
	- **/mb recipe**: shows the recipe to craft a new bottle.
	- **/mb repair**: repairs the tools and armor in your inventory using the bottle you hold in your main hand.
	- **/mb autorepair [on|off]**: turns on or off (or toggles, if not specified) the automatic repair of the tools and armor you use, using a bottle in your inventory.
	- **/mb reload**: reloads the plugin config.
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
- **magicbottle.action.deposit.cost.exempt**
	- Exempts you from paying the experience cost of saving experience into a bottle (see `costs` in the config).
- **magicbottle.command.recipe**
	- Allows you to use `/mb recipe`.
	- Players have this permission by default.
- **magicbottle.command.repair**
	- Allows you to use `/mb repair`.
	- Players have this permission by default.
- **magicbottle.command.repair.auto**
	- Allows you to use `/mb autorepair`.
	- Players have this permission by default.
- **magicbottle.command.give**
	- Admin command. Gives you or another player a number of bottles of a certain level.
- **magicbottle.command.reload**
	- Admin command. Reloads the plugin config.
- **magicbottle.maxlevel.(name)**
	- This allows you to set different permissions for different maximum bottle levels. You must set the permissions in the plugin config.
- **magicbottle.maxlevel.unlimited**
	- A player that has this permission is allowed to save up to 20,000 levels in a MagicBottle.
