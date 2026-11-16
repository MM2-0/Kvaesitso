#!/usr/bin/env bash
#
# test-workflow-ship.sh — regression harness for workflow-ship.sh.
#
# Runs the real ship script end to end against a throwaway git repo with a local
# bare "origin" (so `git push` genuinely pushes) and a stub `gh` on PATH whose
# behaviour each case controls. No network, no GitHub, no credentials.
#
#   ./test-workflow-ship.sh                    # test the sibling workflow-ship.sh
#   ./test-workflow-ship.sh /path/to/other.sh  # or any other copy
#
# Exits 0 when every case passes, 1 otherwise — safe to wire into CI on any host
# that has bash + git.
#
# Why this exists: the ship step runs unattended under `amy buzz workflow run` and
# only ever reports through stdout/stderr, so a `set -e` abort or a bad `--base`
# surfaces as a mystery failure on someone else's machine. These cases pin the
# behaviour that made those failures diagnosable.

set -uo pipefail

SCRIPT="${1:-$(dirname "$0")/workflow-ship.sh}"
[[ -f "$SCRIPT" ]] || { echo "no such script: $SCRIPT" >&2; exit 2; }
SCRIPT="$(cd "$(dirname "$SCRIPT")" && pwd)/$(basename "$SCRIPT")"
pass=0
fail=0

# --- stub gh ---------------------------------------------------------------
STUB_DIR="$(mktemp -d)"
trap 'rm -rf "$STUB_DIR"' EXIT
cat > "$STUB_DIR/gh" <<'STUB'
#!/usr/bin/env bash
case "$1 $2" in
    "repo view")
        case "${GH_DEFAULT_BRANCH_MODE:-ok}" in
            ok)    printf '%s\n' "${GH_DEFAULT_BRANCH:-main}" ;;
            null)  printf 'null\n' ;;   # what jq prints for a missing defaultBranchRef
            empty) printf '' ;;         # exits 0, says nothing
            fail)  exit 1 ;;            # gh itself errors
        esac
        ;;
    "pr list") printf '%s\n' "${GH_EXISTING_PR:-}" ;;
    "pr create")
        # The script runs `gh pr create` inside $( … 2>/dev/null ), so the stub
        # cannot report through stderr — write to the log the harness reads.
        base=""
        while [[ $# -gt 0 ]]; do [[ "$1" == "--base" ]] && base="${2-}"; shift; done
        printf 'pr-create base=<%s>\n' "$base" >> "$STUB_LOG"
        printf 'https://github.com/acme/repo/pull/7\n'
        ;;
    *) exit 1 ;;
esac
STUB
chmod +x "$STUB_DIR/gh"
export PATH="$STUB_DIR:$PATH"
export STUB_LOG="$STUB_DIR/calls.log"

# --- a fresh repo per case -------------------------------------------------
new_repo() { # $1 = "commit" | "empty"
    local root
    root="$(mktemp -d)"
    git init -q --bare "$root/origin.git"
    git init -q -b main "$root/work"
    git -C "$root/work" config user.email harness@example.test
    git -C "$root/work" config user.name  Harness
    git -C "$root/work" config commit.gpgsign false
    git -C "$root/work" remote add origin "$root/origin.git"
    if [[ "$1" == "commit" ]]; then
        echo hi > "$root/work/f.txt"
        git -C "$root/work" add f.txt
        git -C "$root/work" commit -qm "feat: teach the widget to fly"
    fi
    printf '%s\n' "$root/work"
}

check() { # name, expected-exit, expected-substring, [VAR=value ...]
    local name="$1" want_exit="$2" want_text="$3"
    shift 3
    local wt out code
    wt="$(new_repo "${REPO_STATE:-commit}")"
    : > "$STUB_LOG"
    out="$(cd "$wt" && env "$@" BUZZ_WORKTREE="$wt" BUZZ_BRANCH="${BUZZ_BRANCH:-claude/job-1}" \
        BUZZ_RUN=run123 bash "$SCRIPT" 2>&1)"
    code=$?
    out="$out $(cat "$STUB_LOG")"
    if [[ "$code" == "$want_exit" && "$out" == *"$want_text"* ]]; then
        printf '  PASS  %s\n' "$name"
        pass=$((pass + 1))
    else
        printf '  FAIL  %s\n        want exit=%s containing %q\n        got  exit=%s: %s\n' \
            "$name" "$want_exit" "$want_text" "$code" "${out//$'\n'/ | }"
        fail=$((fail + 1))
    fi
    rm -rf "$(dirname "$wt")"
    # bash keeps `VAR=x func` assignments set after the call — clear them so one
    # case cannot configure the next.
    unset REPO_STATE BUZZ_BRANCH GH_EXISTING_PR
}

printf '\nworkflow-ship harness: %s\n\n' "$SCRIPT"

# --- the happy paths -------------------------------------------------------
check "opens a PR and prints its url" 0 "Opened PR: https://github.com/acme/repo/pull/7"
GH_EXISTING_PR="https://github.com/acme/repo/pull/3" \
    check "reuses an already-open PR instead of creating another" 0 \
    "Opened PR: https://github.com/acme/repo/pull/3" \
    GH_EXISTING_PR="https://github.com/acme/repo/pull/3"

# --- the default-branch guard (the script's one safety property) -----------
BUZZ_BRANCH=main    check "refuses to push main"    1 "refusing to operate on the default branch"
BUZZ_BRANCH=master  check "refuses to push master"  1 "refusing to operate on the default branch"
BUZZ_BRANCH=develop check "refuses to push whatever gh reports as default" 1 \
    "refusing to operate on the default branch" GH_DEFAULT_BRANCH=develop
BUZZ_BRANCH=main    check "still refuses main when gh is unusable" 1 \
    "refusing to operate on the default branch" GH_DEFAULT_BRANCH_MODE=fail

# --- a worktree with no commits --------------------------------------------
# Nothing to push, so this must fail — the point is that it fails *with a
# message*. `git log` used to trip `set -e` at the `title=` line, killing the run
# at exit 128 with completely empty output (git's own error swallowed by the
# 2>/dev/null), which the runner then reported as a failure with empty stderr.
REPO_STATE=empty check "empty worktree fails loudly, not silently" 1 "push failed"

# --- base_branch resolution ------------------------------------------------
# Only the "gh exited non-zero" case used to be handled; a gh that succeeds while
# printing `null` or nothing sent that value straight to `gh pr create --base`.
check "gh prints literal null -> base falls back to main" 0 "base=<main>" GH_DEFAULT_BRANCH_MODE=null
check "gh prints nothing      -> base falls back to main" 0 "base=<main>" GH_DEFAULT_BRANCH_MODE=empty
check "gh exits non-zero      -> base falls back to main" 0 "base=<main>" GH_DEFAULT_BRANCH_MODE=fail
check "gh answers normally    -> base is what gh said"    0 "base=<develop>" GH_DEFAULT_BRANCH=develop

printf '\n  %d passed, %d failed\n\n' "$pass" "$fail"
[[ "$fail" -eq 0 ]]
