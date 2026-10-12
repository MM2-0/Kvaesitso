# Intelia: shared agent instructions

Applies to Codex, Claude Code and human contributors. Intelia is an Android
launcher derived from Kvaesitso, with its own product direction. Preserve upstream
attribution, licenses, history and existing fixes.

## Read only what the task needs

1. Read [STATUS](docs/intelia/STATUS.md), then the selected file in `tasks/`.
2. Consult [ARCHITECTURE](docs/intelia/ARCHITECTURE.md) for touched boundaries.
3. Read PRODUCT, DESIGN or PERFORMANCE only when relevant. WORKFLOW contains
   environment commands and handoff rules; DECISIONS records accepted tradeoffs.
4. Inspect nearby source before editing. Repository facts outrank stale notes;
   update the affected note rather than repeating a repository-wide audit.

## Work safely and autonomously

- Use a focused branch; never reset or commit directly to `main`. Check Git status
  first and preserve changes you did not create. Cloud checkouts are already
  isolated; create a worktree only when the user explicitly requests one.
- Make the smallest complete change. Do not mix feature work with rebranding,
  dependency upgrades, broad refactors or cleanup. Bootstrap documentation must
  leave application source and behavior unchanged.
- Reuse the existing Kotlin, Compose, Koin, repository and preferences patterns.
  Respect profile/hidden-app boundaries, lifecycle cancellation and error paths.
- FLOW must work offline, show what will happen and run only on explicit user
  activation. AI proposals never execute themselves. No new services, permissions,
  dependencies, Accessibility automation or overlays without an explicit scope.
- Preserve `LICENSE.txt`, module licenses, copyright headers and notices. Existing
  package IDs and namespaces are not a mandate to rebrand them during a task.
- Never commit credentials, keystores, machine-only helper files, local configuration or build
  outputs. Keep TLS/signature/checksum verification enabled. Check credential
  presence only; use supplied Git authentication before asking for new access.
- Decide routine implementation details yourself. Ask only for missing access,
  irreversible actions or an essential product decision. Commit and open a PR
  when authorized; never merge without explicit authorization.

## Verification and handoff

- Run the relevant existing tests and build target from WORKFLOW. New behavior
  needs tests of its contract, including cancellation/failure cases where relevant.
- Preserve exit codes; distinguish executed, cached, failed, skipped and unrun
  checks. A successful APK build is not a device or performance test.
- Update STATUS and the active task with exact commands, results, remaining work
  and affected paths. Record significant decisions once in DECISIONS.
- Default to one coordinating agent. Delegate only independent, bounded work
  when permitted and useful; give each agent separate file ownership. One agent
  integrates, validates and prepares the PR. Never assume shared chat memory.
- End with the PR link (or precise creation blocker), verified results and the
  next task. Keep logs and large audits out of handoffs.
