# Git workflow

## Branches
- `main` is protected in practice: always compiles, `mvn verify` green, no direct pushes after the baseline.
- One branch per piece of work: `m<N>/<topic>` — e.g. `m1/nio-selector`, `m3/multicast-alerts`, `m4/tls-server`.
- Short-lived branches (a few days). Rebase/merge `main` into your branch often.

## Why conflicts stay low
- Each member edits only their own packages (`docs/ownership.md`).
- `ServerApp` and `TrainClientApp` already wire every module and stay headless. The only window is `ControlCenterApp`.
- Cross-module calls go through the frozen interfaces in `common/service`.
- Each member has their own test classes, panel, module doc and plan file.

## Pull requests
1. `mvn verify` passes locally.
2. PR title: `[M<N>] <what>` — e.g. `[M3] Multicast alert publisher + subscriber`.
3. Fill the PR template (concept shown, checklist items, how to test).
4. At least **one reviewer from another member**; shared-contract PRs need **all five**.
5. Squash or merge — the team picks one style and keeps it.

## Commit messages
- Imperative, short: `Add NIO selector accept loop`.
- **No `Co-Authored-By:` lines and no AI attribution lines** (project rule; `.claude/settings.json` disables it for Claude Code).
- Never commit `target/`, IDE files, `certs/*.p12`, passwords or local config overrides.

## Changing a shared contract (`common/**`, `app/**`, `config/**`, `pom.xml`, docs contracts)
1. Open a GitHub issue labelled `contract-change` describing the change and who is affected.
2. Agree in the team chat/meeting.
3. One PR that changes code + `docs/protocol.md`/`docs/configuration.md` together, approved by all five.

## Issues
Each member has one tracking issue (label `member-N`) with their full checklist. Tick items there and in
`docs/plan/member-N-*.md`. Bugs found in someone else's module: open an issue and label the owner — do not fix
their files yourself.
