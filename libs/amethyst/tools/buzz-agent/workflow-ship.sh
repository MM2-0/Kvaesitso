#!/usr/bin/env bash
#
# workflow-ship.sh — the --on-approve step of `amy buzz workflow run` (the GATED path).
#
# The runner calls this ONLY after a human grants the run's 46010 approval gate. It pushes the
# run's branch and opens (or reuses) the PR, printing the PR URL as the run's result (46005). It
# NEVER touches the default branch and NEVER force-pushes — the merge stays a human action on GitHub.
#
# Contract (set by the runner):
#   BUZZ_WORKTREE .... the run's git worktree (already carries the agent's commits)
#   BUZZ_BRANCH ...... the run's branch to push
#   BUZZ_RUN ......... the run id
#   stdout ........... the PR URL → the run result (46005)
#   non-zero exit .... fails the run; stderr is the detail
#
# Host requirements: git + gh authenticated with a PR-ONLY token (Contents:RW + PRs:RW on this repo),
# and branch protection on the default branch. See README.md.

set -euo pipefail
log() { printf '%s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

[[ -n "${BUZZ_WORKTREE:-}" ]] || die "BUZZ_WORKTREE unset — run this under 'amy buzz workflow run'"
[[ -n "${BUZZ_BRANCH:-}" ]] || die "BUZZ_BRANCH unset"
cd "$BUZZ_WORKTREE" || die "cannot cd into worktree $BUZZ_WORKTREE"

# The PR base = the repo's default branch. Never operate on it directly.
# `gh` can also succeed while printing nothing (or a literal "null") for a repo it can't
# resolve, so fall back on the value, not just on the exit status.
base_branch="$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name 2>/dev/null || true)"
[[ -n "$base_branch" && "$base_branch" != "null" ]] || base_branch="main"
case "$BUZZ_BRANCH" in
    "$base_branch" | main | master) die "refusing to operate on the default branch ($BUZZ_BRANCH)" ;;
    # Anything else is a per-run branch — the only thing this script is allowed to push.
    *) : ;;
esac

# `|| true` keeps `set -e` from killing the script when the worktree has no commits yet —
# without it the fallback below is unreachable and the run fails with an empty stderr.
title="$(git log -1 --format='%s' 2>/dev/null | cut -c1-72)" || true
[[ -n "$title" ]] || title="Buzz run ${BUZZ_RUN:-}"

log "[workflow-ship] pushing $BUZZ_BRANCH"
git push -u origin "HEAD:$BUZZ_BRANCH" || die "push failed (is a PR-only token configured?)"

pr_url="$(gh pr list --head "$BUZZ_BRANCH" --state open --json url -q '.[0].url' 2>/dev/null || true)"
if [[ -z "$pr_url" ]]; then
    body="Approved via Buzz workflow run \`${BUZZ_RUN:-unknown}\`. Merge is a human action on GitHub."
    pr_url="$(gh pr create --base "$base_branch" --head "$BUZZ_BRANCH" --title "$title" --body "$body" 2>/dev/null)" ||
        die "gh pr create failed (PR-only token + branch protection configured?)"
fi

printf 'Opened PR: %s\n' "$pr_url"
