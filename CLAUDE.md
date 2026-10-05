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
`magicbottle` crafting recipes, registers the listeners, registers the `Commands` tree
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

**Interaction flow**: all player-facing behavior is driven by Bukkit events, not GUIs. The listeners are split by
concern, in the `listeners` package: `BottleInteractListener` (clicks in hand, throttled by the `ClickCooldown` that `PlayerListener` clears on
quit/kick), `CraftingGridListener` (deposit/withdraw, new bottle recipe, crafters), `InventoryListener` (recipe menu and
anvil/brewing block), `AnvilListener` (repair in the anvil), `RepairListener` (auto-repair) and `PlayerListener` (join migration, leave cleanup):
- `onInteract` — left-click deposits, right-click withdraws, holding the bottle in hand (shift = 10 levels,
  no shift = 1 level). Accepted clicks start a 3 tick per-player cooldown (`throttle`, a map of last click ticks).
- `onPrepareCraft`/`onClickCraftResult` — a single MagicBottle (amount 1) alone in a crafting grid (3x3 or the
  2x2 inventory grid) withdraws/deposits *all* XP. These aren't registered recipes (the recipe book would autofill
  any glass bottle or dragon's breath), so `onPrepareCraft` sets the preview itself (PrepareItemCraftEvent fires
  even when no recipe matches) and `onClickCraftResult` always cancels clicks on that result slot (vanilla would
  duplicate the bottle, since no recipe consumes it) and does the transaction by hand: it picks the destination
  first (empty cursor, free inventory slot on shift-click, empty hotbar slot on number key, empty offhand on F; Q/Ctrl+Q does nothing, since `dropItem` doesn't fire `PlayerDropItemEvent`; creative middle click is left to vanilla), re-checks
  `recipe.deposit`/`recipe.withdraw` (`Config.recipeFill`/`recipePour`, reloadable) and permissions, and only
  then moves the XP, so a click that can't deliver the bottle moves nothing.
- `onPrepareCraft`/`onCraft` — the new bottle recipe is crafted by vanilla. `onPrepareCraft` removes its result if
  the player lacks `magicbottle.action.craft` or `isEmptyBottleRecipe` fails (identified by its `NamespacedKey`,
  `Recipes.getKey`, and no grid item may be a MagicBottle); `onCraft` only plays the sound (and cancels as a
  safety net).
- `onCrafterCraft` — crafter blocks have no player, so they bypass the player crafting checks. The new bottle
  recipe (identified by its key) is allowed only if `recipe.bottle.allow crafters`
  (`Config.recipeNewBottleAllowCrafters`, reloadable) is on and no slot of the crafter holds a MagicBottle
  (otherwise it would be consumed as an ingredient). Crafters can't deposit or withdraw.
- `onClickInventory`/`onDragInventory`/`onMoveItem` — bottles can't enter brewing stands (dragon's breath
  is a brewing ingredient) or the first slot of anvils. Clicks are cancelled when the clicked item is a bottle in a top
  slot or is shift-clicked from the player's inventory (plain clicks there are fine) or, on a top slot, when the cursor,
  the number-key hotbar item or the offhand item (F) is one; drags over the top inventory and hopper moves into
  those inventories are cancelled too. The exception is the anvil's second slot (`AnvilListener.acceptsBottle`, only if
  `repair.enabled`), where a bottle is accepted; vanilla's shift-click would put it in the first slot, so
  `AnvilListener.onShiftClickBottle` moves it by hand.
- `AnvilListener` — repair in the anvil: a damaged item accepted by `Config.canRepair` in the first slot and a usable
  MagicBottle in the second (`repair.enabled`, `magicbottle.action.repair`). `onPrepareAnvil` sets the repaired item as
  the result with repair cost 0 (no levels) and a lore line with the exp it will spend and what the bottle keeps (`messages.repair.anvil cost`; only the preview has it); `onClickResult` cancels the click and takes it by hand like
  `onClickCraftResult` does (`ResultSlot.destination`), because vanilla would consume the bottle. 1 exp repairs 2
  durability points; a bottle with less exp repairs partially and stays in the slot, empty.
- `onItemDamage` — auto-repair of tools/armor using a usable bottle anywhere in the inventory if the player has
  enabled auto-repair (tracked in `Plugin.autoEnabled`) and `Config.canRepair` accepts the item (it has the
  configured `repair.enchantment`, Mending by default, or any item if set to `ANY`; parsed by `EnchantParser`
  from an enchantment registry key). It checks the cheap conditions first, has no cooldown (it must not interfere
  with clicks) and only repairs when the item's damage is odd, since 1 exp repairs 2 durability points. The exp it spends is reported to `AutoRepairFeedback`, which debounces an action bar message (`messages.repair.auto spent`, disabled by `repair.auto feedback`, reloadable): it is sent once the player has gone 3 seconds without auto-repairing, with the total spent meanwhile.

**Recipes** (`Recipes.java`): registers a single recipe on enable, if `recipe.bottle.enabled` is on: the shaped
"new bottle" recipe (`magicbottle:bottle`, result `MagicBottle(0)`) whose datapack-style `shape`/`ingredients`
(item IDs or `#` item tags) come from config. `Config.loadNewBottleRecipe` validates them into
`recipeNewBottleShape`/`recipeNewBottleIngredients`; if they're invalid it logs the problem and disables the
recipe. The default recipe uses `dragon_breath` on purpose: filled bottles are dragon's breath, which grants the "You
Need a Mint" advancement, so only players who already have it can craft one (the `shape` comment in `Settings` warns admins about it).
Like any shaped recipe it matches anywhere in the grid (and mirrored), so `CraftingGridListener` identifies it by its
key (`Recipes.getKey`), never by grid positions, and refuses it when a MagicBottle is in the grid. Depositing and
withdrawing are not recipes (see `CraftingGridListener`).

**Commands** (`Commands.java`): the single `/magicbottle` command (aliases `mb`, `magicb`, `mbottle`) is a Brigadier
tree (`Commands#build`) registered from `Plugin.onEnable` through `LifecycleEvents.COMMANDS`; it is not in
`plugin.yml`. Subcommands (`about`, `reload`, `give <level> [amount] [player]`, `recipe`, `autorepair [on|off]`) are
literal nodes gated with `.requires(...)` on their permission in `Config` (`recipe` and `autorepair` also require a
player executor), so senders only see and can run what they may; the menu shown by the bare command lists the
nodes the source can use.
`give` takes bounded arguments (`level` 0..`Config.maxLevel`, `amount` 1..64, `player` as Paper's player selector,
which fails if nobody matches); without `player` it targets the executor, so `/execute as` works. New subcommands
are added as nodes in `build`, with their line in `USAGES`.

**Recipe menu** (`RecipeMenu.java`): `/mb recipe` (`magicbottle.action.craft`, the same permission as crafting it)
opens a chest inventory showing the configured new-bottle recipe (any shape up to 3x3; ingredients that accept
several items, i.e. tags, cycle through them every second). It must stay strictly read-only: the inventory has a custom `InventoryHolder`
(`RecipeMenu`), `InventoryListener` cancels every `InventoryClickEvent`/`InventoryDragEvent` while it is the *top* inventory of
the view (which also covers shift-clicks, number keys, offhand swaps and double clicks from the player's own
inventory) and clears it on `InventoryCloseEvent`; `Plugin.onDisable` closes it for whoever has it open. The result
is `MagicBottle.createDisplayItem()`, a bottle without the PDC markers, so it is never a real MagicBottle.

**Config layer** (`config/`): the files are mapped with [Configurate](https://github.com/SpongePowered/Configurate)
(`configurate-yaml`, loaded by the server through `libraries:` in `plugin.yml`, not shaded). `Settings` (config.yml)
and `Messages.Texts` (messages.yml) are `@ConfigSerializable` classes whose field initializers are the defaults and
whose `@Comment`s document the file; `@Setting` keeps the original option names. `ConfigFile.load` loads the file into
that object, writes it back (so options missing from the file are added without touching the admin's values) and
inserts the comments, since the YAML loader keeps them in the nodes but doesn't write them. A type error is logged with
its path and the defaults are used. Values that need the server registries are parsed by custom serializers
(`Ingredient`, `EnchantParser`) that don't throw: they keep the error, so only the recipe or repairing is disabled
(`Config.load`) instead of the whole file. `Config.load` and `Messages.load` (see `Plugin.loadConfig`, also invoked by
`/magicbottle reload`) leave the result in `Config.settings`/`Messages.texts`; game logic reads those, plus the validated
`Config.recipeNewBottle*`/`repairEnabled`/`repairAutoEnabled`. A new option is a field in `Settings`/`Messages`.
Permission node strings are also defined as constants on `Config`.

**Messages**: `messages.yml` is written in MiniMessage (no `&` codes, no `ChatColor`), and everything player-facing is
sent as Adventure components. The texts stay raw strings in `Messages.texts` and are rendered when used with
`Messages.render(msg, TagResolver...)`, passing
`Placeholder.unparsed` for values (player names, numbers) and `Placeholder.component` for components (the bottle's
`<xpbar>`, built from the `filled bar`/`empty bar` components), never `Placeholder.parsed` or `String.replace`.
Placeholder names are written as literals where they're resolved. Bottle name and lore use
`Messages.renderItemText`, which turns italics off unless the message asks for them (vanilla shows custom item text
italic).

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

## Git workflow

- `master` is always releasable. Nothing is committed to it directly: every change goes through a PR.
- **Branch names**: `<type>/<issue>-<short-description>`, lowercase kebab-case, a few words; the issue number
  only when there is one. Types: `feat` (new behavior), `fix` (bug), `refactor`, `perf`, `docs`, `build`
  (Gradle, dependencies), `chore` (repo housekeeping), `release`. Examples: `fix/61-crafter-dupe`,
  `feat/recipe-command`, `build/bstats-3.2.1`. Worktrees (including subagent ones) use a branch that follows this
  convention, never an auto-generated name like `worktree-agent-*`.
- **One change per branch/PR**, even when several changes are developed together, so each PR can be reviewed on
  its own; follow-ups get their own PR instead of being folded into an open one. A change that depends on an
  unmerged one is stacked: its branch starts from the parent branch and its PR targets it. Once the parent is
  merged, rebase it onto master (`git rebase --onto origin/master <parent>`) and retarget the PR to `master`.
- **Keeping up to date**: rebase onto `origin/master` and push with `--force-with-lease`; never merge master into
  a branch. Force pushes are fine on feature branches, never on `master`. The Claude Code hook
  `.claude/hooks/block-merge-into-branch.sh` blocks `git merge` (except `--ff-only`/`--abort`/`--continue`) and
  merging `git pull`s.
- **Commits**: English, imperative, capitalized subject without a trailing period, ~72 chars max; the body says
  why when it isn't obvious. Each commit is one logical, compiling step. The PR description links its issue
  (`Fixes #61`).
- **Merging**: PRs are merged on GitHub with a merge commit (the branch's commits stay grouped under the PR).
  GitHub deletes the remote branch on merge; delete the local one (`git branch -d`, `git fetch --prune`) and its
  worktree.
- **Releases**: a `release/X.Y.Z` PR bumps the version in `build.gradle`; after merging it, master is tagged with an
  annotated `X.Y.Z` tag (semver). The GitHub release carries a changelog summarizing the user-facing changes of
  every PR merged since the previous tag.
- **Local setup** (git config isn't versioned, run it once per clone):
  ```
  git config pull.rebase true        # git pull rebases instead of merging
  git config rebase.updateRefs true  # rebasing a branch also moves the stacked branches on top of it
  git config rebase.autoSquash true  # fixup! commits are squashed when rebasing
  ```
