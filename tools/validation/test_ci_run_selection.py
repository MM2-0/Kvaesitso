"""Safety boundaries for automatic cancellation of obsolete CI runs."""
import importlib.util
from pathlib import Path
import unittest

source = Path(__file__).resolve().parents[2] / ".github/scripts/cancel_obsolete_ci.py"
spec = importlib.util.spec_from_file_location("ci_cleanup", source)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RunSelectionTest(unittest.TestCase):
    def reason(self, event="push", branch="main", sha="old", pulls=(), associated=()):
        run = dict(event=event, head_branch=branch, head_sha=sha, pull_requests=list(associated))
        if event == "dynamic":
            run["path"] = "dynamic/dependency-graph/auto-submission"
        return module.cancellation_reason(run, "main", "current", list(pulls),
                                          lambda old, new: old == "old" and new == "current")

    def test_current_main_is_preserved(self):
        self.assertIsNone(self.reason(sha="current"))

    def test_older_main_and_managed_submission_are_cancelled(self):
        self.assertIsNotNone(self.reason())
        self.assertIsNotNone(self.reason(event="dynamic"))

    def test_newer_or_diverged_history_is_preserved(self):
        self.assertIsNone(self.reason(sha="newer"))

    def test_releases_schedules_and_manual_runs_are_preserved(self):
        self.assertIsNone(self.reason(branch="v1.45.71"))
        self.assertIsNone(self.reason(event="workflow_dispatch"))
        self.assertIsNone(self.reason(event="schedule"))

    def test_closed_pull_request_is_cancelled(self):
        self.assertIsNotNone(self.reason(event="pull_request", associated=[{"number": 1}],
                                         pulls=[{"number": 1, "state": "closed"}]))

    def test_open_or_unknown_pull_request_is_preserved(self):
        self.assertIsNone(self.reason(event="pull_request", associated=[{"number": 1}],
                                      pulls=[{"number": 1, "state": "open"}]))
        self.assertIsNone(self.reason(event="pull_request", associated=[{"number": 2}]))

    def test_same_commit_on_an_open_pr_protects_a_run_without_association(self):
        self.assertIsNone(self.reason(event="pull_request", pulls=[
            {"number": 1, "state": "closed", "head": {"sha": "old"}},
            {"number": 2, "state": "open", "head": {"sha": "old"}},
        ]))


if __name__ == "__main__":
    unittest.main()
