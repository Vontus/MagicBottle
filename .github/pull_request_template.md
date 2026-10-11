<!--
Title: English, imperative, like a commit subject. One change per PR.
The rules are in CONTRIBUTING.md; delete the checklist items that don't apply.
-->

## What and why

Fixes #

## How it was tested

<!-- What you tried on the server. For anything that moves items: plain click, shift-click, number key, F,
double click, drag, Q/Ctrl+Q, creative middle click, hoppers, crafters. -->

## Checklist

- [ ] `./gradlew build` passes.
- [ ] No XP can be lost or duplicated: everything is checked before moving XP, and every way of moving the item was considered.
- [ ] Existing bottles keep working: PDC keys and upgrade ids unchanged, unknown upgrade ids kept, no XP lost to a new limit.
- [ ] Bottles are only created or changed through `MagicBottle`.
- [ ] New features have a permission and, if needed, a config option with a default and a comment.
- [ ] CLAUDE.md / CONTRIBUTING.md updated if the architecture or a rule changed.
