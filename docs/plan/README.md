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
| **3 — Integration** | week 3 | Real end-to-end through `ServerApp` / `TrainClientApp` / `ControllerApp` | integration checkpoints C1–C8 pass |
| **4 — UI & evidence** | week 4 | Member panels in the shells; screenshots/logs for the report | every feature reachable from the UI |
| **5 — Test, docs, demo** | week 5 | Test plan executed on a real LAN, module docs, report section, rehearsal | 10-minute demo runs twice without restart |

Merge to `main` at least once per phase — integrate continuously, never at the end.

## Integration checkpoints (Phase 3)

| ID | Checkpoint | Members | Check |
|---|---|---|---|
| C1 | `RailwayServiceImpl` + router merged (replaces fakes) | 1 | server starts; `/api/trains` via curl not needed yet |
| C2 | Train registers over plain NIO channel | 1 + 2 | `REGISTER_OK` + `ASSIGN_ROUTE` in train window |
| C3 | Same train over TLS | 4 + 2 (+1 router) | `-Dclient.transport=TLS` works; untrusted cert rejected |
| C4 | Discovery fills the train's host/port | 3 + 2 | Discover button → connect without typing IP |
| C5 | Heartbeat → INACTIVE | 3 + 1 | kill a train → INACTIVE after `heartbeat.timeout.ms` |
| C6 | RMI hold reaches the train | 5 + 1 + 2 | `requestSimulatedHold(TR02)` → TR02 window shows HELD |
| C7 | Obstruction alert reaches all trains | 5 + 1 + 3 | `reportObstruction(SEC_B)` → 2+ trains show alert |
| C8 | Typed resources | 4 + 5 | `ResourceClient.fetchContent("tracks")` and `smartrail://…/trains/TR01` return typed objects |

## Dependency rules (how parallel work stays unblocked)
- Before C1, Members 3/4/5 use `FakeRailwayService`; Member 4's TLS server can use a fake `MessageHandler` in tests.
- Member 2 can test `TrainClient` against a tiny `ServerSocket` in the test or against `nc -l <port>`.
- Member 1 can test the server with `nc localhost 5000` (type protocol lines by hand) before Member 2 is ready.
- Nobody edits `ServerApp`/`TrainClientApp`/`ControllerApp` — they already wire every class.

## Workload balance (target ≈ 20% each)

Effort points per member (1 point ≈ half a day). Every member also carries panel, tests, docs and a 2-minute demo.

| Work | M1 | M2 | M3 | M4 | M5 |
|---|---|---|---|---|---|
| Core networking concept(s) | NIO server 5, framing 2 | TCP client 4, InetAddress 3 | UDP discovery 4, multicast 4 | TLS 4 + certs 2, URLConnection 3 | RMI 5, protocol handler 4 |
| Second responsibility | router 3, reservations/registry/routes 4 | simulator 3, reconnect/errors 2 | heartbeat 3, unicast fallback 1 | HTTP endpoints 3, TLS client 1 | resource server 2, content handlers 3 |
| UI | panel 2 | shells 2 + panel 1 | panel 2 | panel 2 | panel 2 |
| Tests + docs + demo | 3 | 3 | 3 | 3 | 3 |
| Integration support | 1 | 2 | 2 | 1 | 1 |
| **Total** | **20** | **20** | **19** | **19** | **20** |

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
