#!/usr/bin/env python3
"""Keep Claude Code and Codex reading the same rules, skills and checks.

The layout this enforces (see "Agent Configuration" in AGENTS.md):

- `AGENTS.md` holds the rules. `.claude/CLAUDE.md` imports the root one with
  `@../AGENTS.md`; any other `CLAUDE.md` sits next to an `AGENTS.md` and only
  imports it (`@AGENTS.md`), so no rule is written twice.
- Every skill lives once in `.claude/skills/<name>/SKILL.md`, and
  `.agents/skills/<name>` is a symlink to it. Slash commands are skills too, so
  `.claude/commands/` stays empty.
- Each skill has a `name` matching its directory and a `description` of
  MIN_DESCRIPTION..MAX_DESCRIPTION characters. The description is what both
  agents match a task against: a one-liner stops a skill from triggering, a long
  one eats the shared skill-list budget.
- The skill list fits SKILL_LIST_CAP. Codex shows the model a bounded skill list
  (2% of the context window, or 8,000 characters when the window is unknown) of
  names, descriptions and paths, shared with the user's own skills, and shortens
  descriptions, then drops skills, past it. The cap keeps this repo's entries
  well under the 8,000 fallback so there is room for the user's skills.
- Every path a skill cites resolves: a `references/`, `scripts/` or `assets/`
  path must exist in that skill (or in another skill, for a cross-reference), a
  `.claude/skills/<name>/…` path must exist, and so must any full repo path (one
  that contains `/src/`). Shorthand like `commons/ui/StringRes.kt` is not checked.
  A `quartz/…` path names a file in vitorpamplona/quartz (its library module is
  `quartz/` there too): it is checked against a checkout next to this one
  (`../quartz`, or `$QUARTZ_DIR`) when there is one, and skipped otherwise.

Exit 0 = consistent. Exit 1 = prints every problem found.
"""

import os
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SKILLS = ROOT / ".claude" / "skills"
AGENT_SKILLS = ROOT / ".agents" / "skills"
# A description long enough to name what the skill covers, short enough for the budget.
MIN_DESCRIPTION = 120
MAX_DESCRIPTION = 300
# Codex stops reading project docs past project_doc_max_bytes (32 KiB by default).
MAX_AGENTS_MD_BYTES = 32 * 1024
# Codex's documented fallback budget for its whole skill list, all sources included.
CODEX_FALLBACK_SKILL_LIST = 8000
# What this repo's entries (name + description + `.agents/skills/<name>/SKILL.md`)
# may use of that, leaving the rest for the user's own and built-in skills.
SKILL_LIST_CAP = 6000
# vitorpamplona/quartz, checked out beside this repo (what -PquartzDir=../quartz builds).
QUARTZ_ROOT = Path(os.environ.get("QUARTZ_DIR") or ROOT.parent / "quartz")
REPO_URLS = (
    "https://github.com/vitorpamplona/amethyst/blob/main/",
    "https://github.com/vitorpamplona/amethyst/tree/main/",
    "https://raw.githubusercontent.com/vitorpamplona/amethyst/main/",
)


def git(*args):
    return subprocess.run(["git", "-C", str(ROOT), *args], capture_output=True, text=True, check=False)


def description_of(text):
    """The frontmatter's name and description, or None for a missing field."""
    match = re.match(r"\A---\n(.*?)\n---\n", text, re.S)
    if not match:
        return None, None
    lines = match.group(1).split("\n")
    fields = {}
    i = 0
    while i < len(lines):
        key, sep, value = lines[i].partition(":")
        i += 1
        if not sep or lines[i - 1].startswith(" "):
            continue
        value = value.strip()
        if value in ("|", ">", "|-", ">-"):
            block = []
            while i < len(lines) and (lines[i].startswith(" ") or not lines[i].strip()):
                block.append(lines[i].strip())
                i += 1
            value = " ".join(part for part in block if part)
        elif len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        fields[key.strip()] = value
    return fields.get("name"), fields.get("description")


def check_skills(problems):
    skills = sorted(p.name for p in SKILLS.iterdir() if (p / "SKILL.md").is_file())
    list_size = 0

    for name in skills:
        text = (SKILLS / name / "SKILL.md").read_text(encoding="utf-8")
        declared, description = description_of(text)
        where = f".claude/skills/{name}/SKILL.md"
        if declared != name:
            problems.append(f"{where}: frontmatter `name` is {declared!r}, expected {name!r}")
        if not description:
            problems.append(f"{where}: missing `description`")
            continue
        list_size += len(name) + len(description) + len(f".agents/skills/{name}/SKILL.md")
        if len(description) < MIN_DESCRIPTION:
            problems.append(
                f"{where}: description is {len(description)} characters, under {MIN_DESCRIPTION}; "
                "name the modules, classes and commands it covers so it can trigger"
            )
        elif len(description) > MAX_DESCRIPTION:
            problems.append(
                f"{where}: description is {len(description)} characters, over {MAX_DESCRIPTION}; "
                "keep the strongest triggers and move the rest into the body"
            )

        link = AGENT_SKILLS / name
        if not link.is_symlink():
            problems.append(f".agents/skills/{name}: missing, or a copy instead of a symlink to ../../.claude/skills/{name}")
        elif link.resolve() != (SKILLS / name).resolve():
            problems.append(f".agents/skills/{name}: points to {os.readlink(link)}, not ../../.claude/skills/{name}")

    if AGENT_SKILLS.is_dir():
        for entry in sorted(AGENT_SKILLS.iterdir()):
            if entry.name not in skills:
                problems.append(
                    f".agents/skills/{entry.name}: no matching .claude/skills/{entry.name}; "
                    "write the skill there and symlink it"
                )

    if list_size > SKILL_LIST_CAP:
        problems.append(
            f".claude/skills: the skill list takes {list_size} characters, over SKILL_LIST_CAP ({SKILL_LIST_CAP}) "
            f"of Codex's {CODEX_FALLBACK_SKILL_LIST}-character fallback budget; fold a skill into another's "
            "references/ or tighten descriptions instead of adding to the list"
        )

    commands = ROOT / ".claude" / "commands"
    if commands.is_dir() and any(commands.glob("*.md")):
        problems.append(".claude/commands/: slash commands must be skills in .claude/skills/ so Codex can read them")


# A backticked or markdown-linked path; trailing punctuation and `:line` suffixes are stripped.
PATH_TOKEN = re.compile(r"`([^`\s]+)`|\]\(([^)\s#]+)\)")
SKILL_LOCAL = ("references/", "scripts/", "assets/")
# Placeholders, globs and elisions mark a pattern, not a path to check.
NOT_A_PATH = re.compile(r"[…*<>{}$|]|\.\.\.")


def check_skill_paths(problems, root=SKILLS):
    skill_dirs = [d for d in root.iterdir() if (d / "SKILL.md").is_file()]
    for skill in skill_dirs:
        for doc in sorted(skill.rglob("*.md")):
            for match in PATH_TOKEN.finditer(doc.read_text(encoding="utf-8")):
                token = re.sub(r":\d+(-\d+)?$", "", (match.group(1) or match.group(2)).rstrip(".,:;)"))
                repo_path = next((token[len(u):] for u in REPO_URLS if token.startswith(u)), None)
                if repo_path is not None:
                    found = (ROOT / repo_path).exists()
                elif token.startswith(("http:", "https:", "mailto:")) or NOT_A_PATH.search(token):
                    continue
                elif token.startswith(SKILL_LOCAL):
                    found = (doc.parent / token).exists() or any((d / token).exists() for d in skill_dirs)
                elif token.startswith("quartz/") and "/src/" in token:
                    if not (QUARTZ_ROOT / "quartz" / "src").is_dir():
                        continue
                    found = (QUARTZ_ROOT / token).exists()
                elif token.startswith(".claude/skills/") or token.startswith(".agents/skills/") or "/src/" in token:
                    found = (ROOT / token).exists()
                else:
                    continue
                problem = f"{doc.relative_to(ROOT)}: cites `{token}`, which does not exist"
                if not found and problem not in problems:
                    problems.append(problem)


def check_guidance(problems):
    root_agents = ROOT / "AGENTS.md"
    if not root_agents.is_file():
        problems.append("AGENTS.md: missing; it holds the rules both agents read")
    elif root_agents.stat().st_size > MAX_AGENTS_MD_BYTES:
        problems.append(
            f"AGENTS.md: {root_agents.stat().st_size} bytes, over Codex's default {MAX_AGENTS_MD_BYTES}-byte "
            "project doc limit; Codex would silently drop the end"
        )

    found = git("ls-files", "--cached", "--others", "--exclude-standard", "--", "*CLAUDE.md", "CLAUDE.md").stdout
    for rel in sorted(set(found.split())):
        path = ROOT / rel
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8")
        if rel == ".claude/CLAUDE.md":
            if not re.search(r"^@\.\./AGENTS\.md\s*$", text, re.M):
                problems.append(".claude/CLAUDE.md: must import the rules with an `@../AGENTS.md` line")
            continue
        if not (path.parent / "AGENTS.md").is_file():
            problems.append(f"{rel}: no AGENTS.md beside it; move its rules there so Codex sees them")
        if [line for line in text.split("\n") if line.strip()] != ["@AGENTS.md"]:
            problems.append(f"{rel}: must contain only `@AGENTS.md`; put the rules in the AGENTS.md beside it")


def main():
    problems = []
    check_skills(problems)
    check_skill_paths(problems)
    check_guidance(problems)
    if problems:
        print("Agent configuration is out of sync between Claude Code and Codex:", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        print("See 'Agent Configuration' in AGENTS.md.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
