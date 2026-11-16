#!/usr/bin/env python3
"""Record real answers from a live vespa-relay so the local search engine can be tested
against them offline.

Run by hand when the fixture needs refreshing; the test that consumes its output never
touches the network, so `./gradlew test` stays hermetic and offline.

    ./gradlew :cli:installDist
    tools/search-parity/fetch_fixtures.py > tools/search-parity/fixture.json

Each case pins the filter FIELDS (kinds/tags/window) as flags rather than folding them into
the search string, so the test can rebuild the identical NIP-01 Filter and assert our matcher
agrees with the relay about every event it chose to return.
"""
import json
import os
import shlex
import subprocess
import sys

RELAY = os.environ.get("RELAY", "wss://search.brainstorm.world")
AMY = os.environ.get("AMY", os.path.join(os.getcwd(), "cli/build/install/amy/bin/amy"))
# The relay gates and ranks results through the searcher's web of trust, and answers an
# anonymous query with nothing at all. `include:spam` waives that gate, which is what makes
# this fixture reproducible: the alternative, `observer:<pubkey>`, ties every recorded answer
# to one account's trust graph and re-records differently as that graph moves.
#
# It does NOT make the relay lexical. Measured on this corpus, results still include events
# carrying no literal occurrence of the term — asked for `"open source"` it returns "FOSS you say
# will save you". Retrieval is semantic; see the parity test for why that means text results are
# not compared.
LENS = os.environ.get("LENS", "include:spam")
LIMIT = os.environ.get("LIMIT", "8")
TIMEOUT = os.environ.get("TIMEOUT", "30")

# name, search terms, filter flags
CASES = [
    ("text_single", "bitcoin", ["--kind", "1"]),
    ("text_two_words", "bitcoin lightning", ["--kind", "1"]),
    ("text_longform", "nostr", ["--kind", "30023"]),
    ("tag_hashtag", "bitcoin", ["--kind", "1", "--tag", "t=bitcoin"]),
    ("window_since", "nostr", ["--kind", "1", "--since", "1700000000"]),
    ("window_until", "nostr", ["--kind", "1", "--until", "1800000000"]),
    ("kinds_union", "nostr", ["--kind", "1,30023"]),
    ("phrase_quoted", '"open source"', ["--kind", "1"]),
]


def fetch(terms, flags):
    search = f"{terms} {LENS}"
    cmd = [AMY, "fetch", *flags, "--search", search, "--limit", LIMIT,
           "--relay", RELAY, "--timeout", TIMEOUT, "--json"]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=int(TIMEOUT) + 30)
        return search, json.loads(out.stdout).get("events", [])
    except Exception as e:  # a relay that is down must not silently produce an empty fixture
        print(f"FAILED {' '.join(shlex.quote(c) for c in cmd)}: {e}", file=sys.stderr)
        raise


def main():
    if not os.access(AMY, os.X_OK):
        sys.exit("amy not built: ./gradlew :cli:installDist")
    cases = []
    for name, terms, flags in CASES:
        search, events = fetch(terms, flags)
        print(f"{name:16s} {len(events):3d} events", file=sys.stderr)
        cases.append({"name": name, "terms": terms, "flags": flags,
                      "search": search, "events": events})
    json.dump({"relay": RELAY, "lens": LENS, "cases": cases},
              sys.stdout, indent=1, sort_keys=True)
    print()


if __name__ == "__main__":
    main()
