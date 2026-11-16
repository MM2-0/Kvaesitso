#!/usr/bin/env python3
"""Check a release build's R8 output against tools/r8-verify/reflection-contract.txt.

    python3 tools/r8-verify/verify_reflection_contract.py <mapping-dir> [contract]

<mapping-dir> is amethyst/build/outputs/mapping/<variant>/ — BOTH mapping.txt
and usage.txt are read, because neither is sufficient alone:

  * mapping.txt records only what CHANGED. A class kept intact by
    `-keep ... { *; }` appears with an empty body, and an unrenamed member has
    no line at all. Absence there means "preserved", not "missing".
  * usage.txt is the other half: what R8 deleted. A constant that was shrunk
    away leaves no trace in mapping.txt, so removals are only visible here.

Preserved therefore means: present in mapping.txt under its own name, and not
listed in usage.txt.

R8 cannot see reflection, so a keep rule that stops matching — a class moved to
another package, a rule deleted, a DTO renamed — fails silently: the build is
green, the APK ships, and the break surfaces on a user's device as a
ClassNotFoundException, an unreadable settings file, or JSON with one-letter
property names. The contract lists what must survive; this asserts it against
what R8 actually emitted.

Exits 0 when every entry holds, 1 otherwise, naming each failure and the keep
rule that should have covered it.
"""
import re
import sys

CLASS_LINE = re.compile(r"^(?P<orig>[^\s]+) -> (?P<obf>[^\s:]+):")
# "    <type> <name> -> <new>" (field) / "    [1:2:]<ret> <name>(args) -> <new>" (method)
MEMBER_LINE = re.compile(
    r"^\s+(?:\d+:\d+:)?[^\s]+\s+(?P<name>[^\s(]+)(?P<args>\([^)]*\))?\s*->\s*(?P<new>[^\s]+)\s*$"
)


FLAVORS = ("play", "fdroid")


def parse_contract(path):
    """Read the contract. A line may open with @<flavor> to scope it.

    Play-only code (the Cast provider, the AppFunctions bridge) is not compiled
    into the F-Droid APK at all, so an unscoped entry for it would read as
    "R8 deleted this" on that variant and fail a release the moment CI checked
    both. `@play class ...` limits the check to the variant that has the class.
    """
    entries = []
    with open(path, encoding="utf-8") as fh:
        for lineno, raw in enumerate(fh, 1):
            line = raw.split("#", 1)[0].strip()
            if not line:
                continue
            parts = line.split()
            scope = None
            if parts[0].startswith("@"):
                scope = parts[0][1:]
                if scope not in FLAVORS:
                    sys.exit(f"{path}:{lineno}: unknown flavor {scope!r}; "
                             f"expected one of {', '.join(FLAVORS)}")
                parts = parts[1:]
            if len(parts) < 2:
                sys.exit(f"{path}:{lineno}: expected '<kind> <fqn> [names...]'")
            kind, fqn, rest = parts[0], parts[1], parts[2:]
            if kind not in ("class", "method", "fields", "enum"):
                sys.exit(f"{path}:{lineno}: unknown check kind {kind!r}")
            entries.append((kind, fqn, rest, lineno, scope))
    return entries


def flavor_of(mapping_dir):
    """playRelease -> play, fdroidRelease -> fdroid, anything else -> None.

    None means "check everything": an unrecognised directory must not silently
    skip entries.
    """
    variant = mapping_dir.rsplit("/", 1)[-1]
    for flavor in FLAVORS:
        if variant.startswith(flavor):
            return flavor
    return None


def consistency_check(mapping_path, usage_path):
    """Are these two files from the SAME R8 run?

    mapping.txt is written later than the rest of the mapping directory (R8
    emits it during packaging, not at minify time), so a stale one survives a
    rebuild that only got as far as minifying — leaving a directory whose
    usage.txt describes one build and whose mapping.txt describes another. The
    checks below would then read a coherent-looking mix and report nonsense.

    The invariant: a class R8 deleted cannot also be a class R8 named. Any class
    that usage.txt lists as removed while mapping.txt shows it live under a real
    name means the two files disagree about what was built.
    """
    removed = set()
    with open(usage_path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if line[:1].isspace() or not line.strip():
                continue
            name = line.strip()
            if not name.endswith(":"):
                removed.add(name)
    clashes = []
    with open(mapping_path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if line.startswith("#") or line[:1].isspace():
                continue
            m = CLASS_LINE.match(line)
            if (
                m
                and not m.group("obf").startswith("R8$$REMOVED$$")
                and m.group("orig") in removed
            ):
                clashes.append(m.group("orig"))
                if len(clashes) > 20:
                    break
    return clashes


def collect_removed(usage_path, wanted):
    """usage.txt: `com.foo.Bar` alone = class deleted; `com.foo.Bar:` + indented
    signatures = those members deleted."""
    gone_classes, gone_members, current = set(), {}, None
    with open(usage_path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if not line.strip():
                continue
            if not line[:1].isspace():
                name = line.strip()
                current = None
                if name.endswith(":"):
                    name = name[:-1]
                    if name in wanted:
                        current = name
                        gone_members.setdefault(name, [])
                elif name in wanted:
                    gone_classes.add(name)
                continue
            if current is not None:
                gone_members[current].append(line.strip())
    return gone_classes, gone_members


def collect(mapping_path, wanted):
    """Stream the mapping (it is ~500 MB) and keep only the blocks we asked for."""
    blocks, current = {}, None
    with open(mapping_path, encoding="utf-8", errors="replace") as fh:
        for line in fh:
            if line.startswith("#"):
                continue
            if not line[:1].isspace():
                m = CLASS_LINE.match(line)
                current = None
                if m and m.group("orig") in wanted:
                    current = m.group("orig")
                    blocks[current] = {"obf": m.group("obf"), "fields": {}, "methods": {}}
                continue
            if current is None:
                continue
            m = MEMBER_LINE.match(line)
            if not m:
                continue
            bucket = "methods" if m.group("args") else "fields"
            blocks[current][bucket].setdefault(m.group("name"), m.group("new"))
    return blocks


def main():
    if not 2 <= len(sys.argv) <= 3:
        sys.exit(__doc__)
    mapping_dir = sys.argv[1].rstrip("/")
    mapping_path = mapping_dir + "/mapping.txt"
    usage_path = mapping_dir + "/usage.txt"
    for required in (mapping_path, usage_path):
        try:
            open(required).close()
        except OSError as exc:
            sys.exit(f"cannot read {required}: {exc}\n"
                     "Point this at amethyst/build/outputs/mapping/<variant>/ from a "
                     "minified build.")
    contract_path = (
        sys.argv[2]
        if len(sys.argv) == 3
        else __file__.rsplit("/", 1)[0] + "/reflection-contract.txt"
    )

    clashes = consistency_check(mapping_path, usage_path)
    if clashes:
        print(
            f"{mapping_dir} is not one build: {len(clashes)}+ classes are listed as\n"
            f"removed in usage.txt yet named in mapping.txt, e.g.\n  "
            + "\n  ".join(clashes[:3])
            + "\n\nmapping.txt is written during packaging, so a minify-only rebuild leaves\n"
            "the previous one behind. Re-run a full assemble/bundle for this variant\n"
            "and check again — the results from this directory would be meaningless.",
            file=sys.stderr,
        )
        return 1

    entries = parse_contract(contract_path)
    flavor = flavor_of(mapping_dir)
    in_scope = [e for e in entries if e[4] is None or e[4] == flavor]
    skipped = len(entries) - len(in_scope)
    entries = in_scope
    wanted = {fqn for _, fqn, _, _, _ in entries}
    blocks = collect(mapping_path, wanted)
    gone_classes, gone_members = collect_removed(usage_path, wanted)

    def removed_member(fqn, name):
        """usage.txt prints whole signatures; match the member name inside one."""
        for sig in gone_members.get(fqn, ()):
            head = sig.split("(", 1)[0]
            if head.split()[-1:] == [name] or head.endswith(" " + name):
                return True
        return False

    failures = []

    def fail(lineno, fqn, msg):
        failures.append(f"  {contract_path}:{lineno}  {fqn}\n      {msg}")

    for kind, fqn, rest, lineno, _scope in entries:
        if fqn in gone_classes:
            fail(lineno, fqn, "deleted by R8 (listed in usage.txt) — nothing keeps it.")
            continue
        blk = blocks.get(fqn)
        if blk is None:
            fail(lineno, fqn, "absent from the mapping entirely — R8 removed it, or it "
                              "was renamed/moved in source and the contract is stale.")
            continue
        if blk["obf"].startswith("R8$$REMOVED$$"):
            fail(lineno, fqn, "shrunk away by R8; nothing keeps it any more.")
            continue

        if kind in ("class", "method", "fields") and blk["obf"] != fqn:
            fail(lineno, fqn, f"renamed to {blk['obf']} — it is looked up by name at runtime.")
            continue

        # From here on, a member with NO mapping line kept its name. Only an
        # explicit rename, or an entry in usage.txt, is a failure.
        if kind == "method":
            for name in rest:
                if removed_member(fqn, name):
                    fail(lineno, fqn, f"method {name}() was deleted by R8.")
                elif blk["methods"].get(name, name) != name:
                    fail(lineno, fqn, f"method {name}() renamed to {blk['methods'][name]}().")
        elif kind == "fields":
            renamed = sorted(n for n, new in blk["fields"].items() if n != new)
            if renamed:
                shown = ", ".join(renamed[:6]) + ("…" if len(renamed) > 6 else "")
                fail(lineno, fqn, f"{len(renamed)} field(s) renamed ({shown}) — these names "
                                  "are the JSON/wire format.")
            dropped = [s for s in gone_members.get(fqn, ()) if "(" not in s]
            if dropped:
                fail(lineno, fqn, f"{len(dropped)} field(s) deleted by R8: {', '.join(dropped[:4])}")
        elif kind == "enum":
            for const in rest:
                if removed_member(fqn, const):
                    fail(lineno, fqn, f"constant {const} was deleted; valueOf(\"{const}\") "
                                      "throws on a value already on disk.")
                elif blk["fields"].get(const, const) != const:
                    fail(lineno, fqn, f"constant {const} renamed to {blk['fields'][const]}; "
                                      "every stored value of it becomes unreadable.")

    checked = len(entries)
    if failures:
        print(f"R8 reflection contract: {len(failures)} of {checked} checks FAILED\n", file=sys.stderr)
        print("\n".join(failures), file=sys.stderr)
        print("\nFix the keep rule in amethyst/proguard-rules.pro (or quartz/consumer-rules.pro),"
              "\nor update the contract if the code genuinely moved.", file=sys.stderr)
        return 1
    note = f" ({skipped} skipped: not in the {flavor} flavor)" if skipped else ""
    print(f"R8 reflection contract: all {checked} checks passed against {mapping_path}{note}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
