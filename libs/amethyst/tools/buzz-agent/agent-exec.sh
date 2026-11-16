#!/usr/bin/env bash
#
# agent-exec.sh — reference `--exec` wrapper for `amy buzz agent serve`.
#
# The scheduler runs this once per Buzz job (kind-43001). Contract:
#   stdin ............ the task text (the job request)
#   cwd .............. the job's isolated git worktree (a fresh branch off --base-ref)
#   env .............. BUZZ_JOB_ID BUZZ_REQUESTER BUZZ_CHANNEL BUZZ_RELAY BUZZ_AGENT
#                      BUZZ_UPVOTES BUZZ_BRANCH BUZZ_WORKTREE BUZZ_BASE_REF
#   stdout ........... becomes the job RESULT (kind-43004) — we print the PR URL
#   non-zero exit .... becomes the job ERROR (kind-43006); stderr is the error detail
#
# What it does: runs a coding agent (Claude Code by default) on the task inside the
# worktree, commits + pushes the job branch, opens a PR, and prints the PR URL. It NEVER
# touches the default branch and NEVER force-pushes — the merge is a human action on
# GitHub. See README.md for the required token scope + branch-protection checklist.
#
# Host requirements: git, gh (authenticated with a PR-ONLY token), and the agent CLI
# (`claude`), or a custom command via $AGENT_CMD.

set -euo pipefail

log() { printf '%s\n' "$*" >&2; }                 # progress → stderr (job-error tail on failure)
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

# --- config knobs (all overridable via env) --------------------------------
AGENT_CMD="${AGENT_CMD:-}"                          # override the entire agent invocation; reads the
                                                    # prompt on stdin and as $AGENT_PROMPT
AGENT_ALLOWED_TOOLS="${AGENT_ALLOWED_TOOLS:-Edit,Write,Read,Bash,Glob,Grep}"
COMMIT_PREFIX="${COMMIT_PREFIX:-feat}"

# --- guards ----------------------------------------------------------------
[[ -n "${BUZZ_BRANCH:-}" ]] || die "BUZZ_BRANCH unset — run the scheduler with --worktree"
[[ -n "${BUZZ_WORKTREE:-}" ]] || die "BUZZ_WORKTREE unset — run the scheduler with --worktree"
cd "$BUZZ_WORKTREE" || die "cannot cd into worktree $BUZZ_WORKTREE"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "worktree is not a git repo"

# The PR base = the repo's default branch. Never operate on it directly.
base_branch="$(gh repo view --json defaultBranchRef -q .defaultBranchRef.name 2>/dev/null || echo main)"
case "$BUZZ_BRANCH" in
    "$base_branch" | main | master) die "refusing to operate on the default branch ($BUZZ_BRANCH)" ;;
esac

# Pin the branch's starting commit now, so "did the agent change anything?" is correct even
# when --base-ref is the symbolic "HEAD" (which moves as the agent commits).
base_sha="$(git rev-parse HEAD)"

# --- 1. read the task ------------------------------------------------------
task="$(cat)"
[[ -n "${task//[[:space:]]/}" ]] || die "empty task"
title="$(printf '%s' "$task" | head -n1 | cut -c1-72)"

# --- 2. run the coding agent inside the worktree ---------------------------
log "[agent-exec] job ${BUZZ_JOB_ID:-?}: running agent on: $title"
prompt="You are working in a fresh git worktree on branch '$BUZZ_BRANCH' (off '${BUZZ_BASE_REF:-HEAD}').
Implement the request below and then stop. Do NOT switch branches, push, or open a PR — the
wrapper handles git. Keep changes scoped to this repository.

TASK:
$task"

if [[ -n "$AGENT_CMD" ]]; then
    export AGENT_PROMPT="$prompt"
    agent_summary="$(printf '%s' "$prompt" | bash -c "$AGENT_CMD" 2>&1)" || die "agent command failed"
else
    command -v claude >/dev/null 2>&1 || die "claude CLI not found (set AGENT_CMD to your agent)"
    agent_summary="$(claude -p "$prompt" --permission-mode acceptEdits --allowedTools "$AGENT_ALLOWED_TOOLS" 2>&1)" ||
        die "claude run failed"
fi

# --- 3. verify the agent produced changes ----------------------------------
committed="$(git rev-list --count "$base_sha"..HEAD 2>/dev/null || echo 0)"
if [[ -z "$(git status --porcelain)" && "$committed" == "0" ]]; then
    die "the agent produced no changes"
fi

# --- 4. commit anything the agent left uncommitted -------------------------
if [[ -n "$(git status --porcelain)" ]]; then
    git add -A
    git -c user.name="Buzz Agent" -c user.email="agent@localhost" commit -q \
        -m "$COMMIT_PREFIX: $title" -m "Filed via Buzz job ${BUZZ_JOB_ID:-unknown}."
fi

# --- 5. push the job branch (feature branch only, never --force) -----------
log "[agent-exec] pushing $BUZZ_BRANCH"
git push -u origin "HEAD:$BUZZ_BRANCH" || die "push failed (is a PR-only token configured?)"

# --- 6. open (or reuse) the PR ---------------------------------------------
pr_url="$(gh pr list --head "$BUZZ_BRANCH" --state open --json url -q '.[0].url' 2>/dev/null || true)"
if [[ -z "$pr_url" ]]; then
    body="$task

---
Filed via Buzz job \`${BUZZ_JOB_ID:-unknown}\`${BUZZ_REQUESTER:+ by \`$BUZZ_REQUESTER\`}.

Agent notes:

$agent_summary"
    pr_url="$(gh pr create --base "$base_branch" --head "$BUZZ_BRANCH" --title "$title" --body "$body" 2>/dev/null)" ||
        die "gh pr create failed (PR-only token + branch protection configured?)"
fi

# --- 7. emit the result → job 43004 ----------------------------------------
printf 'Opened PR: %s\n' "$pr_url"
