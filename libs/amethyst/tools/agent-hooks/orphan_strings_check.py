#!/usr/bin/env python3
"""Fail if any locale declares a string resource its default values/ no longer has.

A key removed or renamed in a default `values/strings.xml` orphans every
`values-<locale>/strings.xml` entry that still declares it. In an Android res
tree that is an `[ExtraTranslation]` lint ERROR, which aborts
`:amethyst:lint<Variant>` and with it the whole `test-and-build-android` CI job.

Run directly, or via the pre-push-orphan-strings.sh hook that wraps it.
Exits 0 when clean, 2 with a report when not.
"""

import glob
import os
import re
import sys
from collections import defaultdict

# values-night, values-v29, values-sw600dp, ... are configuration qualifiers,
# not locales; only locale-qualified dirs can hold a translation.
LOCALE = re.compile(r"^values-(?:b\+[A-Za-z0-9+]+|[a-z]{2,3}(?:-r[A-Z]{2,3})?)$")
NAMED = re.compile(r'<(?:string|plurals|string-array)\s+[^>]*name="([^"]+)"')

# Both Crowdin-managed resource systems (see the find-missing-translations
# skill, "Resource trees — scan BOTH"), each with what an orphan costs there.
# Android res is the tree lint policies; the Compose-Multiplatform catalog is
# not lint-checked, but an orphan there is the same authoring mistake and
# leaves a dead translation behind.
ROOTS = (
    ("*/src/*/res/values", "Android lint [ExtraTranslation] error — aborts the build"),
    ("*/src/*/composeResources/values", "dead translation — key no longer exists in the default catalog"),
)


def names(paths):
    found = set()
    for path in paths:
        with open(path, encoding="utf-8") as handle:
            found |= set(NAMED.findall(handle.read()))
    return found


def find_orphans():
    orphans = defaultdict(list)  # (res_root, key, consequence) -> [locale, ...]
    for pattern, consequence in ROOTS:
        for default_dir in sorted(glob.glob(pattern)):
            res_root = os.path.dirname(default_dir)
            base = names(glob.glob(os.path.join(default_dir, "*.xml")))
            for locale_dir in sorted(glob.glob(os.path.join(res_root, "values-*"))):
                locale = os.path.basename(locale_dir)
                if not LOCALE.match(locale):
                    continue
                extra = names(glob.glob(os.path.join(locale_dir, "*.xml"))) - base
                for key in extra:
                    orphans[(res_root, key, consequence)].append(locale[len("values-"):])
    return orphans


def main():
    orphans = find_orphans()
    if not orphans:
        return 0

    total = sum(len(v) for v in orphans.values())
    out = sys.stderr
    print(
        f"BLOCKED: {total} orphaned translation(s) across {len(orphans)} key(s) — "
        "translated in a locale, absent from that tree's default values/.",
        file=out,
    )
    print(file=out)
    for (res_root, key, consequence), locales in sorted(orphans.items()):
        print(f"  {res_root}: {key!r} in {len(locales)} locale(s) — {consequence}", file=out)
        print(f"      {' '.join(sorted(locales))}", file=out)
    print(file=out)
    print(
        "A key removed or renamed in a default values/strings.xml must be deleted\n"
        "from every values-*/strings.xml in the SAME commit. Crowdin's next sync is\n"
        "not a cleanup step CI waits for — lint runs on the tree you push.\n"
        "See amethyst/src/main/res/AGENTS.md, 'Renaming or removing a string key'.",
        file=out,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
