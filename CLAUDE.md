# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

MagicBottle is a Paper Minecraft server plugin (targeting 26.2, `api-version: '26.2'`) that lets players store
their XP levels in glass bottles. Java 25, built with Gradle 9. Package root: `vontus.magicbottle`.

## Build

```
./gradlew build       # compile + produce the shaded plugin jar (relocates bstats): build/libs/MagicBottle-<version>.jar
```

Requires a JDK 25 (Gradle toolchain). On Windows use `gradlew.bat` instead of `./gradlew`. There is no test
suite, so there is no single-test command to run.

The plugin version lives only in `build.gradle`; `plugin.yml` gets it through `processResources` expansion.

## Architecture

**Entry point**: `Plugin.java` (`onEnable`) wires everything together: loads config/messages, registers the
`magicbottle` crafting recipes, registers `Events` as a listener, registers the `Commands` tree
through `LifecycleEvents.COMMANDS`, and starts bStats metrics.

**Core domain object**: `MagicBottle` wraps a Bukkit `ItemStack` and is the single source of truth for how
XP is represented on an item. Bottles are identified by a `magicbottle:bottle` PersistentDataContainer marker
and the XP amount is stored in `magicbottle:exp` (see `isMagicBottle`/`calculateExp`); the glint comes from
`setEnchantmentGlintOverride`. The lore only displays the XP. Everything about the 1.5.x format (hidden
Efficiency enchantment, XP parsed from lore line 1) lives in `LegacyBottle`: those bottles are recognized by
`isMagicBottle` and rewritten in the current format when a `MagicBottle` is built from them or when their owner
joins (inventory and ender chest), so no other code has to know about it. The item's material
switches between `materialEmpty` (`GLASS_BOTTLE`) and `materialFilled` (`DRAGON_BREATH`) depending on whether
it holds XP. Any code creating/mutating a bottle must go through `MagicBottle` so the item's
PDC/lore/name/material stay in sync (`recreate()`/`print()`). The keys are created in `MagicBottle.init`, which
`onEnable` must call before anything else touches bottles.

**Interaction flow** (`Events.java`): all player-facing behavior is driven by Bukkit events, not GUIs:
- `onInteract` — left-click deposits, right-click withdraws, holding the bottle in hand (shift = 10 levels,
  no shift = 1 level). Accepted clicks start a 3 tick per-player cooldown (`throttle`, a map of last click ticks).
- `onPrepareCraft`/`onClickCraftResult` — a single MagicBottle (amount 1) alone in a crafting grid (3x3 or the
  2x2 inventory grid) withdraws/deposits *all* XP. These aren't registered recipes (the recipe book would autofill
  any glass bottle or dragon's breath), so `onPrepareCraft` sets the preview itself (PrepareItemCraftEvent fires
  even when no recipe matches) and `onClickCraftResult` always cancels clicks on that result slot (vanilla would
  duplicate the bottle, since no recipe consumes it) and does the transaction by hand: it picks the destination
  first (empty cursor, free inventory slot on shift-click, empty hotbar slot on number key, dropped on Q/Ctrl+Q, empty offhand on F; creative middle click is left to vanilla), re-checks
  `recipe.deposit`/`recipe.withdraw` (`Config.recipeFill`/`recipePour`, reloadable) and permissions, and only
  then moves the XP, so a click that can't deliver the bottle moves nothing.
- `onPrepareCraft`/`onCraft` — the new bottle recipe is crafted by vanilla. `onPrepareCraft` removes its result if
  the player lacks `magicbottle.action.craft` or `isEmptyBottleRecipe` fails (identified by its `NamespacedKey`,
  `Recipes.getKey`, and no grid item may be a MagicBottle); `onCraft` only plays the sound (and cancels as a
  safety net).
- `onCrafterCraft` — crafter blocks have no player, so they bypass the player crafting checks. The new bottle
  recipe (identified by its key) is allowed only if `recipe.bottle.allow crafters`
  (`Config.recipeNewBottleAllowCrafters`, reloadable) is on and no slot of the crafter holds a MagicBottle
  (otherwise it would be consumed as an ingredient). Crafters can't fill or pour.
- `onItemDamage` — auto-repair of tools/armor using a usable bottle anywhere in the inventory if the player has
  enabled auto-repair (tracked in `Plugin.autoEnabled`) and `Config.canRepair` accepts the item (it has the
  configured `repair.enchantment`, Mending by default, or any item if set to `ANY`; parsed by `EnchantParser`
  from an enchantment registry key). It checks the cheap conditions first, has no cooldown (it must not interfere
  with clicks) and only repairs when the item's damage is odd, since 1 exp repairs 2 durability points.

**Recipes** (`Recipes.java`): registers a single recipe on enable, if `recipe.bottle.enabled` is on: the shaped
"new bottle" recipe (`magicbottle:bottle`, result `MagicBottle(0)`) whose datapack-style `shape`/`ingredients`
(item IDs or `#` item tags) come from config. `Config.loadNewBottleRecipe` parses and validates them into
`recipeNewBottleShape`/`recipeNewBottleIngredients`; if they're invalid it logs the problem and disables the
recipe. Like any shaped recipe it matches anywhere in the grid (and mirrored), so `Events` identifies it by its
key (`Recipes.getKey`), never by grid positions, and refuses it when a MagicBottle is in the grid. Filling and
pouring are not recipes (see `Events`).

**Commands** (`Commands.java`): the single `/magicbottle` command (aliases `mb`, `magicb`, `mbottle`) is a Brigadier
tree (`Commands#build`) registered from `Plugin.onEnable` through `LifecycleEvents.COMMANDS`; it is not in
`plugin.yml`. Subcommands (`about`, `reload`, `give <level> [amount] [player]`, `recipe`, `repair [auto]`) are
literal nodes gated with `.requires(...)` on their permission in `Config` (`recipe` and `repair` also require a
player executor), so senders only see and can run what they may; the menu shown by the bare command lists the
nodes the source can use.
`give` takes bounded arguments (`level` 0..`Config.maxLevel`, `amount` 1..64, `player` as Paper's player selector,
which fails if nobody matches); without `player` it targets the executor, so `/execute as` works. New subcommands
are added as nodes in `build`, with their line in `USAGES`.

**Recipe menu** (`RecipeMenu.java`): `/mb recipe` (`magicbottle.action.craft`, the same permission as crafting it)
opens a chest inventory showing the configured new-bottle recipe (any shape up to 3x3; ingredients that accept
several items, i.e. tags, cycle through them every second). It must stay strictly read-only: the inventory has a custom `InventoryHolder`
(`RecipeMenu`), `Events` cancels every `InventoryClickEvent`/`InventoryDragEvent` while it is the *top* inventory of
the view (which also covers shift-clicks, number keys, offhand swaps and double clicks from the player's own
inventory) and clears it on `InventoryCloseEvent`; `Plugin.onDisable` closes it for whoever has it open. The result
is `MagicBottle.createDisplayItem()`, a bottle without the PDC markers, so it is never a real MagicBottle.

**Config layer** (`config/`): `PluginFile` is a generic wrapper around a Bukkit `YamlConfiguration` file
(load/save/defaults-from-jar). `Config` and `Messages` are static classes populated once from
`config.yml`/`messages.yml` at load time (see `Plugin.loadConfig`, also invoked by `/magicbottle reload`) —
all game logic reads these static fields rather than touching the config files directly. Permission node
strings are also defined as constants on `Config`.

**XP math** (`util/Exp.java`): ported from EssentialsX. Bukkit's built-in level/exp handling only tracks the
in-progress level, so this class recomputes true total XP points and implements the get/set/give/take API
plus the level↔exp conversion formulas (`getLevelFromExp`, `getExpAtLevel`) that vanilla Minecraft uses.

**Effects** (`effects/SoundEffect`): sounds and particles (Bukkit `spawnParticle`) for deposit/withdraw/craft
actions.

## Notes

- Crafting a new bottle has no money cost (Vault support was removed). In a crafting grid it requires the
  `magicbottle.action.craft` permission; crafters can't check it, hence the `allow crafters` option.
- There is a single supported line: current Paper. The old per-Minecraft-version branches were removed; their
  tips are kept as `archive/*` tags.
