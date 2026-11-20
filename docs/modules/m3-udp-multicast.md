# Module: UDP, multicast & heartbeat (Member 3)

## Purpose and networking concept
Three connectionless mechanisms that a single TCP/TLS connection cannot provide by itself:
- **UDP discovery** — a train client finds the server's IP without anyone typing it in.
- **IP multicast alerts** — the server tells every train about a track obstruction with one packet,
  with a unicast fallback for Wi-Fi networks that block multicast.
- **Heartbeat** — the server notices a train has gone silent and marks it `INACTIVE`, instead of waiting
  for a failed TCP write.

## How to start / see it
```bash
mvn -q -DskipTests compile
scripts/run.sh server                 # starts the UDP responder, multicast publisher, heartbeat monitor
scripts/run.sh train TR01             # Discover button (Discovery & alerts tab) finds the server
```
`ServerApp` logs `STARTED UDP discovery responder`, `STARTED Multicast alert publisher` and
`STARTED Heartbeat monitor` at start-up (verified against the real launcher, not just unit tests).

## Public classes and methods
| Class / method | Input | Output | Notes |
|---|---|---|---|
| `DiscoveryResponder` (`NetworkService`) | `udp.discovery.*`, `server.advertised.host` | replies on the socket | one daemon thread; ignores non-`DISCOVER` or wrong service/version datagrams |
| `DiscoveryClient.discover()` | — (reads config) | `Optional<DiscoveredServer>` | broadcasts `DISCOVER`, waits up to `udp.discovery.timeout.ms`, ignores non-matching replies until the deadline |
| `DiscoveredServer(host, plainPort, tlsPort, protocolVersion)` | — | — | parsed `DISCOVER_REPLY` |
| `MulticastAlertPublisher` (`AlertService`, `NetworkService`) | `publishAlert(sectionId, reason)` | multicast datagram (+ unicast fallback) | `bindRailwayService()` supplies connected trains' IPs for the fallback |
| `MulticastAlertSubscriber` | `start()` / `stop()` | calls `onAlert` for each `EMERGENCY_ALERT` | `joinGroup`/`leaveGroup` with an explicit `NetworkInterface`; non-alert datagrams ignored |
| `HeartbeatMonitor` (`HeartbeatTracker`, `NetworkService`) | `recordHeartbeat(trainId)` | `isAlive(trainId)`, calls `railway.markInactive` | scheduled check every `heartbeat.interval.ms`; marks a train inactive once per silence, clears on the next heartbeat |
| `HeartbeatSender` | `start()` / `stop()` | periodic `HEARTBEAT` via the injected sink | survives send failures; `start()` is idempotent (restarts cleanly) |
| `UdpMulticastPanel` | Discover / Subscribe buttons | discovery result, alert list | `stopSubscription()` exposed for the owning window to call on close |

## Configuration keys used
`protocol.service.name`, `protocol.version`, `protocol.max.message.bytes`, `server.advertised.host`,
`nio.tcp.port`, `tls.port` (read, not owned, to fill the `DISCOVER_REPLY`), `udp.discovery.*`, `multicast.*`,
`heartbeat.*`. Full descriptions in [docs/configuration.md](../configuration.md).

## Errors and how they are handled
- **Malformed/foreign UDP datagram** (discovery or multicast): decode failure is caught, logged, and the
  receive loop keeps running — it never crashes the service.
- **Wrong service name/version** in a `DISCOVER`/`DISCOVER_REPLY`: silently ignored (responder does not
  reply; client keeps waiting until its deadline).
- **Discovery timeout**: `DiscoveryClient.discover()` returns `Optional.empty()` — the UI shows "No
  response - timed out" and the Train tab still accepts a manually typed host/IP.
- **Multicast join failure** (no multicast-capable route — seen on some VPNs/sandboxed hosts and on Wi-Fi
  networks that block multicast): `MulticastAlertSubscriber.start()` throws `IllegalStateException` with a
  clear message instead of hanging; the panel catches it, reverts the button, and shows the message. The
  **unicast fallback** (`multicast.unicast.fallback.enabled=true`) is the documented workaround for exactly
  this case.
- **Heartbeat timeout**: handled, not an error — `HeartbeatMonitor` marks the train `INACTIVE` exactly once
  per silent period and clears the flag as soon as a heartbeat arrives again.

## Tests
```bash
mvn -q test -Dtest=com.smartrail.udp.DiscoveryTest
mvn -q test -Dtest=com.smartrail.multicast.MulticastAlertTest
mvn -q test -Dtest=com.smartrail.heartbeat.HeartbeatMonitorTest
```
`DiscoveryTest` and `HeartbeatMonitorTest` always run (plain UDP/unicast and pure timer logic — both routable
everywhere). `MulticastAlertTest.twoSubscribersReceiveOneAlert` performs a **real** multicast join + send +
receive; on a host with no usable multicast route it reports itself **skipped** via
`Assumptions.assumeTrue(...)` with the message *"no multicast datagram arrived within the timeout - treating
as multicast unavailable on this host/network"* rather than failing the build. The other two multicast tests
(`nonAlertDatagramsAreIgnored`, `unicastFallbackReachesConnectedTrains`) always run — they only need plain,
routable UDP.

## Evidence (screenshots / logs)
- Two-process discovery (`ServerApp` + a standalone client pointed at `127.0.0.1`):
  ```
  11:23:14.850 [SERVER] STARTED UDP discovery responder -> UDP *:5001
  11:23:16.709 [UDP] Discovery reply sent to /127.0.0.1:55491
  RESULT: Optional[DiscoveredServer[host=192.168.8.106, plainPort=5000, tlsPort=5443, protocolVersion=1]]
  ```
  (host was auto-detected from the machine's LAN interface — `server.advertised.host` was left blank).
- `mvn verify` locally: `MulticastAlertTest` → `Tests run: 3, Skipped: 1` (the real-network test skipped,
  the datagram-decoding and unicast-fallback tests passed) — multicast was genuinely unavailable on the
  development machine used (active VPN tunnels), which is itself evidence for the fallback requirement.
- On the real demo LAN, re-run `MulticastAlertTest` and replace this note with "0 skipped" plus a screenshot
  of the server log showing `[MULTICAST] Published EMERGENCY_ALERT|...` and both train windows' alert lists.

## Limitations
- The multicast outgoing interface is picked automatically when `multicast.interface` is blank (first
  non-loopback, multicast-capable, "up" interface) — on a multi-homed machine, set `multicast.interface`
  explicitly if the wrong NIC is chosen.
- The unicast fallback targets the IP each train's TCP/TLS connection is seen from; it does not work for a
  train that has never registered (no known IP yet).
- `UdpMulticastPanel`'s alert list shows time + section + reason, not the sender's IP — `MulticastAlertSubscriber`'s
  callback is `Consumer<Message>` (a frozen shared type) and does not carry the datagram's source address.
