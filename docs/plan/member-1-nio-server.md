# Member 1 — Java NIO server & track coordination

**Concepts you demonstrate:** Java NIO (`ServerSocketChannel`, `SocketChannel`, `Selector`, `SelectionKey`,
`ByteBuffer`) — the project's mandatory NIO requirement; TCP byte-stream framing; server-side coordination of
shared state (atomic reservation).

**Your files:** `nio/NioTrainServer`, `nio/NioTrainConnection`, `nio/LineFrameDecoder`,
`coordination/TrainMessageRouter`, `coordination/RailwayServiceImpl`, `coordination/TrainRegistry`,
`coordination/TrackReservationService`, `coordination/RouteService`, `ui/panels/NioServerPanel`,
tests in `src/test/java/com/smartrail/{nio,coordination}/`, `docs/modules/m1-nio-server.md`.

**You provide:** `RailwayService` (used by M3, M4, M5, UI), `MessageHandler` (used by your NIO server and M4's TLS server).
**You use:** `AlertService` (M3), `HeartbeatTracker` (M3), `TrainConnection` (yours + M4's).
**Agent:** `.claude/agents/m1-nio-server.md` · **Branch prefix:** `m1/`

## Phase 0 — Setup
- [ ] Clone, JDK 21, `mvn verify` green; create branch `m1/nio-selector`
- [ ] Read `AGENTS.md`, `docs/architecture.md`, `docs/protocol.md` §1, `common/service/*`
- [ ] Confirm with M4 that `MessageHandler`/`TrainConnection` cover the TLS server's needs (before freeze)
- [ ] Confirm with M3 the heartbeat flow (`HEARTBEAT` → `recordHeartbeat`, timeout → `markInactive`)

## Phase 1 — NIO core
- [ ] `LineFrameDecoder.decode`: accumulate bytes, return complete UTF-8 lines, keep partial tail
- [ ] Oversized line → `ProtocolException(MESSAGE_TOO_LARGE)` using `protocol.max.message.bytes`
- [ ] `NioTrainServer.start`: non-blocking `ServerSocketChannel`, bind `server.bind.address:nio.tcp.port`, `OP_ACCEPT`
- [ ] Selector loop on its own thread (`nio.selector.timeout.ms`), accept → non-blocking `SocketChannel`, `OP_READ`, attach `NioTrainConnection`
- [ ] Read path: `ByteBuffer` read → decoder → `ProtocolCodec.decode` → `messageHandler.onMessage`; bad line → `onProtocolError`; `-1` → `onDisconnect`
- [ ] Manual proof: `nc localhost 5000`, type `REGISTER|TR01|ST01`, see the server log line
- [ ] Tests: `LineFrameDecoderTest` (partial, multiple, oversize)

## Phase 2 — Writes, router, coordination
- [ ] `NioTrainConnection.send`: thread-safe queue, `OP_WRITE`, `selector.wakeup()`; flush handles partial writes
- [ ] `stop()`: close selector, server and client channels cleanly
- [ ] `TrainRegistry`: trainId ↔ connection + state; duplicates rejected
- [ ] `TrackReservationService`: **atomic** check-and-reserve; reason `RESERVED_BY_<id>`; release; release-all on disconnect
- [ ] `RouteService`: route from `RailwayData`; `ASSIGN_ROUTE` sections as comma list
- [ ] `TrainMessageRouter`: REGISTER / POSITION / REQUEST_SECTION / RELEASE_SECTION / HEARTBEAT; validation (registered, identity, direction) → `ERROR|code|detail`
- [ ] `RailwayServiceImpl`: snapshots; hold/resume/emergencyStop push messages; reportObstruction → `AlertService`; markInactive; onTrainDisconnected
- [ ] Tests: `TrackReservationServiceTest` (incl. 2 threads racing for one section, 100 iterations), `TrainMessageRouterTest` (with `FakeTrainConnection`), `NioTrainServerTest` (real server on port `0`, 2 clients)
- [ ] PR merged → announce **checkpoint C1** to the team

## Phase 3 — Integration
- [ ] C2 with M2: plain train registers and runs its route
- [ ] C3 with M4: TLS server calls your router; same behaviour as plain
- [ ] C5 with M3: heartbeat timeout marks INACTIVE; reconnect makes it active again
- [ ] C6 with M5: RMI hold → `HOLD|TR02` reaches the train
- [ ] C7 with M3/M5: obstruction publishes an alert
- [ ] Conflict demo: TR01 and TR03 (routes share SEC_A) — only one is granted

## Phase 4 — UI & evidence
- [ ] `NioServerPanel`: trains table (id, status, location, transport, IP, last seen), sections table (state, holder), live event log
- [ ] Refresh with `javax.swing.Timer`; event log via `EventLog` listener + `UiThreads.onEdt`
- [ ] Screenshots: 3 trains connected; a `SECTION_DENIED`; server log of a partial/merged read test

## Phase 5 — Test, docs, demo
- [ ] Fill `docs/modules/m1-nio-server.md`
- [ ] Your rows in `docs/test-plan.md` executed with evidence
- [ ] 2-minute demo slot (0:00–2:00) in `docs/demo-script.md`; explain Selector vs thread-per-client
- [ ] Report section: NIO architecture diagram, framing, atomic reservation

## Done when
Three trains connect to one selector thread; framing survives split/merged reads; concurrent requests for one
section never both succeed; all M1 tests enabled and green.

## Out of scope
SSLEngine/TLS on NIO (M4 owns TLS), persistence, thread-per-client for the NIO server, complex routing algorithms.
