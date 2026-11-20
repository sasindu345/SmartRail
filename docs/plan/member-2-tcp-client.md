# Member 2 — TCP client, IP addressing & application shell

**Concepts you demonstrate:** TCP client sockets with Java I/O streams; IP addressing — `InetAddress`, host name
resolution, IPv4 vs IPv6, private/loopback addresses, local interfaces, ports.

**Your files:** `tcpclient/ServerAddressResolver`, `tcpclient/PlainSocketFactory`, `tcpclient/TrainClient`,
`tcpclient/TrainClientListener`, `tcpclient/TrainSimulator`, tests in `src/test/java/com/smartrail/tcpclient/`,
`docs/modules/m2-tcp-client.md`.

**You provide:** `PlainSocketFactory` (`ClientSocketFactory`), `TrainClient` (used by M3's heartbeat sender through
`client::send`), the three window shells hosting every member's panel.
**You use:** `ClientSocketFactory` (M4's `TlsSocketFactory` for TLS), `DiscoveredServer` (M3), M1's server.
**Agent:** `.claude/agents/m2-tcp-client.md` · **Branch prefix:** `m2/`

## Phase 0 — Setup
- [ ] Clone, JDK 21, `mvn verify` green; create branch `m2/tcp-client`
- [ ] Read `AGENTS.md`, `docs/protocol.md` §1, `TrainClientApp`, `ClientSocketFactory`
- [ ] Agree with M3 how discovery hands over host/port (`TcpClientPanel.useDiscoveredServer`)

## Phase 1 — IP addressing + TCP connect
- [ ] `ServerAddressResolver.resolve/resolveAll` (host names and literal IPv4/IPv6)
- [ ] `describe`: IPv4/IPv6, loopback, site-local (private), link-local, multicast
- [ ] `localInterfaceAddresses` via `NetworkInterface` (name + addresses, up interfaces only)
- [ ] `PlainSocketFactory.connect`: `Socket.connect(addr, timeout)`, `setTcpNoDelay`, `setKeepAlive`
- [ ] Manual proof: connect `TrainClient` to `nc -l 5000` and see `REGISTER|TR01|ST01` arrive
- [ ] Tests: `ServerAddressResolverTest` (localhost, `::1`, unknown host)

## Phase 2 — Train client & simulator
- [ ] `TrainClient.connect`: resolve → factory socket (`client.connect.timeout.ms`) → UTF-8 reader/writer → reader thread → send `REGISTER`
- [ ] Reader thread: `readLine` → `ProtocolCodec.decode` → listeners; malformed line logged, not fatal
- [ ] `send` thread-safe; `disconnect` closes cleanly; listeners notified once
- [ ] Reconnect: `client.reconnect.attempts` × `client.reconnect.delay.ms`
- [ ] Clear errors: unknown host, connection refused, timeout (no stack traces in UI)
- [ ] `TrainSimulator`: follows `ASSIGN_ROUTE`; REQUEST_SECTION → MOVING → RELEASE previous; retries DENIED; obeys HOLD/RESUME/EMERGENCY_STOP; ARRIVED at end
- [ ] Tests: `TrainClientTest` with an in-test `ServerSocket` on port `0` (register, decode lines, refused port, reconnect)

## Phase 3 — Integration
- [ ] C2 with M1: plain registration + full route
- [ ] C3 with M4: same with `-Dclient.transport=TLS` (only the factory changes)
- [ ] C4 with M3: discovery fills host/port (plain or TLS port by transport)
- [ ] Heartbeat starts/stops with the connection (M3's `HeartbeatSender` via `TrainClientApp` listener)
- [ ] C6: HOLD from RMI is shown and obeyed
- [ ] Run 3 trains from 2 different machines on the LAN

## Phase 4 — Evidence

> **UI work moved out.** The Control Center shell (window, theme, shared components,
> `TrainProcessSupervisor`) is UI issue **UI-0**, and the Trains screen is **UI-2**, both against
> `docs/ui/control-center-spec.md`. This issue is now the TCP client and IP addressing only.

- [ ] Evidence for the report: IPv4 and IPv6 resolution, a connection error message, 3 trains running

## Phase 5 — Test, docs, demo
- [ ] Fill `docs/modules/m2-tcp-client.md`; your rows in `docs/test-plan.md`
- [ ] 2-minute demo slot (2:00–4:00): resolve, connect, register, position update, wrong-address error
- [ ] Report section: client-server model, IP/ports, private vs public addressing, IPv4/IPv6

## Done when
Two or three independent train processes connect, register, follow routes and react to server commands; IP
resolution and ports are visible in the UI; the UI never freezes during network calls.

## Out of scope
Building other members' backends, TLS code (M4), discovery code (M3). The network map belongs to M1.
Your UI work is tracked separately in UI-0 (Control Center shell) and UI-2 (Trains screen).
