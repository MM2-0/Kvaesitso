#!/usr/bin/env python3
"""Render data/<date>/findings.json as FINDINGS.md. usage: findings_md.py DATA_DIR"""
import json, os, sys

d = sys.argv[1]
f = json.load(open(os.path.join(d, "findings.json")))
order = {"render-worthy": 0, "protocol-infra": 1, "app-private": 2, "spam/noise": 3}
out = ["# Unsupported kinds: findings", "",
       "Generated from `findings.json` by `findings_md.py`. Kinds with 3+ distinct authors in the "
       "window, grouped by relevance to Amethyst, then by author count. Samples: `samples/kind-<K>.jsonl`.",
       "Author counts overstate real users where an app signs with throwaway keys (noted per kind).", ""]


def cell(s):
    return (s or "").replace("|", "\\|").replace("\n", " ")


for rel in sorted({x["amethyst_relevance"] for x in f["identified"]}, key=lambda r: order.get(r, 9)):
    rows = [x for x in f["identified"] if x["amethyst_relevance"] == rel]
    out += [f"## {rel} ({len(rows)})", "", "| kind | class | authors | events | name | app / protocol | conf | spec |",
            "|---|---|---|---|---|---|---|---|"]
    for x in sorted(rows, key=lambda x: -(x["authors"] or 0)):
        spec = f"[link]({x['spec_url']})" if x.get("spec_url") else ""
        out.append(f"| {x['kind']} | {x['class']} | {x['authors']} | {x['events']} | {cell(x['name'])} | "
                   f"{cell(x['app_or_protocol'])} | {x['confidence']} | {spec} |")
    out.append("")
    for x in sorted(rows, key=lambda x: -(x["authors"] or 0)):
        out.append(f"- **{x['kind']} {cell(x['name'])}** — {cell(x['what_it_is'])}"
                   + (f" _Notes:_ {cell(x['notes'])}" if x.get("notes") else ""))
    out.append("")

lt = f["long_tail"]
out += ["## Long tail (1-2 authors per kind)", "", "### Notable", "",
        "| kind | name | app / protocol | relevance | conf |", "|---|---|---|---|---|"]
for x in lt["notable"]:
    out.append(f"| {x['kind']} | {cell(x['name'])} | {cell(x.get('app_or_protocol'))} | "
               f"{x.get('amethyst_relevance', '')} | {x.get('confidence', '')} |")
out += ["", "### Clusters", "", "| label | kinds | relevance | what it is |", "|---|---|---|---|"]
for c in lt["clusters"]:
    out.append(f"| {cell(c['label'])} | {', '.join(map(str, c['kinds']))} | {c['relevance']} | {cell(c['what_it_is'])} |")
open(os.path.join(d, "FINDINGS.md"), "w").write("\n".join(out) + "\n")
print("wrote", os.path.join(d, "FINDINGS.md"))
