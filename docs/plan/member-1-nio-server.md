# Member 1 — Java NIO server & track coordination

**Concepts you demonstrate:** Java NIO (`ServerSocketChannel`, `SocketChannel`, `Selector`, `SelectionKey`,
`ByteBuffer`) — the project's mandatory NIO requirement; TCP byte-stream framing; server-side coordination of
shared state (atomic reservation).

**Your files:** `nio/NioTrainServer`, `nio/NioTrainConnection`, `nio/LineFrameDecoder`,
`coordination/TrainMessageRouter`, `coordination/RailwayServiceImpl`, `coordination/TrainRegistry`,
`coordination/TrackReservationService`, `coordination/RouteService`,
tests in `src/test/java/com/smartrail/{nio,coordination}/`, `docs/modules/m1-nio-server.md`.

**You provide:** `RailwayService` (used by M3, M4, M5, UI), `MessageHandler` (used by your NIO server and M4's TLS server).
**You use:** `AlertService` (M3), `HeartbeatTracker` (M3), `TrainConnection` (yours + M4's).
**Agent:** `.claude/agents/m1-nio-server.md` · **Branch prefix:** `m1/`

## Phase 0 — Setup
- [ ] Clone, JDK 21, `mvn verify` green; create branch `m1/nio-selector` — implementation is in the working tree; branch and PR are not opened yet
- [x] Read `AGENTS.md`, `docs/architecture.md`, `docs/protocol.md` §1, `common/service/*`
- [x] Confirm with M4 that `MessageHandler`/`TrainConnection` cover the TLS server's needs (before freeze) — frozen `MessageHandler` is what `TlsTrainServer` already calls
- [x] Confirm with M3 the heartbeat flow (`HEARTBEAT` → `recordHeartbeat`, timeout → `markInactive`) — router calls `recordHeartbeat`; `markInactive` releases sections and sets `INACTIVE`

## Phase 1 — NIO core
- [x] `LineFrameDecoder.decode`: accumulate bytes, return complete UTF-8 lines, keep partial tail
- [x] Oversized line → `ProtocolException(MESSAGE_TOO_LARGE)` using `protocol.max.message.bytes`
- [x] `NioTrainServer.start`: non-blocking `ServerSocketChannel`, bind `server.bind.address:nio.tcp.port`, `OP_ACCEPT`
- [x] Selector loop on its own thread (`nio.selector.timeout.ms`), accept → non-blocking `SocketChannel`, `OP_READ`, attach `NioTrainConnection`
- [x] Read path: `ByteBuffer` read → decoder → `ProtocolCodec.decode` → `messageHandler.onMessage`; bad line → `onProtocolError`; `-1` → `onDisconnect`
- [x] Manual proof: `nc localhost 5000`, type `REGISTER|TR01|ST01`, see the server log line — covered by `NioTrainServerTest` (real sockets, port `0`) which logs `REGISTER TR01`
- [x] Tests: `LineFrameDecoderTest` (partial, multiple, oversize)

## Phase 2 — Writes, router, coordination
- [x] `NioTrainConnection.send`: thread-safe queue, `OP_WRITE`, `selector.wakeup()`; flush handles partial writes
- [x] `stop()`: close selector, server and client channels cleanly
- [x] `TrainRegistry`: trainId ↔ connection + state; duplicates rejected
- [x] `TrackReservationService`: **atomic** check-and-reserve; reason `RESERVED_BY_<id>`; release; release-all on disconnect
- [x] `RouteService`: route from `RailwayData`; `ASSIGN_ROUTE` sections as comma list
- [x] `TrainMessageRouter`: REGISTER / POSITION / REQUEST_SECTION / RELEASE_SECTION / HEARTBEAT; validation (registered, identity, direction) → `ERROR|code|detail`
- [x] `RailwayServiceImpl`: snapshots; hold/resume/emergencyStop push messages; reportObstruction → `AlertService`; markInactive; onTrainDisconnected
- [x] Tests: `TrackReservationServiceTest` (incl. 2 threads racing for one section, 100 iterations), `TrainMessageRouterTest` (with `FakeTrainConnection`), `NioTrainServerTest` (real server on port `0`, 2 clients)
- [ ] PR merged → announce **checkpoint C1** to the team

## Phase 3 — Integration
- [ ] C2 with M2: plain train registers and runs its route
- [ ] C3 with M4: TLS server calls your router; same behaviour as plain
- [ ] C5 with M3: heartbeat timeout marks INACTIVE; reconnect makes it active again
- [ ] C6 with M5: RMI hold → `HOLD|TR02` reaches the train
- [ ] C7 with M3/M5: obstruction publishes an alert
- [x] Conflict demo: TR01 and TR03 (routes share SEC_A) — only one is granted — `TrackSectionsPanel` opens two TCP clients; the race itself is `TrackReservationServiceTest`

## Phase 4 — Evidence

> **UI work moved out.** The Overview, Track Sections and Event Log screens (including the network
> map) are now tracked in UI issue **UI-1**, against `docs/ui/control-center-spec.md`. Do not build
> UI here; finish the networking phases first.

- [x] Evidence for the report: 3 trains connected on one selector thread; a `SECTION_DENIED`; server
      log of a partial/merged read test — `NioTrainServerTest`, `TrackReservationServiceTest`, `LineFrameDecoderTest`

## Phase 5 — Test, docs, demo
- [x] Fill `docs/modules/m1-nio-server.md`
- [x] Your rows in `docs/test-plan.md` executed with evidence
- [x] 2-minute demo slot (0:00–2:00) in `docs/demo-script.md`; explain Selector vs thread-per-client — script already says this; the clicks are Start Server and Run conflict demo
- [x] Report section: NIO architecture diagram, framing, atomic reservation — in `docs/modules/m1-nio-server.md`

## Done when
Three trains connect to one selector thread; framing survives split/merged reads; concurrent requests for one
section never both succeed; all M1 tests enabled and green.

## Out of scope
SSLEngine/TLS on NIO (M4 owns TLS), persistence, thread-per-client for the NIO server, complex routing
algorithms. UI screens are tracked separately in UI-1.
