# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.
It describes the architecture; the rules for writing, testing, reviewing and merging code are in CONTRIBUTING.md:

@CONTRIBUTING.md

## Project overview

MagicBottle is a Paper Minecraft server plugin (targeting 26.2, `api-version: '26.2'`) that lets players store
their XP levels in glass bottles. Java 25, built with Gradle 9. Package root: `vontus.magicbottle`.

## Build

```
./gradlew build       # compile + produce the shaded plugin jar (relocates bstats): build/libs/MagicBottle-<version>.jar
./gradlew spotlessApply  # format the code: build runs spotlessCheck and fails if it is not formatted
```

Requires a JDK 25 (Gradle toolchain). On Windows use `gradlew.bat` instead of `./gradlew`. The compiler runs with
`-Xlint:all -Werror` plus Error Prone and NullAway (scoped to `vontus.magicbottle`): a warning or a nullness error fails the build, so fix it or suppress it narrowly with a comment saying why. There is no test
suite, so there is no single-test command to run (see Testing in CONTRIBUTING.md).

The plugin version lives only in `build.gradle`; `plugin.yml` gets it through `processResources` expansion.

## Architecture

**Entry point**: `Plugin.java` (`onEnable`) wires everything together: loads config/messages, registers the
`magicbottle` crafting recipes, registers the listeners, registers the `Commands` tree
through `LifecycleEvents.COMMANDS`, and starts bStats metrics.

**Core domain object**: `MagicBottle` wraps a Bukkit `ItemStack` and is the single source of truth for how
XP is represented on an item. Bottles are identified by a `magicbottle:bottle` PersistentDataContainer marker
and the XP amount is stored in `magicbottle:exp` (see `isMagicBottle`/`calculateExp`); the glint comes from
`setEnchantmentGlintOverride`. The lore displays the XP and the upgrades (below). Every bottle is `DRAGON_BREATH` (`material`), which
is nearly inert (in no recipe or item tag; brewing is blocked), unlike a glass bottle, which vanilla and dispensers
fill with water or honey; an empty bottle only looks like a glass bottle through the `item_model` component.
Everything about the 1.5.x format (hidden Efficiency enchantment, XP parsed from lore line 1, empty bottles as
`GLASS_BOTTLE`) lives in `LegacyBottle`: those bottles are recognized by `isMagicBottle` and rewritten in the current
format when a `MagicBottle` is built from them or by `MagicBottle.refresh`, so no other code has to know about it.
`refresh(Inventory)` brings the bottles of an inventory up to date (legacy format, stack size and any change in how `print()`
writes a bottle, from a new version or from the config: capacity, language, messages, disabled upgrades). It runs for the
inventory and ender chest of a player on join and of every online player on `/mb reload`; bottles in containers and offline
players are only reached when their owner joins or the bottle is used. There is no format version: a bottle is outdated if
printing it again would change the item (compared on a copy), so an up to date one is not touched. A new step in the format
needs no special case, `print()` is the step. Any code creating/mutating a bottle must go through `MagicBottle` so the item's
PDC/lore/name/material stay in sync (`recreate()`/`print()`). The keys are created in `MagicBottle.init`, which
`onEnable` must call before anything else touches bottles.

**Size and capacity** (`MagicBottle`): the capacity belongs to the bottle. `magicbottle:size` (INTEGER, missing = 1, written only when
above 1, never renamed) says how many base bottles it is worth; `getSize()` clamps it to `getMaxSize()` (what fits under the hard cap
`Config.maxLevel`). `getCapacity()`/`getCapacityLevel()` are calculated every time, never stored, with no player or permission (so a
placed lantern can use them): the points of `size` bases (`capacity` in `Settings`, in levels, default 30) rounded down to a whole level
with `Exp.getLevelFloor`, in `long`. A bottle is `isFull()` when `exp >= capacity`; a bottle above its capacity (lowered config) keeps
all its exp, only refuses deposits (`getRoom()` is never negative) and counts as full. Name (`name`/`name full`), the capacity lore line
(only if size > 1, a title `capacity text` and a line `capacity`, like the experience) and `<xpbar>` (fill = exp/capacity, rounded down, complete only when full) come from it; placeholders `<level>`
(whole level held), `<max_level>`, `<size>`, `<points>`, `<max_points>` (the capacity in points, shown by `experience` as `points/max`). `MagicBottle.merge` joins two full bottles (sizes added, union of upgrade ids) and consumes only the capacity of each (`getMergeCost`): what a bottle holds above its capacity (older bottles, a lowered `capacity`, `/mb give`) passes to the new one, which keeps it above its own capacity if need be; no result if it would not fit in a bottle (never lose exp). Bottles have
`max_stack_size` 1, set by `print()` on single bottles (a stack is left alone: it may fail validation above the component) and by
`refresh` for the inventories of a joining player. The old per-player limits (`max level`, `magicbottle.maxlevel.*`) don't exist.

**Upgrades** (`Upgrade`): flags a bottle carries in `magicbottle:upgrades`, a PDC string list of lowercase ids (`autorepair`, `collect`, `soulbound`;
the id is persisted, so never change it) treated as a set: a missing key means none, and ids this version doesn't know are
kept when the bottle is rewritten. Only `MagicBottle` reads and writes it (`hasUpgrade`/`addUpgrade`), and `print()` adds a
lore line per known upgrade (`bottle text.upgrades`; ids this version doesn't know have none): the enabled ones first, then the
disabled ones (`Config.isUpgradeEnabled`), each group in enum order. A disabled upgrade stays on the bottle (PDC untouched, it
works again if re-enabled) and its line is wrapped in `bottle text.upgrade disabled` (struck through by default), so the lore is a
function of PDC + config; it only changes when the bottle is printed again. The `upgrades title` is shown if the bottle has any
known upgrade, even if all are disabled. Features that need a bottle with an upgrade call
`MagicBottle.findWithUpgrade`, which only looks in the hotbar and the offhand (moving the bottle out is the off switch).
Both go through `MagicBottle.find`, which decides which bottle a feature uses: the selected slot, then the offhand, then the
rest of the hotbar and, if asked (`wholeInventory`), the rest of the inventory. Upgrades never ask for the whole inventory;
loans (`XpLoan`) do.
Each upgrade has `upgrades.<id>` in `Settings` (`enabled`, `ingredient`, parsed by `Ingredient`; validated in
`Config.loadUpgrades` into `Config.upgradeIngredients`; a wrong ingredient of an enabled upgrade sets `Config.invalidUpgrade` and `onEnable` disables the plugin, so the admin notices) and the permission `magicbottle.upgrade.<id>` to apply it. They are
applied in the smithing table (see `SmithingListener`). A new upgrade is an `Upgrade` value plus its settings, lore message
and permission (the switches over `Upgrade` in `Config` and `Messages` don't compile until they are added).

**Loans** (`XpLoan`): a workstation that only lets the player use what their real level affords is paid from a bottle by
lending the player the points. `borrow` raises the player to exactly a target point total (the level the workstation needs,
bar 0) with the difference taken from the first bottle of the whole inventory that covers it (`MagicBottle.lend`; bottles
aren't combined), and the workstation spends the levels as vanilla does. The loan (`owed`) is stored in the player's PDC
(`magicbottle:borrowed`) so a crash is recovered on join. `settle` gives the bottle `min(owed, the player's points now)`
(`MagicBottle.repay`, no deposit cost: the points were the bottle's; to the bottle that lent them if it is still in its slot
with room, else to the first bottle with room): own exp is spent first, orbs or other plugins can't create exp, and if no
bottle has room the player keeps the points (never lost). Both ways the exp is set with `Exp.setPointsSilently`, since
`Exp.setPoints` gives it level by level and vanilla plays the level up sound on every multiple of 5. What the bottle really
paid is passed to the function given to `borrow`. `LoanListener` settles on `InventoryCloseEvent` (any
inventory), join, quit, kick, a lethal `EntityDamageEvent` (before the death computes level and dropped exp) and
`PlayerDeathEvent` (lowering `droppedExp` if a loan was settled); `Plugin.onDisable` settles too. Settling ignores the config
and permissions: a loan is always repaid.

**Interaction flow**: all player-facing behavior is driven by Bukkit events, not GUIs. The listeners are split by
concern, in the `listeners` package: `BottleInteractListener` (clicks in hand, throttled by the `ClickCooldown` that `PlayerListener` clears on
quit/kick), `CraftingGridListener` (deposit/withdraw, new bottle recipe, crafters), `InventoryListener` (brewing block), `Guide`/`RecipeMenu`/`RecipeMenuListener` (the `guide` package, see below),
`AnvilListener` (repair and merge in the anvil), `RepairListener` (auto-repair), `SmithingListener` (upgrades), `LoanListener` (returning loans, see above) and `PlayerListener` (join migration, leave cleanup):
- `onInteract` — left-click deposits, right-click withdraws, holding the bottle in hand (shift = 10 levels,
  no shift = 1 level). Accepted clicks start a 3 tick per-player cooldown (`throttle`, a map of last click ticks).
- `onPrepareCraft`/`onClickCraftResult` — a single MagicBottle (amount 1) alone in a crafting grid (3x3 or the
  2x2 inventory grid) withdraws/deposits *all* XP. These aren't registered recipes (the recipe book would autofill
  any dragon's breath), so `onPrepareCraft` sets the preview itself (PrepareItemCraftEvent fires
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
  is a brewing ingredient; anvils take them in both slots, see `AnvilListener`). Clicks are cancelled when the clicked item is a bottle in a top
  slot or is shift-clicked from the player's inventory (plain clicks there are fine) or, on a top slot, when the cursor,
  the number-key hotbar item or the offhand item (F) is one; drags over the top inventory and hopper moves into
  those inventories are cancelled too.
- `AnvilListener` — what a bottle does in the anvil (vanilla would consume the bottles when the result is taken, so `onClickResult`
  cancels the click and takes it by hand like `onClickCraftResult` does, `ResultSlot.destination`, picking the destination first).
  Damaged item accepted by `Config.canRepair` + usable MagicBottle in the second slot: repair (`repair.enabled`,
  `magicbottle.action.repair`). `onPrepareAnvil` sets the repaired item as the result with repair cost 0 (no levels) and a lore line
  with the exp it will spend and what the bottle keeps (`messages.repair.anvil cost`; only the preview has it). 1 exp repairs 2
  durability points; a bottle with less exp repairs partially and stays in the slot, empty.
  MagicBottle + MagicBottle (single bottles, both `isFull`): merge (`merge.enabled`, `magicbottle.action.merge`), the result
  is `MagicBottle.merge` (sizes added, union of the upgrades, the exp above the capacities of both; none if over the hard cap, so no exp is wasted),
  with repair cost 0 and a preview line saying the exp consumed, the capacity of both (`messages.merge.anvil`). Both inputs are consumed
  and the sound is `SoundEffect.merge`. Vanilla's take never runs, so the anvil is not worn by a merge (nor by a repair). A MagicBottle in the first slot with anything else in the second has no result at all
  (vanilla would rename it, and `print()` rewrites the name).
  `lendCost` pays the level cost of any other anvil result (`spend.enabled`, `magicbottle.action.spend`) when the
  player lacks the levels, with a loan (`XpLoan`): the client and the server only let the player take a result their real
  level affords, so the bottle raises the player to exactly the cost's level with an empty bar. The cost shows affordable
  and vanilla's own take (every click type, the items, the anvil's wear) charges it: it spends the levels and keeps the bar,
  so it takes `Exp.getExpAtLevel(cost)` points, not the levels above the player's own (they depend on the player's level).
  Own exp goes first, then the first bottle of the whole inventory that covers the rest (bottles aren't combined; if none
  covers it, the anvil is vanilla). It runs on `PrepareAnvilEvent` (MONITOR, the final cost) and next tick after a click or
  drag in the anvil by a player with a loan, in case no prepare follows: unless the loan for the current cost is untouched,
  it settles the loan for what the anvil showed before (after a take, the bottle's share goes to the action bar right away,
  `messages.spend.from bottle`, not through `DebouncedFeedback`: an anvil operation isn't repeated like the upgrades) and
  lends what the current result needs. Cost 0, "Too Expensive!", creative and players
  who can afford it are vanilla. A fake client level (`Player#sendExperienceChange`) doesn't work: the server overwrites
  it on the first click.
- `EnchantListener` — pays the enchanting table from a bottle with a loan (`XpLoan`; `spend.enabled`, `magicbottle.action.spend`,
  same switch as the anvil). The client and the server refuse an unaffordable option before any event, so the real level must
  exist when the offers are computed: `onPrepare` (every time the item/lapis change, HIGHEST) takes the highest offer the player
  can't afford (required level = `max(cost, button + 1)`, vanilla's check; at most 30) and borrows exactly
  `Exp.getExpAtLevel(required)` points, otherwise a lower offer, or nothing (vanilla). Unless the loan for that level is
  untouched, it settles the earlier one first (which also returns the points of an enchantment just done). The loan is for
  that offer, but the player picks any: `onEnchant` (MONITOR, before vanilla spends the levels) lowers it with `XpLoan.lower` to
  `Exp.getExpAtLevel(max(cost, button + 1))` of the offer picked, so the bottle pays what a player of that level would. Vanilla then
  spends `button + 1` levels (1 to 3, not the cost) keeping the bar, which is 0 here. Closing, join, quit, death... are `LoanListener`'s;
  `EnchantListener` adds the next tick check after an `EnchantItemEvent` or a click/drag by a player with a loan, which settles
  if the table shows no offers any more (item taken out or enchanted: Paper fires no prepare then). The bottle's share goes to the
  action bar right away (`messages.spend.enchant`), like the anvil.
- `SmithingListener` — applies upgrades: each enabled upgrade has a `SmithingTransformRecipe` (`magicbottle:upgrade/<id>`,
  `Recipes.getUpgrade`; no template, any dragon's breath as base, the configured ingredient as addition) so the slots
  accept the items. `onPrepareSmithing` rebuilds the result from the bottle in the base slot (keeping its exp and upgrades,
  adding the new one) and clears it if the base isn't a MagicBottle, already has the upgrade, the upgrade is disabled or the
  player lacks `magicbottle.upgrade.<id>`. Crafters can't apply upgrades.
- `CollectListener` — the `collect` upgrade: `onExpChange` (`PlayerExpChangeEvent`, once per orb unit, after Mending, so it only
  takes what Mending leaves) ignores events whose source isn't an `ExperienceOrb` and, if `upgrades.collect` is on, stores
  the amount in the first bottle with `collect` and room in the hotbar/offhand (`MagicBottle.findWithUpgrade`, then the next
  one if it fills up), setting the event's amount to what is left for the player. Cancelled pickups never reach it and it
  never cancels them. `MagicBottle.collect` charges the deposit cost with stochastic rounding (integer math: mean exactly
  `points * percentage / 100`, no random when it is exact or exempt), only on the points the bottle takes, never in
  `getDepositGain` (the crafting preview, which must stay deterministic). Every deposit (`deposit`, `collect`) is limited
  by what the bottle *gains*, not by what the player pays: the gain is capped at the room left and the player pays the
  fewest points that give it, so a bottle fills exactly; a deposit whose cost leaves nothing to store is refused (always
  with 100%). The exp stored is reported to its channel of
  `DebouncedFeedback` (`messages.collect.stored`, `upgrades.collect.feedback`).
- `SoulboundListener` — the `soulbound` upgrade: on `PlayerDeathEvent` (HIGH, after `LoanListener` has settled any loan) each bottle with
  the upgrade in the player's inventory (main, hotbar, offhand and armor: protecting a bottle doesn't depend on where it is) that is in
  `getDrops()` moves to `getItemsToKeep()`; one that isn't in the drops (`keepInventory`, another plugin) is left alone, or it would be duplicated.
  Permanent, and off with `upgrades.soulbound.enabled` (reloadable). Cursor and crafting grid items aren't covered.
- `onItemDamage` — auto-repair of tools/armor using the first non-empty bottle with the `autorepair` upgrade in the
  hotbar or offhand (`MagicBottle.findWithUpgrade`) if `Config.canRepair` accepts the item (it has the
  configured `repair.enchantment`, Mending by default, or any item if set to `ANY`; parsed by `EnchantParser`
  from an enchantment registry key). It checks the cheap conditions first, has no cooldown (it must not interfere
  with clicks) and only repairs when the item's damage is odd, since 1 exp repairs 2 durability points. The exp it spends is reported to its channel of `DebouncedFeedback` (`messages.repair.auto spent`, disabled by `repair.auto feedback`, reloadable). That class debounces one action bar message shared by the channels (auto-repair and collect happen together when mining, and the action bar shows one message): it is sent once the player has gone 3 seconds without any, with the channels that have something to report joined by `messages.feedback separator`.

**Recipes** (`Recipes.java`, `Plugin.recipes`): `register` adds the recipes from `Config` on enable and again on `/mb reload`,
removing the ones it added before (it keeps their keys) and resending the recipes to the online players once at the end, so
every recipe option (and `RecipeMenu`, which reads `Config`) agrees with what can be crafted without a restart. An invalid
upgrade ingredient on reload only leaves that upgrade without a recipe (the sender is told); on enable it disables the plugin. The shaped
"new bottle" recipe (`magicbottle:bottle`, result `MagicBottle(0)`) whose datapack-style `shape`/`ingredients`
(item IDs or `#` item tags) come from config. `Config.loadNewBottleRecipe` validates them into
`recipeNewBottleShape`/`recipeNewBottleIngredients`; if they're invalid it logs the problem and disables the
recipe. The default recipe uses `dragon_breath` on purpose: filled bottles are dragon's breath, which grants the "You
Need a Mint" advancement, so only players who already have it can craft one (the `shape` comment in `Settings` warns admins about it).
Like any shaped recipe it matches anywhere in the grid (and mirrored), so `CraftingGridListener` identifies it by its
key (`Recipes.getKey`), never by grid positions, and refuses it when a MagicBottle is in the grid. Depositing and
withdrawing are not recipes (see `CraftingGridListener`).
The upgrades have one smithing recipe each, registered by the same class when enabled.

**Commands** (`commands` package, `Commands.java` is the tree; `BottleArgument`, `UpgradeList` and `UpgradeListArgument` are its arguments): the single `/magicbottle` command (alias `mb`) is a Brigadier
tree (`Commands#build`) registered from `Plugin.onEnable` through `LifecycleEvents.COMMANDS`; it is not in
`plugin.yml`. Subcommands (`about`, `reload`, `give <targets> <bottle> [count]`, `upgrade <add|remove> <upgrades>`, `recipe`) are
literal nodes gated with `.requires(...)` on their permission in `Config` (`recipe` also requires a
player executor), so senders only see and can run what they may; the menu shown by the bare command lists the
nodes the source can use.
`give` follows vanilla `/give`: `targets` (Paper's players selector, fails if nobody matches; `@s` for the executor),
`bottle` with its optional count (1..6400, split into stacks; what doesn't fit is dropped at the player's feet).
`bottle` is `BottleArgument`, a custom argument with a greedy native type (the client only accepts `[` and `,`
there, so the count after the space is parsed by the argument itself): the level (0..`Config.maxLevel`) optionally
followed by the upgrades in brackets (`30`, `30[collect,autorepair]`), then the count. The upgrade list (parsing, where unknown or
repeated ids are a parse error, and suggestions after each comma) is shared with `upgrade` through `UpgradeList`. `give` is an
admin command: it ignores `magicbottle.upgrade.<id>` and `upgrades.<id>.enabled`.
`upgrade <add|remove> <upgrades>` (`magicbottle.command.upgrade`, player executor so `/execute as` works; it is not named
`magicbottle.upgrade.manage` because that namespace is one node per upgrade id) changes the upgrades of the single MagicBottle in the
executor's main hand with `MagicBottle#addUpgrade`/`removeUpgrade` (the XP and the upgrade ids this version doesn't know are kept). It is an admin
command like `give`: it ignores `magicbottle.upgrade.<id>` and `upgrades.<id>.enabled`. `<upgrades>` is `UpgradeListArgument`, a greedy
string (the client paints commas red in a word) that suggests what the bottle in hand lacks (`add`) or has (`remove`); adding what is present
or removing what is absent changes nothing and says so.
New subcommands are added as nodes in `build`, with their line in `USAGES`.

**Guide** (`guide` package): `/mb guide` (`magicbottle.command.guide`, player executor) shows native Paper dialogs (`player.showDialog`, no inventory, so no dupe surface): a home with a button per page, each page with a button back. `Guide` decides the pages the player sees when it opens (config and permissions, so `/mb reload` applies): In hand, Upgrades (item bodies: the ingredient of each upgrade, described by its lore line), Capacity and merging, Paying costs, Repair, About (the same lines `/mb about` sends, one source in `Messages.Guide.about`), and before them all the Recipe button (the chest menu below, only if the player has the recipe). Some paragraphs depend on the config and are only there when they apply (the real deposit cost of the player, the crafting grid, the repair enchantment, the capacity levels calculated from `capacity`). Every page is `Messages.Page` (title = button name, paragraphs) in `messages.guide`. `Dialogs` is the generic part (buttons, text, item rows): dialogs do not pause the game (`pause(false)`, or `afterAction` NONE is rejected) and the callbacks of the buttons are unlimited-use with a 30 minute lifetime, so a guide left open keeps working. The bottle lore is one line pointing at it (`bottle text.lore`, fixed `/magicbottle`, not `/mb`, which may belong to another plugin).

**Recipe menu** (`RecipeMenu.java`, in `guide`): `/mb recipe` (`magicbottle.command.recipe`) opens a chest inventory (3 rows, no
buttons) with the new bottle recipe: the configured shape (any shape up to 3x3), a named arrow and the empty bottle; it needs
`recipe.bottle.enabled` and `magicbottle.action.craft`, else it sends `messages.recipe.disabled`. It is the first button of the
guide. The other workstations (smithing, anvil) are only explained in the guide. Ingredients that accept several items, i.e. tags,
cycle through them every second. It must stay strictly read-only: the inventory has a custom `InventoryHolder` (`RecipeMenu`),
`RecipeMenuListener` cancels every `InventoryClickEvent`/`InventoryDragEvent` while it is the *top* inventory of the view (which
also covers shift-clicks, number keys, offhand swaps and double clicks from the inventory of the player) and clears it on
`InventoryCloseEvent`; `Plugin.onDisable` closes it for whoever has it open. The bottle shown is `MagicBottle.createDisplayItem`,
a bottle without the PDC markers, so it is never a real MagicBottle.

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
`Config.recipeNewBottle*`/`repairEnabled`/`repairAutoEnabled`. A new option is a field in `Settings`/`Messages`. An option of an older version that changes is migrated before mapping with a `ConfigFile.Migration` (`Settings.migrate`: `max level.default` becomes `capacity` once and the `max level` node is removed); there is no versioned transformation yet.
Permission node strings are also defined as constants on `Config`.

**Messages**: `messages.yml` is written in MiniMessage (no `&` codes, no `ChatColor`), and everything player-facing is
sent as Adventure components. The texts stay raw strings in `Messages.texts` and are rendered when used with
`Messages.render(msg, TagResolver...)`, passing
`Placeholder.unparsed` for values (player names, numbers) and `Placeholder.component` for components (the bottle's
`<xpbar>`, built from the `filled bar`/`empty bar` components), never `Placeholder.parsed` or `String.replace`.
Placeholder names are written as literals where they're resolved. Bottle name and lore use
`Messages.renderItemText`, which turns italics off unless the message asks for them (vanilla shows custom item text
italic).
The language comes from `language` in `Settings` (`en`, `es`; invalid = logged, `en`) and `custom messages` (whether
`messages.yml` is used). English is the field initializers of `Messages.Texts` (the reference, with the `@Comment`s); every
other language is the resource `lang/<language>.yml` (same keys as `messages.yml`, listed in `Messages.TRANSLATIONS`), loaded
by `ConfigFile.loadResource`. Layers: the admin's file (only if `custom messages`) ← the translation (`mergeFrom` in the load
migration, so a new `messages.yml` is created in that language) ← English. Keys a translation lacks are logged at load
(`logMissing`) and are English. With `custom messages` false the file is ignored (said in its header and in the log).
`Settings.migrate` sets `custom messages` to true if `messages.yml` exists and the option doesn't, so no customization is lost.
A new message is added to `Texts` and to every `lang/*.yml`. Bottles keep the language they were written in until rewritten.

**XP math** (`util/Exp.java`): ported from EssentialsX. Bukkit's built-in level/exp handling only tracks the
in-progress level, so this class recomputes true total XP points and implements the get/set/give/take API
plus the level↔exp conversion formulas (`getLevelFromExp`, `getExpAtLevel`) that vanilla Minecraft uses.

**Effects** (`effects/SoundEffect`): sounds and particles (Bukkit `spawnParticle`) for deposit/withdraw/craft
actions.

## Notes

- Crafting a new bottle has no money cost (Vault support was removed). In a crafting grid it requires the
  `magicbottle.action.craft` permission; crafters can't check it, hence the `allow crafters` option.
