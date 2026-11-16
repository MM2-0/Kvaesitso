#!/bin/bash
# PreToolUse gate: Claude Code and Codex must keep reading the same rules and skills.
#
# Fires on `git push` (Claude's Bash tool or Codex's shell tool) and on the
# create_pull_request MCP tool. Delegates to agent_config_check.py, which checks
# that AGENTS.md holds the rules and every CLAUDE.md only imports it, that every
# skill in .claude/skills/ is symlinked (not copied) from .agents/skills/, and
# that no skill description lost most of its triggers. See "Agent Configuration"
# in AGENTS.md.
#
# Run the check by hand any time with:  tools/agent-hooks/agent_config_check.py
set -uo pipefail

hook_dir="$(cd "$(dirname "$0")" && pwd)"
# Shared by Claude Code (.claude/settings.json) and Codex (.codex/hooks.json):
# resolve the repo from this script, not from either tool's env vars.
cd "$hook_dir/../.." || exit 0

payload="$(cat)"

# Cheap pure-bash pre-filter: only a payload mentioning "push" or "pull_request"
# can be a boundary, and this hook runs on every shell tool call.
case "$payload" in
  *push*|*pull_request*) ;;
  *) exit 0 ;;
esac

printf '%s' "$payload" | python3 "$hook_dir/lib/git_push_gate.py" || exit 0

# A PreToolUse hook blocks the call only on exit 2.
python3 "$hook_dir/agent_config_check.py" || exit 2
