#!/usr/bin/env python3
"""Download every event signed since SINCE from every relay in RELAYS_FILE, using amy.

usage: crawl.py RELAYS_FILE WORKDIR SINCE KNOWN_KINDS_TSV [WORKERS] [PER_RELAY_TIMEOUT]

One `amy fetch --paginate --limit 0` per relay, WORKERS in parallel, each worker with its own
amy HOME (so no two processes share a SQLite store). After every relay the worker's store is
folded into WORKDIR/census.db (see harvest.py) and deleted: the per-relay stores repeat the same
popular events and their FTS indexes, so keeping them fills the disk within the first few
hundred relays.

Resumable: every finished relay is appended to WORKDIR/crawl_log.jsonl and skipped on restart.
Set AMY to the launcher (default: cli/build/install/amy/bin/amy from `./gradlew :cli:installDist`).
"""
import json, os, queue, re, shutil, subprocess, sys, threading, time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harvest import harvest, load_known, open_census  # noqa: E402

REPO = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
AMY = os.environ.get("AMY", os.path.join(REPO, "cli/build/install/amy/bin/amy"))

relays_file, workdir, since, known_tsv = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4]
workers = int(sys.argv[5]) if len(sys.argv) > 5 else 16
per_relay_timeout = int(sys.argv[6]) if len(sys.argv) > 6 else 300

os.makedirs(workdir, exist_ok=True)
known = load_known(known_tsv)
census_lock = threading.Lock()
census = open_census(os.path.join(workdir, "census.db"))
log_path = os.path.join(workdir, "crawl_log.jsonl")

done = set()
if os.path.exists(log_path):
    for line in open(log_path):
        try:
            done.add(json.loads(line)["relay"])
        except Exception:
            pass

q = queue.Queue()
for r in open(relays_file):
    r = r.strip()
    if r and r not in done:
        q.put(r)
total = q.qsize()
log_lock = threading.Lock()
finished = [0]


def last_error_line(text):
    for line in reversed(text.splitlines()):
        if line.strip().startswith("{"):
            return line.strip()[:600]
    return ""


def worker(i):
    home = os.path.join(workdir, f"w{i:02d}")
    store = os.path.join(home, ".amy", "shared", "events.db")
    # C1-only JIT + serial GC: most runs are short, so startup CPU matters more than peak speed.
    env = dict(os.environ, HOME=home, JAVA_OPTS="-Xmx1g -XX:TieredStopAtLevel=1 -XX:+UseSerialGC")
    while True:
        try:
            relay = q.get_nowait()
        except queue.Empty:
            return
        os.makedirs(home, exist_ok=True)
        t0 = time.time()
        cmd = ["timeout", "-k", "10", str(per_relay_timeout), AMY, "--json", "fetch", "--relay", relay,
               "--since", since, "--paginate", "--limit", "0", "--timeout", "8"]
        out_path = os.path.join(home, "stdout.json")
        with open(out_path, "w") as out:
            p = subprocess.run(cmd, env=env, stdout=out, stderr=subprocess.PIPE, text=True)
        m = re.search(r'"count":(\d+)', open(out_path).read(4096))
        os.remove(out_path)
        # Events are stored as they arrive, so even a run killed by the timeout left its
        # events in the store: harvest regardless of the exit code.
        stored = 0
        if os.path.exists(store):
            with census_lock:
                stored = harvest(census, store, relay, int(since), known)
        shutil.rmtree(os.path.join(home, ".amy", "shared"), ignore_errors=True)
        rec = {"relay": relay, "exit": p.returncode, "count": int(m.group(1)) if m else None,
               "stored": stored, "secs": round(time.time() - t0, 1),
               "err": "" if p.returncode == 0 else last_error_line(p.stderr)}
        with log_lock:
            finished[0] += 1
            with open(log_path, "a") as f:
                f.write(json.dumps(rec) + "\n")
            if finished[0] % 25 == 0:
                print(f"{time.strftime('%H:%M:%S')} {finished[0]}/{total}", flush=True)


threads = [threading.Thread(target=worker, args=(i,)) for i in range(workers)]
for t in threads:
    t.start()
for t in threads:
    t.join()
print("DONE", finished[0], flush=True)
