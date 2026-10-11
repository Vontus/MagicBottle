# Contributing

How code is written, tested, reviewed and merged in MagicBottle. These rules apply when programming, not only when
reviewing. How the plugin is built inside (listeners, `MagicBottle`, config layer...) is described in
[CLAUDE.md](CLAUDE.md).

There is a single supported line: current Paper. The old per-Minecraft-version branches were removed; their tips are
kept as `archive/*` tags.

## Building

```
./gradlew build       # compile + produce the shaded plugin jar: build/libs/MagicBottle-<version>.jar
```

Requires a JDK 25 (Gradle toolchain). On Windows use `gradlew.bat`. `./gradlew build` must pass after every change.

## Rules

They are ordered by how much a mistake costs: the first ones are what a review blocks on.

### Players never lose XP

- **No change in the plugin loses XP.** A new version, a migration or a config change (e.g. a lower capacity) keeps
  the XP stored in every existing bottle: a bottle left above a new limit keeps all its XP and only refuses deposits
  until it goes below it. XP is only lost through something the player does knowingly (a deposit cost, a repair, a
  feature that consumes it and says so).
- **Check first, then move.** A transaction checks the destination, the permissions and the config, and only then
  moves the XP, so an action that can't complete moves nothing (see `onClickCraftResult`, `AnvilListener.onClickResult`).
- **Every way of moving an item.** Code that touches inventories considers plain click, shift-click, number key,
  F (offhand), double click, drag, Q/Ctrl+Q, creative middle click, hoppers and crafters. Most dupes come from the one
  that was forgotten. Paths without a player (crafters, hoppers, dispensers) can't check permissions.
- **Previews match the action.** What a preview shows (crafting result, anvil lore) is exactly what the action does:
  previews are deterministic, and the randomness of the stochastic rounding is only in the real transaction.

### The persisted format is a public API

Bottles live in player inventories for years.

- The PDC keys (`magicbottle:bottle`, `magicbottle:exp`, `magicbottle:upgrades`) and the upgrade ids are never renamed.
- Upgrade ids this version doesn't know are kept when a bottle is rewritten (a downgrade or a newer version must not lose them).
- Everything about the 1.5.x format stays in `LegacyBottle`.

### Code

- Bottles are only created or changed through `MagicBottle`, so PDC, lore, name and material stay in sync. No other
  class touches their `ItemMeta` or PDC.
- Every feature has a permission (a constant on `Config`) and, if it can be turned off, a config switch. Decide whether
  `/mb reload` changes it (read it when used) or not.
- A new option is a field in `Settings` (or `Messages.Texts`) with its default and a `@Comment`. An invalid value disables
  only its feature, unless it is something the admin must notice (like an invalid upgrade ingredient).
- Messages are MiniMessage, rendered with `Messages.render`. Values go through `Placeholder.unparsed` (player names
  could inject tags otherwise), components through `Placeholder.component`; never `Placeholder.parsed`, `String.replace`,
  `&` codes or `ChatColor`.
- Hot events (`PlayerExpChangeEvent`, `PlayerItemDamageEvent`, inventory clicks) check the cheap conditions first and
  only look where they must (hotbar and offhand for upgrades). Elsewhere, readability wins over micro-optimizations.
- Exp math uses integers.
- Nullness is checked by NullAway: whatever can be null is annotated `@Nullable` (`org.jspecify.annotations`), and Bukkit calls that
  return null (`getItemMeta()`, `getItem()`, PDC `get`) are checked before use. A field that is set later by `onEnable` is
  suppressed with `@SuppressWarnings("NullAway.Init")` and a comment saying who sets it.
- Reuse what exists (`Exp`, `ResultSlot.destination`, `MagicBottle.findWithUpgrade`, `UpgradeList`...) instead of
  reimplementing it, and write code like the code around it: naming, idioms, comments that say why.
- A change to the architecture or to a rule updates CLAUDE.md or this file in the same PR.

## Testing

Until there are unit tests, behavior is tested by hand on a Paper server. The PR says what was tried; for anything
that moves items, with which of the ways listed above.

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
  (`Fixes #61`) and follows the PR template.
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

## Reviewing

Review against the rules above, most important first. Leave to the tools what they catch (compiler warnings,
formatting) and spend the review on what they can't see: a dupe, lost XP, a broken format.

Prefix each comment with a label ([Conventional Comments](https://conventionalcomments.org/)) so the author knows what
blocks the merge:

- `blocking:` must be fixed before merging (a rule above is broken).
- `question:` the reviewer doesn't understand something; not necessarily wrong.
- `suggestion:` an improvement worth considering; optional.
- `nit:` a minor detail (naming, style, a loop that reads better another way); optional, never blocks.
