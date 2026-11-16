#!/bin/bash
# PreToolUse gate: the Compose resource catalog must not carry Android escaping.
#
# Fires on `git push` (Bash tool) and on the create_pull_request MCP tool.
# Delegates to compose_escaping_check.py, which scans
# `*/src/*/composeResources/values*/strings.xml` for \' \" \? \@ and `tools:`
# attributes. Compose resolves only \uXXXX, \n and \t, so anything else carried
# verbatim out of an Android res tree renders literally -- the login screen once
# read `Don\'t have a Nostr account?`.
#
# Why a dedicated hook: this is reintroduced by every Crowdin sync, because
# Crowdin holds the Android-escaped source. It came back twice in two days, 2,068
# escaped apostrophes across 40 locales each time. Nothing in the Gradle build
# fails on it -- the strings simply ship wrong -- so there is no slow gate this
# stands in for; it is the only gate.
#
# Run the scan by hand any time with:  tools/agent-hooks/compose_escaping_check.py
set -uo pipefail

hook_dir="$(cd "$(dirname "$0")" && pwd)"
# Shared by Claude Code (.claude/settings.json) and Codex (.codex/hooks.json):
# resolve the repo from this script, not from either tool's env vars.
cd "$hook_dir/../.." || exit 0

payload="$(cat)"

# Same cheap pre-filter as the orphan-strings gate: only a payload mentioning a
# push or the PR tool can possibly match, and this runs on every Bash call.
case "$payload" in
  *push*|*pull_request*) ;;
  *) exit 0 ;;
esac

printf '%s' "$payload" | python3 "$hook_dir/lib/git_push_gate.py" || exit 0

# The scan exits 1 on a hit (CI reads that), but a PreToolUse hook only blocks the
# call on exit 2 (the Claude Code contract that Codex hooks follow), so map any failure to 2.
python3 "$hook_dir/compose_escaping_check.py" || exit 2
