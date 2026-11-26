# CLAUDE.md — SmartRail

@AGENTS.md

## Claude Code specifics

- **First ask which member / concept the user is working on** if it is not obvious from the branch name
  (`m1/…` … `m5/…`) or the request, then stay inside that member's files (AGENTS.md §2, `docs/ownership.md`).
- Member-scoped subagents live in `.claude/agents/` (`m1-nio-server`, `m2-tcp-client`, `m3-udp-multicast`,
  `m4-tls-urlconnection`, `m5-rmi-handlers`). Prefer delegating module work to the matching agent when the
  user asks for an agent.
- Read the member's plan (`docs/plan/member-N-*.md`) before implementing; tick checklist items you finish.
- Read the relevant contract before coding against it: `docs/protocol.md` (wire formats),
  `docs/configuration.md` (keys), `common/service/*` (interfaces).
- For any `ui/**` work, read `docs/ui/control-center-spec.md` (what each screen must show and who owns it)
  and `docs/ui/design-system.md` (theme tokens and shared components) first. The UI is one window whose
  buttons trigger real network events — every widget must visualise one, and nothing is decorative.
- The only allowed attribution in commits/PRs is none: **no `Co-Authored-By`, no "Generated with Claude Code"**.
  `.claude/settings.json` disables Claude Code's automatic attribution; do not re-add it manually.
- Do not "improve" the architecture, add frameworks, add files or refactor shared contracts on your own
  initiative. Suggest it to the user instead and wait for an explicit request.
- When a requirement is ambiguous, prefer the simplest implementation that clearly demonstrates the
  networking API in a 2-minute presentation.

## Quick orientation

```
config/                         all ports/hosts/timeouts (smartrail.properties) + railway model
src/main/java/com/smartrail/
  common/   SHARED, frozen      config · protocol · model · service (interfaces) · resource · log
  app/      SHARED launchers    ServerApp · TrainClientApp (headless) · ControlCenterApp (the one window, M3)
  nio/ coordination/            Member 1  Java NIO server + coordination
  tcpclient/                    Member 2  TCP client + InetAddress
  udp/ multicast/ heartbeat/    Member 3  UDP · multicast · heartbeat
  tls/ urlconnection/           Member 4  TLS · URL/URLConnection
  rmi/ protocolhandler/ contenthandler/   Member 5  RMI · smartrail:// · content handlers
  ui/shell · ui/theme · ui/components (M2, except UiThreads) · ui/panels (one screen per member)
docs/plan/                      phase checklists per member
docs/ui/                        Control Center spec · design system · demo flow
```

The demo runs from one window: `scripts/run.sh controlcenter`. It hosts the server services
in-process and spawns each train as a real child JVM (`TrainProcessSupervisor`), so no terminal is
needed during a demonstration. The older `server` / `train` / `controller` launchers still work for
a multi-machine LAN run.
