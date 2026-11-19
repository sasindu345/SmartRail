# Configuration, services and ports

**Rule: nothing is hard-coded.** All values live in `config/smartrail.properties` (network) and
`config/railway-data.properties` (railway model). Code reads them through `NetworkConfig` with constants from
`ConfigKeys`, and `RailwayData`.

## How values are resolved
1. JVM system property `-Dkey=value` (per process, e.g. `scripts/run.sh train TR02 -Dclient.transport=TLS`)
2. `config/smartrail.properties` (or the file named by `-Dsmartrail.config=...`)

Tests: `TestConfig.withOverrides(ConfigKeys.NIO_TCP_PORT, "0")` — port `0` lets the OS pick a free port.

## Services and default ports

| Service | Owner | Transport | Key(s) | Default |
|---|---|---|---|---|
| Train-control (plain) | M1 | TCP, Java NIO | `nio.tcp.port` | 5000 |
| UDP discovery | M3 | UDP | `udp.discovery.port` | 5001 |
| Multicast alerts | M3 | UDP multicast | `multicast.group`, `multicast.port` | 230.0.0.1:5002 |
| Train-control (secure) | M4 | TLS over TCP | `tls.port` | 5443 |
| smartrail:// resources | M5 | TCP | `smartrail.resource.port` | 5100 |
| HTTP resources | M4 | HTTP | `http.resource.port`, `http.resource.base.path` | 8080 `/api` |
| RMI registry / object | M5 | RMI over TCP | `rmi.registry.port`, `rmi.service.port` | 1099 / 1100 |

Each server service has an `*.enabled` flag. Server binds to `server.bind.address` (default `0.0.0.0`).
`server.advertised.host` is the address put in discovery replies and used as `java.rmi.server.hostname`
(empty = auto-detect a site-local address). Clients connect to `client.server.host`.

Firewall: on the demo LAN allow inbound TCP 5000, 5443, 5100, 8080, 1099, 1100 and UDP 5001, 5002 on the server.
Many campus Wi-Fi networks block multicast — then set `multicast.unicast.fallback.enabled=true`.

## All keys

| Key | Meaning |
|---|---|
| `protocol.service.name`, `protocol.version` | identity checked in discovery |
| `protocol.max.message.bytes` | max protocol line size |
| `railway.data.path` | railway model file |
| `log.capacity` | in-memory event log size |
| `server.bind.address`, `server.advertised.host`, `ui.server.window.enabled` | server |
| `client.server.host`, `client.transport` (`PLAIN`/`TLS`), `client.default.train.id` | clients |
| `client.connect.timeout.ms`, `client.reconnect.attempts`, `client.reconnect.delay.ms`, `client.simulator.step.ms` | train client (M2) |
| `nio.tcp.enabled`, `nio.tcp.port`, `nio.selector.timeout.ms` | NIO server (M1) |
| `udp.discovery.enabled`, `udp.discovery.port`, `udp.discovery.broadcast.address`, `udp.discovery.timeout.ms` | discovery (M3) |
| `multicast.enabled`, `multicast.group`, `multicast.port`, `multicast.ttl`, `multicast.interface`, `multicast.unicast.fallback.enabled` | alerts (M3) |
| `heartbeat.enabled`, `heartbeat.interval.ms`, `heartbeat.timeout.ms` | heartbeat (M3) |
| `tls.enabled`, `tls.port`, `tls.protocols`, `tls.keystore.type`, `tls.keystore.path`, `tls.keystore.password.env`, `tls.truststore.path`, `tls.truststore.password.env` | TLS (M4) |
| `http.resource.enabled`, `http.resource.port`, `http.resource.base.path`, `http.client.connect.timeout.ms`, `http.client.read.timeout.ms` | HTTP + URLConnection (M4) |
| `rmi.enabled`, `rmi.registry.port`, `rmi.service.port`, `rmi.service.name` | RMI (M5) |
| `smartrail.resource.enabled`, `smartrail.resource.port`, `smartrail.resource.timeout.ms` | smartrail:// (M5) |

Adding a key: update `ConfigKeys`, `smartrail.properties` and this table in **one** PR approved by the team
(`NetworkConfigTest` fails if a `ConfigKeys` constant is missing from the file).

## TLS material

Generated locally by Member 4's script, never committed:
```bash
export SMARTRAIL_KEYSTORE_PASSWORD='choose-one'      # names come from tls.*.password.env
export SMARTRAIL_TRUSTSTORE_PASSWORD='choose-one'
scripts/generate-dev-certs.sh localhost <server-LAN-IP>
```
The server needs `certs/server-keystore.p12` + `SMARTRAIL_KEYSTORE_PASSWORD`; each TLS client needs
`certs/client-truststore.p12` + `SMARTRAIL_TRUSTSTORE_PASSWORD` (copy the truststore to client machines).

## Railway model (`railway-data.properties`)

Stations, sections (`from`/`to`), routes (ordered sections), trains (name, start station, route) and timetable
(`schedule.TR01=ST01@08:00,...`). Change the scenario here — e.g. the default routes R01 and R03 share SEC_A/SEC_B
so TR01 and TR03 demonstrate a reservation conflict.
