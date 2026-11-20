# Member 3 — UDP discovery, multicast alerts & heartbeat

**Concepts you demonstrate:** UDP datagrams (connectionless request/reply, broadcast), IP multicast (group
membership, TTL, one-to-many), heartbeat-based availability monitoring.

**Your files:** `udp/DiscoveryResponder`, `udp/DiscoveryClient`, `udp/DiscoveredServer`,
`multicast/MulticastAlertPublisher`, `multicast/MulticastAlertSubscriber`, `heartbeat/HeartbeatMonitor`,
`heartbeat/HeartbeatSender`, tests in
`src/test/java/com/smartrail/{udp,multicast,heartbeat}/`, `docs/modules/m3-udp-multicast.md`.

**You provide:** `AlertService` + `HeartbeatTracker` (used by M1), `DiscoveredServer` (used by M2's panel).
**You use:** `RailwayService.markInactive` / `getTrains` (M1; `FakeRailwayService` until C1).
**Agent:** `.claude/agents/m3-udp-multicast.md` · **Branch prefix:** `m3/`

## Phase 0 — Setup
- [x] Clone, JDK 21, `mvn verify` green; create branch `m3/udp-discovery-multicast-heartbeat`
- [x] Read `AGENTS.md`, `docs/protocol.md` §2–3, `AlertService`, `HeartbeatTracker`
- [x] Agree heartbeat format/flow with M1 (`HEARTBEAT|trainId` on TCP, router calls `recordHeartbeat`) — already frozen and unambiguous in `docs/protocol.md` and the `HeartbeatTracker`/`MessageHandler` Javadoc, so no further negotiation was needed
- [x] Check whether the demo Wi-Fi allows multicast (decides whether fallback is needed on demo day) — on the dev machine used for this work, real multicast routing is **not** available (active VPN tunnels block it); this confirms the unicast fallback is needed, see `docs/modules/m3-udp-multicast.md`

## Phase 1 — UDP discovery
- [x] `DiscoveryResponder`: `DatagramSocket` on `udp.discovery.port`, loop thread, validate `DISCOVER|service|version`
- [x] Reply `DISCOVER_REPLY|service|version|host|nioPort|tlsPort` to the sender (host = `server.advertised.host` or auto site-local)
- [x] `DiscoveryClient.discover`: `setBroadcast(true)`, send to `udp.discovery.broadcast.address`, `setSoTimeout(udp.discovery.timeout.ms)`, validate reply
- [x] Timeout → `Optional.empty()` (UI offers manual IP)
- [x] Tests: `DiscoveryTest` (responder on port `0` + client to `127.0.0.1`; timeout; wrong service ignored) — verified further with a real two-process run (see module doc)

## Phase 2 — Multicast + heartbeat
- [x] `MulticastAlertPublisher.start/publishAlert`: TTL `multicast.ttl`, interface `multicast.interface`, send `EMERGENCY_ALERT|section|reason`
- [x] Unicast fallback when `multicast.unicast.fallback.enabled`: same datagram to each connected train IP
- [x] `MulticastAlertSubscriber`: bind port, `joinGroup(SocketAddress, NetworkInterface)` (non-deprecated), receive loop, only EMERGENCY_ALERT, `leaveGroup` on stop
- [x] `HeartbeatSender`: scheduled `HEARTBEAT|trainId` every `heartbeat.interval.ms`, survives send failures
- [x] `HeartbeatMonitor`: last-seen map; scheduled check; silent > `heartbeat.timeout.ms` → `markInactive` once; heartbeat again → alive
- [x] Tests: `MulticastAlertTest` (2 subscribers receive one alert — skips itself with a message on this host, which has no multicast route; the decode/filter logic and the unicast fallback are covered by two further tests that always run), `HeartbeatMonitorTest` with `FakeRailwayService` and short timeouts from `TestConfig.withOverrides`

## Phase 3 — Integration
- [ ] C4 with M2: discovery fills the train's host/port — **blocked on M2**: `TcpClientPanel.useDiscoveredServer` is still `TODO(M2)`; my side (`DiscoveryClient` → `UdpMulticastPanel` → `onDiscovered.accept(...)`) is ready and was verified to work in a real two-process run (see module doc)
- [ ] C5 with M1: stopping a train → INACTIVE on the server panel after timeout — **blocked on M1**: `RailwayServiceImpl`/`NioTrainServer` are still stubs; `HeartbeatMonitor` itself is verified against `FakeRailwayService` in `HeartbeatMonitorTest`
- [ ] C7 with M1/M5: `reportObstruction` → all train windows show the alert — **blocked on M1**: `RailwayServiceImpl.reportObstruction` is still a stub (M5's RMI call already reaches it, see `RailwayAdminServiceTest`); my `AlertService` side is ready and covered by `MulticastAlertTest`
- [ ] Test on the real LAN: discovery across machines; multicast or fallback — not yet done (needs a second physical machine)

## Phase 4 — UI & evidence
- [x] `UdpMulticastPanel`: Discover button + result/timeout message; Subscribe/Unsubscribe; received alerts list with time — **note:** sender IP is not shown; `MulticastAlertSubscriber`'s callback type is the frozen `Consumer<Message>`, which does not carry the datagram's source address (documented as a limitation in the module doc)
- [ ] Screenshots/logs: discovery request + reply (done, see module doc), two clients receiving one alert (not available on this host - no multicast route; will capture on the real demo LAN), INACTIVE after timeout (done, see `HeartbeatMonitorTest` passing)

## Phase 5 — Test, docs, demo
- [x] Fill `docs/modules/m3-udp-multicast.md` (incl. multicast-blocked note + fallback)
- [x] Your rows in `docs/test-plan.md`
- [ ] 2-minute demo slot (4:00–6:00): discovery, alert to 2 trains, heartbeat timeout — script already drafted in `docs/demo-script.md`; not yet rehearsed live
- [ ] Report section: TCP vs UDP, broadcast vs multicast, why heartbeat — not yet written

## Done when
A client finds the server without typing an IP; one alert reaches every subscribed train (or fallback documented);
a stopped train becomes INACTIVE automatically.

## Out of scope
Sending train telemetry over UDP/multicast, reliable-multicast protocols, NAT traversal.
UI work is tracked separately in UI-0 (Control Center shell foundation) and UI-3 (Alerts & Network screens).
