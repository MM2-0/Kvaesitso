#!/bin/bash
# PreToolUse gate: make sure Kotlin is spotless-clean BEFORE it leaves the box.
#
# Fires on `git push` (Bash tool) and on the create_pull_request MCP tool. Runs
# `spotlessApply`; if that reformats any tracked .kt/.kts file, the push/PR is
# blocked (exit 2) so the agent commits the formatting fix first. This turns
# CI's `spotlessCheck` failure into an in-session block — no red PR, no round
# trip. `spotlessApply` runs the same formatters CI's `spotlessCheck` verifies,
# so a clean apply means a green check.
set -uo pipefail

hook_dir="$(cd "$(dirname "$0")" && pwd)"
# Shared by Claude Code (.claude/settings.json) and Codex (.codex/hooks.json):
# resolve the repo from this script, not from either tool's env vars.
cd "$hook_dir/../.." || exit 0

# --- Is this call a push/PR boundary? ---
payload="$(cat)"

# Cheap pure-bash pre-filter before paying for a python spawn. The gate below
# can only answer "yes" for a payload containing "push" (a git push command) or
# "pull_request" (the create_pull_request MCP tool), so anything else is a
# guaranteed no. This hook runs on EVERY Bash tool call, and the spawn it skips
# costs ~35ms each time.
case "$payload" in
  *push*|*pull_request*) ;;
  *) exit 0 ;;
esac

printf '%s' "$payload" | python3 "$hook_dir/lib/git_push_gate.py" || exit 0

# Nothing to format if no Kotlin is tracked/changed at all — cheap early out.
if ! git ls-files --error-unmatch '*.kt' '*.kts' >/dev/null 2>&1; then
  exit 0
fi

# Snapshot Kotlin state (vs HEAD, so staged + unstaged both count) before/after
# formatting; any delta means the committed tree wasn't spotless.
before="$(git diff HEAD -- '*.kt' '*.kts' 2>/dev/null | sha1sum)"

log="$(mktemp /tmp/spotless-gate.XXXXXX.log)"
if ! ./gradlew spotlessApply >"$log" 2>&1; then
  # Any failure blocks. The web sandbox pre-seeds the Gradle distribution (see
  # .claude/hooks/session-start.sh) and Gradle resolves deps through the proxy,
  # so spotlessApply no longer fails for infra reasons — a failure here is a
  # real formatting/compile error, not a restricted-sandbox hiccup.
  echo "BLOCKED: spotlessApply failed — fix the build/formatting error before pushing." >&2
  echo "----- gradle output (tail) -----" >&2
  tail -n 40 "$log" >&2
  rm -f "$log"
  exit 2
fi
rm -f "$log"

after="$(git diff HEAD -- '*.kt' '*.kts' 2>/dev/null | sha1sum)"

if [ "$before" != "$after" ]; then
  echo "BLOCKED: spotlessApply reformatted Kotlin files that were about to be pushed." >&2
  echo "The changes below are now in your working tree. Commit them, then retry:" >&2
  echo >&2
  git diff --name-only HEAD -- '*.kt' '*.kts' >&2
  echo >&2
  echo "  git add -A && git commit -m 'style: apply spotless' && <retry the push>" >&2
  echo "(CI runs 'spotlessCheck'; pushing now would fail the lint job.)" >&2
  exit 2
fi

exit 0
