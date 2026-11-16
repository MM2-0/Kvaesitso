#!/usr/bin/env python3
"""Profile every unsupported kind in census.db and write samples.

usage: profile.py CENSUS_DB KNOWN_TSV OUT_DIR [SAMPLES_PER_KIND] [MAX_SAMPLE_BYTES]
"""
import collections, json, os, re, sqlite3, sys

db, known_tsv, out = sys.argv[1], sys.argv[2], sys.argv[3]
per_kind = int(sys.argv[4]) if len(sys.argv) > 4 else 20
max_bytes = int(sys.argv[5]) if len(sys.argv) > 5 else 400_000

known = {}
for l in open(known_tsv):
    p = l.rstrip("\n").split("\t")
    known[int(p[0])] = p[2] if len(p) > 2 else ""

c = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
os.makedirs(os.path.join(out, "samples"), exist_ok=True)


# Invisible / bidi codepoints are written as \\uXXXX escapes (identical once parsed) so the
# sample files stay visually unambiguous on disk (repo rule; see AGENTS.md, Kotlin Style).
INVISIBLE = re.compile("[\u200b-\u200f\u202a-\u202e\u2060\u2066-\u2069\u061c\ufeff]")


def escape_invisible(s):
    return INVISIBLE.sub(lambda m: "\\u%04x" % ord(m.group()), s)


def kind_class(k):
    if k in (0, 3) or 10000 <= k < 20000:
        return "replaceable"
    if 20000 <= k < 30000:
        return "ephemeral"
    if 30000 <= k < 40000:
        return "addressable"
    return "regular"


def shape(content):
    s = content.strip()
    if not s:
        return "empty"
    if s[0] in "{[":
        try:
            json.loads(s)
            return "json"
        except Exception:
            return "json-ish"
    if "?iv=" in s:
        return "nip04"
    if re.fullmatch(r"[A-Za-z0-9+/=_\-]{40,}", s):
        return "nip44" if s.startswith("A") and len(s) % 4 == 0 else "opaque-b64"
    if re.fullmatch(r"[0-9a-f]{64}(:\S*)?", s):
        return "hex"
    return "text"


# all kinds histogram
hist = c.execute("select kind,count(*),count(distinct pubkey) from ev group by kind").fetchall()
with open(os.path.join(out, "kind_histogram.tsv"), "w") as h:
    h.write("kind\tevents\tauthors\ttyped_in_quartz\tquartz_label\n")
    for k, n, a in sorted(hist, key=lambda r: -r[1]):
        h.write(f"{k}\t{n}\t{a}\t{'yes' if k in known else 'NO'}\t{known.get(k, '')}\n")

profiles = []
for k, n, a in sorted((r for r in hist if r[0] not in known), key=lambda r: (-r[2], -r[1])):
    evs = [json.loads(j) for (j,) in c.execute("select json from raw where kind=?", (k,))]
    tagnames, shapes, alts, clients, content_keys = (collections.Counter() for _ in range(5))
    for e in evs:
        names = set()
        for t in e["tags"]:
            if t:
                names.add(t[0])
                if t[0] == "alt" and len(t) > 1:
                    alts[t[1][:100]] += 1
                if t[0] == "client" and len(t) > 1:
                    clients[t[1][:40]] += 1
        tagnames.update(names)
        sh = shape(e["content"])
        shapes[sh] += 1
        if sh == "json":
            try:
                v = json.loads(e["content"])
                if isinstance(v, dict):
                    content_keys.update(list(v)[:30])
            except Exception:
                pass
    # samples: round-robin over authors, newest first, bounded size
    by_author = collections.defaultdict(list)
    for e in sorted(evs, key=lambda e: -e["created_at"]):
        by_author[e["pubkey"]].append(e)
    picked, size = [], 0
    while len(picked) < per_kind and any(by_author.values()):
        for au in list(by_author):
            if by_author[au] and len(picked) < per_kind:
                e = by_author[au].pop(0)
                line = escape_invisible(json.dumps(e, ensure_ascii=False))
                if picked and size + len(line) > max_bytes:
                    by_author[au] = []
                    continue
                picked.append(line)
                size += len(line)
    with open(os.path.join(out, "samples", f"kind-{k}.jsonl"), "w") as f:
        f.write("\n".join(picked) + "\n")
    relays = c.execute(
        "select count(distinct relay) from relay_kind where kind=? and relay not like '(pre-%'", (k,)).fetchone()[0]
    profiles.append({
        "kind": k, "class": kind_class(k), "events": n, "authors": a,
        "tag_presence": dict(tagnames.most_common(15)),
        "content_shape": dict(shapes.most_common()),
        "json_content_keys": dict(content_keys.most_common(15)),
        "alt": dict(alts.most_common(3)), "client": dict(clients.most_common(5)),
        "samples": len(picked),
    })

with open(os.path.join(out, "unsupported_kinds.json"), "w") as f:
    json.dump(profiles, f, indent=1, ensure_ascii=False)
with open(os.path.join(out, "unsupported_kinds.tsv"), "w") as u:
    u.write("kind\tclass\tevents\tauthors\ttags\tcontent\tclient\talt\n")
    for p in profiles:
        u.write("\t".join(str(x) for x in [
            p["kind"], p["class"], p["events"], p["authors"],
            ",".join(f"{t}" for t in list(p["tag_presence"])[:10]),
            ",".join(f"{s}:{n}" for s, n in p["content_shape"].items()),
            ",".join(p["client"]), "|".join(p["alt"])]) + "\n")
print(len(hist), "kinds;", len(profiles), "unsupported")
