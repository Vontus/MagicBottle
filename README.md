![MagicBottle](assets/banner.png)

# MagicBottle

A Paper plugin to store your experience in bottles and take it back whenever you want. No GUIs, no commands to learn:
everything happens with the bottle in your hand and the vanilla workstations.

Download it from [Hangar](https://hangar.papermc.io/Vontus/MagicBottle). It requires Paper 26.2 or newer.

## Features

- **Deposit and withdraw XP** with the bottle in your hand: left click stores 1 level, right click takes 1 level back
  (sneak for 10 levels at a time).
- **All at once** in a crafting grid: put a bottle alone in the grid to empty it, or an empty one to store all your XP.
- **Pay anvil and enchanting costs** from a bottle when you lack the levels. The bottle lends you exactly what the
  workstation spends and gets the rest back.
- **Repair** tools and armor in the anvil with the XP of a bottle.
- **Bigger bottles**: merge two full bottles in the anvil to get one with the capacity of both.
- **Upgrades**, applied in the smithing table:
  - **Auto-repair** (Nether Star): tools and armor repair themselves with the XP of a bottle in your hotbar.
  - **Collect** (Heart of the Sea): the XP orbs you pick up go into the bottle.
  - **Soulbound** (Totem of Undying): you keep the bottle when you die.
- **In-game guide**: `/mb guide` explains everything in native dialogs, adapted to the server's config and the
  player's permissions.
- **Safe by design**: neither a new version nor a config change (a lower capacity, a disabled upgrade) makes a player
  lose stored XP.

## Install

1. Put the plugin jar in the `plugins` folder of a Paper server.
2. Start the server.

## Upgrading from 1.5.x

2.0.0 is a rewrite for Paper: it no longer runs on Spigot.

1. Back up `config.yml` and `messages.yml`, delete them and start the server, so the plugin writes new ones with every
   option documented. The 1.5.x files have options that no longer exist and `&` color codes, while messages are now
   MiniMessage.
2. Copy your values back: the deposit cost, the new bottle recipe (now a `shape` with item IDs or tags) and the repair
   enchantment. The old `max level.default` is now `capacity`, the levels a bottle holds: it defaults to 30 (it was
   200), and a bottle holding more than the capacity keeps all its XP but accepts no more until it goes below it.
3. Existing bottles are converted when their owner joins or uses them, keeping all their XP.

What was removed:

- The money cost of crafting a bottle (Vault) and `magicbottle.action.craft.cost.exempt`.
- The max level permissions (`magicbottle.maxlevel.*`): the capacity belongs to the bottle, and two full bottles merge
  into a bigger one.
- `/mb repair` and its automatic mode (`magicbottle.command.repair`, `magicbottle.command.repair.auto`): repair in the
  anvil, or with the auto-repair upgrade.
- The `/magicb` and `/mbottle` aliases.

## Configuration

The first time it runs, the plugin creates `config.yml` and `messages.yml` in its folder, with every option documented
in comments: bottle capacity, the new bottle recipe (any shape, item tags supported), deposit cost, repair, every
upgrade and its ingredient, and more. Options added in later versions are appended to your files with their default
value, keeping your values. `/mb reload` applies the changes without a restart.

Messages are written in [MiniMessage](https://docs.papermc.io/adventure/minimessage/format/) and come in English and
Spanish (`language`). Set `custom messages` to edit every one of them in `messages.yml`.

The defaults are defined in [Settings.java](src/main/java/vontus/magicbottle/config/Settings.java) and
[Messages.java](src/main/java/vontus/magicbottle/config/Messages.java).

## Commands

`/magicbottle` (alias `/mb`) lists the subcommands you can use.

| Command | Description |
|---|---|
| `/mb guide` | How to use a MagicBottle |
| `/mb recipe` | The new bottle recipe |
| `/mb about` | Plugin version and author |
| `/mb give <players> <level>[upgrades] [count]` | Give bottles, e.g. `/mb give @s 30[collect,soulbound] 2` (admin) |
| `/mb upgrade <add\|remove> <upgrades>` | Change the upgrades of the bottle in your hand (admin) |
| `/mb reload` | Reload the config (admin) |

## Permissions

| Permission | Default | Description |
|---|---|---|
| `magicbottle.action.craft` | everyone | Craft new bottles in a crafting grid |
| `magicbottle.action.deposit` | everyone | Store experience in bottles |
| `magicbottle.action.withdraw` | everyone | Take experience from bottles |
| `magicbottle.action.repair` | everyone | Repair tools and armor in the anvil with a bottle |
| `magicbottle.action.spend` | everyone | Pay anvil and enchanting table costs with a bottle |
| `magicbottle.action.merge` | everyone | Merge two full bottles in the anvil |
| `magicbottle.action.deposit.cost.exempt` | nobody | Store experience without paying the deposit cost |
| `magicbottle.upgrade.apply.<upgrade>` | everyone | Apply an upgrade in the smithing table (`autorepair`, `collect`, `soulbound`) |
| `magicbottle.upgrade.use.<upgrade>` | everyone | Get the effect of an upgrade |
| `magicbottle.command.guide` | everyone | `/mb guide` |
| `magicbottle.command.recipe` | everyone | `/mb recipe` |
| `magicbottle.command.give` | op | `/mb give` |
| `magicbottle.command.upgrade` | op | `/mb upgrade` |
| `magicbottle.command.reload` | op | `/mb reload` |

## Building

Requires a JDK 25.

```
./gradlew build
```

The plugin jar is written to `build/libs/`. See [CONTRIBUTING.md](CONTRIBUTING.md) to contribute.
