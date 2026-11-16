#!/usr/bin/env python3
"""Decide whether a PreToolUse payload on stdin is a push/PR boundary.

Shared by every pre-push hook in this directory (pre-push-spotless.sh,
pre-push-orphan-strings.sh) so the gate condition is defined once. Each hook is
a separate process with its own stdin, so this is exec'd per hook rather than
run once and shared.

Claude Code and Codex send the same PreToolUse payload: Codex reports its shell
tool as `Bash` too, with the command in `tool_input.command`. Older Codex tool
names and an argv-list command are still accepted, as a fallback.

Exit 0 = this call publishes code (gate it). Exit 1 = let it through.
"""

import json
import shlex
import sys

# Reaching the push subcommand means stepping over git's global options first.
GLOBAL_WITH_ARG = {"-c", "-C", "--namespace", "--git-dir", "--work-tree", "--exec-path"}

# `Bash` for both agents; the rest are older Codex names, kept as a fallback.
SHELL_TOOLS = {"Bash", "exec_command", "shell", "shell_command", "local_shell"}


def command_of(tool_input):
    cmd = tool_input.get("command", tool_input.get("cmd", ""))
    if isinstance(cmd, list):
        # An argv list like ["bash", "-lc", "git push"]: the script is its last element.
        return cmd[-1] if len(cmd) >= 3 and cmd[1] in ("-c", "-lc") else shlex.join(cmd)
    return cmd if isinstance(cmd, str) else ""


def is_boundary(data):
    tool = data.get("tool_name", "")
    if tool.endswith("create_pull_request"):
        return True
    if tool not in SHELL_TOOLS:
        return False

    cmd = command_of(data.get("tool_input") or {})
    # Tokenize like a shell so `push` inside a quoted commit message or heredoc
    # stays one token and is NOT mistaken for the push subcommand.
    try:
        tokens = shlex.split(cmd, comments=True)
    except ValueError:
        tokens = cmd.split()

    for i, token in enumerate(tokens):
        if token != "git" and not token.endswith("/git"):
            continue
        j = i + 1
        while j < len(tokens):
            tok = tokens[j]
            if tok in GLOBAL_WITH_ARG:
                j += 2
            elif tok.startswith("-"):
                j += 1
            else:
                break
        if j < len(tokens) and tokens[j] == "push":
            return True
    return False


def main():
    try:
        data = json.load(sys.stdin)
    except Exception:
        return 1
    return 0 if is_boundary(data) else 1


if __name__ == "__main__":
    sys.exit(main())
