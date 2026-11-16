"""Fold one amy store (events.db) into the compact census DB, then the caller can delete it.

census.db keeps:
  ev(id, kind, pubkey, created_at)            every distinct event seen (all kinds)
  raw(id, kind, json)                         full signed JSON, only for kinds quartz has no typed class for
  relay_kind(relay, kind, events)             how many events of each kind each relay served
"""
import json, sqlite3

SCHEMA = """
create table if not exists ev(id text primary key, kind int, pubkey text, created_at int) without rowid;
create table if not exists raw(id text primary key, kind int, json text) without rowid;
create table if not exists relay_kind(relay text, kind int, events int, primary key(relay, kind)) without rowid;
"""


def open_census(path):
    # check_same_thread=False: crawl.py shares one connection across workers behind a lock.
    c = sqlite3.connect(path, timeout=120, check_same_thread=False)
    c.executescript(SCHEMA)
    c.execute("pragma journal_mode=wal")
    c.execute("pragma synchronous=normal")
    return c


def harvest(census, store_path, relay, since, known):
    src = sqlite3.connect(f"file:{store_path}?mode=ro", uri=True, timeout=60)
    rows = src.execute(
        "select id,pubkey,created_at,kind,tags,content,sig from event_headers where created_at >= ?", (since,)
    ).fetchall()
    src.close()
    per_kind = {}
    ev_rows, raw_rows = [], []
    for id_, pk, ca, kind, tags, content, sig in rows:
        per_kind[kind] = per_kind.get(kind, 0) + 1
        ev_rows.append((id_, kind, pk, ca))
        if kind not in known:
            raw_rows.append((id_, kind, json.dumps(
                {"id": id_, "pubkey": pk, "created_at": ca, "kind": kind,
                 "tags": json.loads(tags), "content": content, "sig": sig}, ensure_ascii=False)))
    with census:
        census.executemany("insert or ignore into ev values (?,?,?,?)", ev_rows)
        census.executemany("insert or ignore into raw values (?,?,?)", raw_rows)
        census.executemany(
            "insert into relay_kind values (?,?,?) on conflict(relay,kind) do update set events=max(events, excluded.events)",
            [(relay, k, n) for k, n in per_kind.items()])
    return len(rows)


def load_known(tsv):
    return {int(l.split("\t")[0]) for l in open(tsv) if l.strip()}
