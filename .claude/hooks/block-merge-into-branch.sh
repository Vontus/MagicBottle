#!/usr/bin/env bash
# PreToolUse hook: branches are kept up to date by rebasing onto origin/master, never by merging
# (see the Git workflow section of CLAUDE.md). Blocks `git merge` and `git pull` without rebase.
cmd=$(sed -nE 's/.*"command"[[:space:]]*:[[:space:]]*"(([^"\]|\.)*)".*/\1/p')
git_cmd='(^|[^[:alnum:]_-])git([[:space:]]+-[Cc][[:space:]]+[^[:space:]]+)*[[:space:]]+'

if printf '%s' "$cmd" | grep -qE "${git_cmd}merge([[:space:]]|$)" \
  && ! printf '%s' "$cmd" | grep -qE "${git_cmd}merge[[:space:]]+(--abort|--continue|--quit|--ff-only)"; then
  echo "Blocked: don't merge into a branch. Rebase it onto origin/master and push with --force-with-lease (CLAUDE.md, Git workflow). Only 'git merge --ff-only' is allowed." >&2
  exit 2
fi

if printf '%s' "$cmd" | grep -qE "${git_cmd}pull([[:space:]]|$)" \
  && printf '%s' "$cmd" | grep -qE -- "--no-rebase|--no-ff|--ff([[:space:]]|$)"; then
  echo "Blocked: 'git pull' must rebase, not merge (CLAUDE.md, Git workflow)." >&2
  exit 2
fi
exit 0
