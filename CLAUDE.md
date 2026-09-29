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
`magicbottle` crafting recipes, registers `Events` as a listener, registers `Commands` as the command
executor, sets up Vault economy (optional), and starts bStats metrics.

**Core domain object**: `MagicBottle` wraps a Bukkit `ItemStack` and is the single source of truth for how
XP is represented on an item. Bottles are identified by a `magicbottle:bottle` PersistentDataContainer marker
and the XP amount is stored in `magicbottle:exp` (see `isMagicBottle`/`calculateExp`); the glint comes from
`setEnchantmentGlintOverride`. The lore only displays the XP. There is no support for bottles made by older
plugin versions (lore/enchantment based); nobody uses them anymore. The item's material
switches between `materialEmpty` (`GLASS_BOTTLE`) and `materialFilled` (`DRAGON_BREATH`) depending on whether
it holds XP. Any code creating/mutating a bottle must go through `MagicBottle` so the item's
PDC/lore/name/material stay in sync (`recreate()`/`print()`). The keys are created in `MagicBottle.init`, which
`onEnable` must call before anything else touches bottles.

**Interaction flow** (`Events.java`): all player-facing behavior is driven by Bukkit events, not GUIs:
- `onInteract` — left-click deposits, right-click withdraws, holding the bottle in hand (shift = 10 levels,
  no shift = 1 level).
- `onPrepareCraft`/`onCraft` — placing a bottle in a crafting grid alone withdraws/deposits *all* XP; the
  three crafting recipes (fill/pour/craft new bottle) are conditionally offered based on `Config` toggles.
- `onCrafterCraft` — blocks every bottle recipe in crafter blocks, which bypass the player crafting checks.
- `onItemUse` — auto-repair of tools/armor using a usable bottle anywhere in the inventory if the player has
  enabled auto-repair (tracked in `Plugin.autoEnabled`) and `Config.canRepair` accepts the item (it has the
  configured `repair.enchantment`, Mending by default, or any item if set to `ANY`; parsed by `EnchantParser`
  from an enchantment registry key).

**Recipes** (`Recipes.java`): registers up to three recipes on enable, each gated by a `Config` flag —
shapeless "fill" (empty bottle → filled), shapeless "pour" (filled → empty + XP), and a shaped "new bottle"
recipe whose 3x3 ingredient layout comes entirely from config (`Config.getBottleRecipeIngredient`).

**Commands** (`Commands.java`): single `/magicbottle` command (aliases `mb`, `magicb`, `mbottle`) dispatched
by subcommand string (`about`, `reload`, `give`, `repair [auto]`), each gated by its own permission in
`Config`.

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

- Economy costs (crafting a new bottle) are optional and require Vault; if Vault isn't present,
  `Plugin.loadConfig` forces the cost to 0 and logs a warning instead of failing.
- There is a single supported line: current Paper. The old per-Minecraft-version branches were removed; their
  tips are kept as `archive/*` tags.
