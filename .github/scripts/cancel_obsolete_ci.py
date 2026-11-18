"""Cancel obsolete checks without touching releases, manual runs or current main."""
import json
import os
import subprocess
import sys
from functools import lru_cache


def cancellation_reason(run, default_branch, latest_sha, pulls, is_ancestor):
    if run.get("event") == "pull_request":
        numbers = {p["number"] for p in run.get("pull_requests", [])}
        if numbers:
            candidates = [p for p in pulls if p["number"] in numbers]
            if len(candidates) != len(numbers):
                return None
        else:
            candidates = [p for p in pulls if run.get("head_sha") in
                          (p.get("head", {}).get("sha"), p.get("merge_commit_sha"))]
        if candidates and all(p["state"] == "closed" for p in candidates):
            return "pull request is already closed or merged"
    elif (run.get("head_branch") == default_branch
          and (run.get("event") == "push" or run.get("path", "").startswith("dynamic/dependency-graph/"))
          and run.get("head_sha") and run["head_sha"] != latest_sha
          and is_ancestor(run["head_sha"], latest_sha)):
        return "older default-branch commit superseded by current main"
    return None


def main():
    repo = os.environ["GITHUB_REPOSITORY"]

    def api(path, method="GET"):
        result = subprocess.run(
            ["gh", "api", "--method", method, f"repos/{repo}" + (f"/{path}" if path else "")],
            capture_output=True, text=True, check=True,
        )
        return json.loads(result.stdout) if result.stdout.strip() else {}

    def pages(path, key=None):
        result = []
        for page in range(1, 11):
            data = api(path + ("&" if "?" in path else "?") + f"per_page=100&page={page}")
            items = data[key] if key else data
            result.extend(items)
            if len(items) < 100:
                break
        return result

    @lru_cache(maxsize=100)
    def is_ancestor(old, new):
        try:
            return api(f"compare/{old}...{new}").get("merge_base_commit", {}).get("sha") == old
        except subprocess.CalledProcessError:
            # Unknown/diverged history is not sufficient evidence to cancel a run.
            return False

    branch = api("")["default_branch"]
    latest = api(f"commits/{branch}")["sha"]
    pulls = pages("pulls?state=all")
    runs = {}
    for status in ("in_progress", "queued", "pending", "waiting"):
        runs.update({r["id"]: r for r in pages(f"actions/runs?status={status}", "workflow_runs")})
    cancelled = []
    failures = []
    for run in runs.values():
        if str(run["id"]) == os.environ.get("GITHUB_RUN_ID"):
            continue
        reason = cancellation_reason(run, branch, latest, pulls, is_ancestor)
        if not reason:
            continue
        if "--dry-run" not in sys.argv:
            try:
                api(f"actions/runs/{run['id']}/cancel", "POST")
            except subprocess.CalledProcessError as error:
                # Completion between listing and cancelling is an expected race.
                if any(code in error.stderr for code in ("HTTP 404", "HTTP 409", "HTTP 422")):
                    continue
                failures.append(run["id"])
                continue
        cancelled.append((run["id"], reason))
    summary = {"cancelled": cancelled, "failed": failures, "current_main": latest}
    message = json.dumps(summary).replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
    print("::notice title=Obsolete CI cleanup::" + message)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as output:
            output.write(f"Kept current `{branch}` at `{latest}`. Cancelled {len(cancelled)} obsolete runs.\n")
            for run_id, reason in cancelled:
                output.write(f"- Run {run_id}: {reason}\n")
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
