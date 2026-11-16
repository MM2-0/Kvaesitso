#!/usr/bin/env python3
"""Move string keys from the Android app's res/ to commons Compose resources.

Usage: tools/strings-migrate/migrate.py key1 key2 ...
       tools/strings-migrate/migrate.py @keylist.txt   (one key per line)

For the default locale and every values-* locale dir, each named
<string> or <plurals> element is removed from
  amethyst/src/main/res/<values*>/strings.xml
and appended (before </resources>) to
  commonsUI/src/commonMain/composeResources/<values*>/strings.xml
creating the commons locale file when a translation exists that commons
doesn't carry yet. Elements are moved verbatim (raw XML slice), so
escaping, CDATA and comments inside values survive byte-for-byte and
Crowdin sees a pure move.

Fails loudly when a key's default-locale value uses non-positional format
specifiers (bare %s / %d): compose-resources only formats the positional
%1$s form, so those keys must be rewritten (in code AND all locales)
before migrating. Run from the repo root.
"""
import os
import re
import sys

from fix_escapes import STRING_EL, fix_text, strip_android_only_attrs

APP_RES = "amethyst/src/main/res"
COMMONS_RES = "commonsUI/src/commonMain/composeResources"

NEW_FILE_TEMPLATE = '<?xml version="1.0" encoding="utf-8"?>\n<resources>\n</resources>\n'

# %s, %d, %.2f ... without a position (%1$s). The leading `%%` alternative is
# what makes this correct: a literal percent must be consumed as a unit, or the
# SECOND `%` of `%1$d%% of all` starts a fresh match and the space flag in
# `[-#+ 0,(]*` lets `% o` look like a bare conversion. Callers test
# `is_bare(text)`, not `.search()`, because a `%%` match is not a finding.
FORMAT_SPEC = re.compile(r"%%|%(?!\d+\$)[-#+ 0,(]*\d*(?:\.\d+)?[a-zA-Z]")


def is_bare(text: str) -> bool:
    """True when text uses a non-positional conversion (bare %s / %d)."""
    return any(m.group(0) != "%%" for m in FORMAT_SPEC.finditer(text))


def element_pattern(key: str) -> re.Pattern:
    # `name` is not always the first attribute (Crowdin emits e.g.
    # `<string xmlns:ns0="..." name="key" ns0:ignore="Typos">`), so allow
    # any attributes before it.
    return re.compile(
        r"[ \t]*<(string|plurals)\b[^>]*?\sname=\"" + re.escape(key) + r"\"[^>]*?(?:/>|>.*?</\1>)[ \t]*\n?",
        re.S,
    )


# Name-agnostic twin of element_pattern(), used by the bulk path: one scan per
# file instead of one per (key, file). Spans match element_pattern's exactly -
# `name` is unique within a tag, so the lazy `[^>]*?` lands in the same place.
ANY_ELEMENT = re.compile(
    r"[ \t]*<(string|plurals)\b[^>]*?\sname=\"([^\"]+)\"[^>]*?(?:/>|>.*?</\1>)[ \t]*\n?",
    re.S,
)


def extract_many(path: str, keys: list):
    """Remove every element in `keys` from `path` in ONE pass.

    Returns them ordered by `keys`, matching what repeated extract() calls
    produced, so the appended block is byte-identical to the slow path.
    """
    if not os.path.exists(path):
        return []
    src = open(path, encoding="utf-8").read()
    wanted = set(keys)
    found = {}
    out = []
    last = 0
    for m in ANY_ELEMENT.finditer(src):
        if m.group(2) not in wanted or m.group(2) in found:
            continue
        found[m.group(2)] = m.group(0).strip("\n")
        out.append(src[last : m.start()])
        last = m.end()
    if not found:
        return []
    out.append(src[last:])
    open(path, "w", encoding="utf-8").write("".join(out))
    return [found[k] for k in keys if k in found]


def extract(path: str, key: str):
    """Remove key's element from path; return the element text or None."""
    if not os.path.exists(path):
        return None
    src = open(path, encoding="utf-8").read()
    m = element_pattern(key).search(src)
    if not m:
        return None
    open(path, "w", encoding="utf-8").write(src[: m.start()] + src[m.end() :])
    return m.group(0).strip("\n")


def convert_escaping(element: str) -> str:
    """Rewrite Android-only escaping that Compose resources do not understand.

    Compose resolves \\uXXXX, \\n and \\t itself but leaves \\', \\", \\? and \\@
    alone, and renders Android's quote-wrapping literally. Moving an element verbatim
    therefore shipped `Don\\'t have a Nostr account?` to the login screen. See
    fix_escapes.py, which repairs files already migrated.
    """
    element = STRING_EL.sub(lambda m: m.group(1) + fix_text(m.group(2)) + m.group(3), element)
    return strip_android_only_attrs(element)[0]


def append(path: str, elements: list):
    if os.path.exists(path):
        src = open(path, encoding="utf-8").read()
    else:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        src = NEW_FILE_TEMPLATE
    close = src.rindex("</resources>")
    block = "".join("    " + convert_escaping(el.strip()) + "\n" for el in elements)
    open(path, "w", encoding="utf-8").write(src[:close] + block + src[close:])


def main(keys):
    default_src = os.path.join(APP_RES, "values", "strings.xml")
    default_text = open(default_src, encoding="utf-8").read()

    for key in keys:
        m = element_pattern(key).search(default_text)
        if not m:
            sys.exit(f"ERROR: key '{key}' not found in {default_src}")
        if is_bare(m.group(0)):
            sys.exit(
                f"ERROR: key '{key}' uses a non-positional format specifier (bare %s/%d).\n"
                "compose-resources only formats %1$s-style args - rewrite the key "
                "(code + every locale) before migrating."
            )

    locale_dirs = ["values"] + sorted(
        d for d in os.listdir(APP_RES) if d.startswith("values-") and os.path.exists(os.path.join(APP_RES, d, "strings.xml"))
    )

    moved_total = 0
    for d in locale_dirs:
        src_path = os.path.join(APP_RES, d, "strings.xml")
        elements = extract_many(src_path, keys)
        if elements:
            append(os.path.join(COMMONS_RES, d, "strings.xml"), elements)
            moved_total += len(elements)
            print(f"{d}: moved {len(elements)}")
    print(f"done: {moved_total} elements across {len(locale_dirs)} locale dirs")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    args = sys.argv[1:]
    if len(args) == 1 and args[0].startswith("@"):
        args = open(args[0][1:], encoding="utf-8").read().split()
    main(args)
