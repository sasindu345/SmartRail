## Member / concept
Member: M_ — Concept(s): <!-- e.g. Java NIO Selector, UDP multicast, TLS -->

## What changed
-

## Checklist items completed
<!-- copy from docs/plan/member-N-*.md -->
- [ ]

## Rules
- [ ] Only my own files changed (docs/ownership.md) — or this is an agreed contract change (all 5 approve)
- [ ] No new/moved/renamed files or packages
- [ ] Nothing hard-coded (ports/hosts/timeouts/IDs/paths via config; secrets via env vars)
- [ ] No new dependencies or frameworks
- [ ] Network work is off the Swing EDT
- [ ] `mvn verify` passes

## How to test
```bash
mvn verify
scripts/run.sh ...
```
