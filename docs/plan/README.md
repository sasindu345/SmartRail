# SmartRail implementation plan

Five members work **in parallel** from the same baseline. Every member owns separate packages, codes against the
frozen interfaces in `common/service`, and tests in isolation with `testsupport` fakes until integration.

| Member | Plan & checklist | Concepts |
|---|---|---|
| 1 | [member-1-nio-server.md](member-1-nio-server.md) | Java NIO server, TCP framing, coordination |
| 2 | [member-2-tcp-client.md](member-2-tcp-client.md) | TCP client + I/O streams, IP addressing/InetAddress/ports, app shell |
| 3 | [member-3-udp-multicast-heartbeat.md](member-3-udp-multicast-heartbeat.md) | UDP discovery, multicast alerts, heartbeat |
| 4 | [member-4-tls-urlconnection.md](member-4-tls-urlconnection.md) | TLS, URL/URLConnection + HTTP resources |
| 5 | [member-5-rmi-handlers.md](member-5-rmi-handlers.md) | RMI, custom protocol handler, content handlers |

## Phases (same for everyone, time-boxed)

| Phase | Suggested time | Goal | Exit condition (team) |
|---|---|---|---|
| **0 — Setup & freeze** | days 1–2 | Everyone builds the baseline, reads the contracts, creates their branch | all 5 run `mvn verify` green; contracts frozen |
| **1 — Concept core** | week 1 | Each member gets their *networking API* working in isolation (tests / small manual run) | each core demo works alone |
| **2 — Module complete** | week 2 | Full module behaviour, error handling, unit tests (fakes) | each member's tests pass, PR merged |
| **3 — Integration** | week 3 | Real end-to-end through `ServerApp` / `TrainClientApp` and the Control Center | integration checkpoints C1–C8 pass |
| **4 — Evidence** | week 4 | Screenshots/logs for the report (UI screens are tracked in the separate UI issues) | every feature evidenced |
| **5 — Test, docs, demo** | week 5 | Test plan executed on a real LAN, module docs, report section, rehearsal | 10-minute demo runs twice without restart |

### Control Center UI track (separate issues, runs alongside)

The demo is driven from **one window** whose buttons trigger real network events; trains are launched
as real child JVMs. Spec: [`docs/ui/control-center-spec.md`](../ui/control-center-spec.md), visual
rules: [`design-system.md`](../ui/design-system.md), demo: [`demo-flow.md`](../ui/demo-flow.md).

| Issue | Owner | Scope | Depends on |
|---|---|---|---|
| **UI-0** Control Center shell | M3 | window, FlatLaf theme, shared components, `TrainProcessSupervisor`, `SimulationController`, control + status bars | — **blocks all others** |
| **UI-1** Overview, Track Sections, Event Log | M1 | network map, train table, selected-train card, sections table, conflict demo, log view | UI-0 |
| **UI-2** Trains screen | M2 | train table + per-train child-process consoles, start/stop train | UI-0 |
| **UI-3** Alerts & Network | M3 | alert feed, subscribe, trigger obstruction, discovery, services & ports | UI-0, PR #7 |
| **UI-4** Security & Resources | M4 | TLS session details, untrusted-cert test, URLConnection fetches | UI-0 |
| **UI-5** Administration | M5 | restyle `RmiHandlerPanel` into the shell (RMI + smartrail://) | UI-0 |

Rule: **no member starts their UI issue before their networking concept passes its tests.**

Merge to `main` at least once per phase — integrate continuously, never at the end.

## Integration checkpoints (Phase 3)

| ID | Checkpoint | Members | Check |
|---|---|---|---|
| C1 | `RailwayServiceImpl` + router merged (replaces fakes) | 1 | server starts; `/api/trains` via curl not needed yet |
| C2 | Train registers over plain NIO channel | 1 + 2 | `REGISTER_OK` + `ASSIGN_ROUTE` in the train console |
| C3 | Same train over TLS | 4 + 2 (+1 router) | `-Dclient.transport=TLS` works; untrusted cert rejected |
| C4 | Discovery fills the train's host/port | 3 + 2 | Discover button → connect without typing IP |
| C5 | Heartbeat → INACTIVE | 3 + 1 | kill a train → INACTIVE after `heartbeat.timeout.ms` |
| C6 | RMI hold reaches the train | 5 + 1 + 2 | `requestSimulatedHold(TR02)` → TR02 window shows HELD |
| C7 | Obstruction alert reaches all trains | 5 + 1 + 3 | `reportObstruction(SEC_B)` → 2+ trains show alert |
| C8 | Typed resources | 4 + 5 | `ResourceClient.fetchContent("tracks")` and `smartrail://…/trains/TR01` return typed objects |
| C9 | Control Center starts the services | 2 (+1) | `Start Server` turns every status dot green; real ports bound |
| C10 | Trains launch from buttons | 2 | `+TR01` spawns a real child JVM; its console streams into the window |
| C11 | One alert, three consoles | 3 + 2 | `Trigger obstruction` appears in all running train consoles at once |

## Dependency rules (how parallel work stays unblocked)
- Before C1, Members 3/4/5 use `FakeRailwayService`; Member 4's TLS server can use a fake `MessageHandler` in tests.
- Member 2 can test `TrainClient` against a tiny `ServerSocket` in the test or against `nc -l <port>`.
- Member 1 can test the server with `nc localhost 5000` (type protocol lines by hand) before Member 2 is ready.
- Nobody edits `ServerApp`/`TrainClientApp` except to keep them headless. `ControlCenterApp` is M3's,
  and it is the only window.

## Workload balance (target ≈ 20% each)

Effort points per member (1 point ≈ half a day). Every member also carries panel, tests, docs and a 2-minute demo.

| Work | M1 | M2 | M3 | M4 | M5 |
|---|---|---|---|---|---|
| Core networking concept(s) | NIO server 5, framing 2 | TCP client 4, InetAddress 3 | UDP discovery 4, multicast 4 | TLS 4 + certs 2, URLConnection 3 | RMI 5, protocol handler 4 |
| Second responsibility | router 3, reservations/registry/routes 4 | simulator 3, reconnect/errors 2 | heartbeat 3, unicast fallback 1 | HTTP endpoints 3, TLS client 1 | resource server 2, content handlers 3 |
| Tests + docs + demo | 3 | 3 | 3 | 3 | 3 |
| Integration support | 1 | 2 | 2 | 1 | 1 |
| **Backend subtotal** | **18** | **17** | **17** | **17** | **18** |
| Control Center UI issue | 4 (UI-1) | 2 (UI-2) | 9 (UI-0 + UI-3) | 3 (UI-4) | 2 (UI-5) |
| Shared docs / demo / evidence upkeep | 1 | 3 | 0 | 2 | 3 |
| **Total** | **23** | **22** | **26** | **22** | **23** |

**M3 owns UI-0** (agreed 2026-11-20): their backend is already finished while M1/M2/M4 have not started, and
the shell blocks every other screen, so it goes to whoever can land it now. That leaves M3 ~3 points above
average, so all shared docs, demo-script and evidence duties move off M3 to M2/M4/M5. If M3 is still heavy
once UI-0 lands, hand the UI-3 Network screen or part of the component set to M2.

Rebalance at the end of Phase 2 if someone is clearly ahead/behind: the next free item goes to the member with
the lowest finished-points total (inside *their own* packages — never take over someone's concept).

## Definition of completion (from the project guide)
- [ ] 2+ (target 3) train clients connect; registration, position/status updates and replies work over TCP
- [ ] Java NIO Selector server demonstrated (M1)
- [ ] Simultaneous reservation of one section is impossible (M1)
- [ ] IP resolution and port configuration visible and documented (M2)
- [ ] UDP discovery with manual IP fallback (M3)
- [ ] Multicast alerts reach subscribers, or documented unicast fallback (M3)
- [ ] Heartbeat marks inactive clients (M3)
- [ ] TLS with certificate validation; untrusted certificate rejected (M4)
- [ ] URLConnection retrieves a railway resource (M4)
- [ ] RMI remote administration works (M5)
- [ ] `smartrail://` resolves a real resource; content handler returns a typed object (M5)
- [ ] Each member: independent module, tests, module doc, test evidence, presentation slot
