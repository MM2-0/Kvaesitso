#!/usr/bin/env bash
#
# workflow-agent.sh — the --exec step of `amy buzz workflow run` (the GATED path).
#
# The runner calls this once per workflow run to do the agent's work. It runs a coding agent
# (Claude Code by default) on the task inside the run's isolated git worktree and COMMITS the
# result — but it does NOT push. A human approves the 46010 gate first; then workflow-ship.sh
# (the --on-approve step) pushes the branch and opens the PR.
#
# Contract (set by the runner):
#   stdin ............ the task text
#   BUZZ_WORKTREE .... the run's git worktree (a fresh branch off BUZZ_BASE_REF)
#   BUZZ_BRANCH ...... the run's branch name
#   BUZZ_BASE_REF .... what the branch was cut from
#   BUZZ_RUN ......... the run id;  BUZZ_REQUESTER — who asked
#   stdout ........... becomes the approver's gate note (46010) — we print a short summary
#   non-zero exit .... fails the run before it ever reaches the gate; stderr is the detail
#
# Config knobs (env): AGENT_CMD (override the whole agent call; reads the prompt on stdin and as
# $AGENT_PROMPT), AGENT_ALLOWED_TOOLS (Claude Code --allowedTools), COMMIT_PREFIX.

set -euo pipefail
log() { printf '%s\n' "$*" >&2; }
die() { printf 'error: %s\n' "$*" >&2; exit 1; }

AGENT_CMD="${AGENT_CMD:-}"
AGENT_ALLOWED_TOOLS="${AGENT_ALLOWED_TOOLS:-Edit,Write,Read,Bash,Glob,Grep}"
COMMIT_PREFIX="${COMMIT_PREFIX:-feat}"

[[ -n "${BUZZ_WORKTREE:-}" ]] || die "BUZZ_WORKTREE unset — run this under 'amy buzz workflow run'"
cd "$BUZZ_WORKTREE" || die "cannot cd into worktree $BUZZ_WORKTREE"
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || die "worktree is not a git repo"

# Pin the start commit so "did the agent change anything?" is correct even off a moving HEAD.
base_sha="$(git rev-parse HEAD)"

task="$(cat)"
[[ -n "${task//[[:space:]]/}" ]] || die "empty task"
title="$(printf '%s' "$task" | head -n1 | cut -c1-72)"

log "[workflow-agent] run ${BUZZ_RUN:-?}: $title"
prompt="You are working in a fresh git worktree on branch '${BUZZ_BRANCH:-?}' (off '${BUZZ_BASE_REF:-HEAD}').
Implement the request below and then stop. Do NOT switch branches, push, or open a PR — the
wrapper handles git after a human approves. Keep changes scoped to this repository.

TASK:
$task"

if [[ -n "$AGENT_CMD" ]]; then
    export AGENT_PROMPT="$prompt"
    summary="$(printf '%s' "$prompt" | bash -c "$AGENT_CMD" 2>&1)" || die "agent command failed"
else
    command -v claude >/dev/null 2>&1 || die "claude CLI not found (set AGENT_CMD to your agent)"
    summary="$(claude -p "$prompt" --permission-mode acceptEdits --allowedTools "$AGENT_ALLOWED_TOOLS" 2>&1)" ||
        die "claude run failed"
fi

# Verify the agent produced changes; commit anything it left uncommitted. No push here — the gate.
committed="$(git rev-list --count "$base_sha"..HEAD 2>/dev/null || echo 0)"
if [[ -z "$(git status --porcelain)" && "$committed" == "0" ]]; then
    die "the agent produced no changes"
fi
if [[ -n "$(git status --porcelain)" ]]; then
    git add -A
    git -c user.name="Buzz Agent" -c user.email="agent@localhost" commit -q \
        -m "$COMMIT_PREFIX: $title" -m "Buzz run ${BUZZ_RUN:-unknown} — awaiting approval."
fi

# stdout → the gate note the approver reads before granting.
printf 'Ready for review on %s.\n\n%s\n' "${BUZZ_BRANCH:-?}" "$summary"
