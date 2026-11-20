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
- [ ] Clone, JDK 21, `mvn verify` green; create branch `m3/udp-discovery`
- [ ] Read `AGENTS.md`, `docs/protocol.md` §2–3, `AlertService`, `HeartbeatTracker`
- [ ] Agree heartbeat format/flow with M1 (`HEARTBEAT|trainId` on TCP, router calls `recordHeartbeat`)
- [ ] Check whether the demo Wi-Fi allows multicast (decides whether fallback is needed on demo day)

## Phase 1 — UDP discovery
- [ ] `DiscoveryResponder`: `DatagramSocket` on `udp.discovery.port`, loop thread, validate `DISCOVER|service|version`
- [ ] Reply `DISCOVER_REPLY|service|version|host|nioPort|tlsPort` to the sender (host = `server.advertised.host` or auto site-local)
- [ ] `DiscoveryClient.discover`: `setBroadcast(true)`, send to `udp.discovery.broadcast.address`, `setSoTimeout(udp.discovery.timeout.ms)`, validate reply
- [ ] Timeout → `Optional.empty()` (UI offers manual IP)
- [ ] Tests: `DiscoveryTest` (responder on port `0` + client to `127.0.0.1`; timeout; wrong service ignored)

## Phase 2 — Multicast + heartbeat
- [ ] `MulticastAlertPublisher.start/publishAlert`: TTL `multicast.ttl`, interface `multicast.interface`, send `EMERGENCY_ALERT|section|reason`
- [ ] Unicast fallback when `multicast.unicast.fallback.enabled`: same datagram to each connected train IP
- [ ] `MulticastAlertSubscriber`: bind port, `joinGroup(SocketAddress, NetworkInterface)` (non-deprecated), receive loop, only EMERGENCY_ALERT, `leaveGroup` on stop
- [ ] `HeartbeatSender`: scheduled `HEARTBEAT|trainId` every `heartbeat.interval.ms`, survives send failures
- [ ] `HeartbeatMonitor`: last-seen map; scheduled check; silent > `heartbeat.timeout.ms` → `markInactive` once; heartbeat again → alive
- [ ] Tests: `MulticastAlertTest` (2 subscribers receive one alert — skip with message if the host has no multicast), `HeartbeatMonitorTest` with `FakeRailwayService` and short timeouts from `TestConfig.withOverrides`

## Phase 3 — Integration
- [ ] C4 with M2: discovery fills the train's host/port
- [ ] C5 with M1: stopping a train → INACTIVE on the server panel after timeout
- [ ] C7 with M1/M5: `reportObstruction` → all train windows show the alert
- [ ] Test on the real LAN: discovery across machines; multicast or fallback

## Phase 4 — Evidence

> **UI work moved out.** The Alerts and Network screens are now UI issue **UI-3**, and you additionally
> own **UI-0**, the Control Center shell foundation that every other member's screen plugs into — land it
> first, since it blocks UI-1…UI-5. Both against `docs/ui/control-center-spec.md`.

- [x] *(superseded by UI-3, work done)* `UdpMulticastPanel`: Discover button + result/timeout message;
      Subscribe/Unsubscribe; received alerts list with time. Note: sender IP is not shown —
      `MulticastAlertSubscriber`'s callback type is the frozen `Consumer<Message>`, which does not carry
      the datagram's source address (documented in the module doc).
- [ ] Evidence for the report: discovery request + reply (done, see module doc), two clients receiving
      one alert (needs a LAN with multicast), INACTIVE after timeout (done, `HeartbeatMonitorTest`)

## Phase 5 — Test, docs, demo
- [ ] Fill `docs/modules/m3-udp-multicast.md` (incl. multicast-blocked note + fallback)
- [ ] Your rows in `docs/test-plan.md`
- [ ] 2-minute demo slot (4:00–6:00): discovery, alert to 2 trains, heartbeat timeout
- [ ] Report section: TCP vs UDP, broadcast vs multicast, why heartbeat

## Done when
A client finds the server without typing an IP; one alert reaches every subscribed train (or fallback documented);
a stopped train becomes INACTIVE automatically.

## Out of scope
Sending train telemetry over UDP/multicast, reliable-multicast protocols, NAT traversal.
UI work is tracked separately in UI-0 (Control Center shell foundation) and UI-3 (Alerts & Network screens).
