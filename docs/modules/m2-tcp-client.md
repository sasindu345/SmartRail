# Module: TCP client & IP addressing (Member 2)

## Purpose and networking concept

Two networking concepts demonstrated directly on the train client side:

1. **IP addressing & host resolution (`InetAddress`, `NetworkInterface`, ports)** — `ServerAddressResolver` resolves host names and literal IPv4/IPv6 addresses (`InetAddress.getByName`, `getAllByName`, `getLocalHost`), classifies each address (`IPv4`/`IPv6` and `loopback`, `site-local/private`, `link-local`, `multicast`, or `public`), and lists active local network interfaces (`NetworkInterface.getNetworkInterfaces()` with `isUp()`).
2. **TCP client sockets & Java I/O streams (`Socket`, UTF-8 reader/writer)** — `PlainSocketFactory` opens a plain TCP `Socket` to an `InetSocketAddress` with `client.connect.timeout.ms`, enabling `TCP_NODELAY` and `SO_KEEPALIVE`. `TrainClient` wraps the socket streams in UTF-8 `BufferedReader` and `BufferedWriter` (`ProtocolCodec.CHARSET`), sends `REGISTER|<trainId>|<stationId>`, decodes incoming `\n`-delimited lines on a daemon reader thread, reports clear errors, and retries dropped connections. `TrainSimulator` drives the train along its assigned route.

```
TrainClientApp
  │
  ├─► ServerAddressResolver.resolve(host)   ──► InetAddress + family/scope log
  ├─► ClientSocketFactory.connect(addr, ms) ──► PlainSocketFactory (or M4 TlsSocketFactory)
  └─► TrainClient
        ├── BufferedWriter (UTF-8, thread-safe send) ──► REGISTER, REQUEST_SECTION, POSITION, RELEASE_SECTION
        └── reader thread (BufferedReader.readLine)  ──► ProtocolCodec.decode ──► TrainSimulator / listeners
```

## How to start / see it

```bash
scripts/run.sh controlcenter
```
Click **Start Server**, then launch `+TR01` and `+TR02` (or `+TR03 TLS`, which swaps only `ClientSocketFactory` to M4's `TlsSocketFactory`). Each child JVM runs `TrainClientApp` headlessly and logs resolution, connection, and sent/received protocol lines.

Headless LAN run (two machines):
```bash
scripts/run.sh server
scripts/run.sh train TR01 -Dclient.server.host=<server-IP>
```

## Public classes and methods

| Class / method | Input | Output | Notes |
|---|---|---|---|
| `ServerAddressResolver.resolve(host)` | host name or IPv4/IPv6 literal | `InetAddress` | throws `UnknownHostException("Unknown host: ...")` on blank/invalid host |
| `ServerAddressResolver.resolveAll(host)` | host name or literal | `List<InetAddress>` | wraps `InetAddress.getAllByName` |
| `ServerAddressResolver.localHost()` | none | `InetAddress` | wraps `InetAddress.getLocalHost` |
| `ServerAddressResolver.describe(address)` | `InetAddress` | formatted string | e.g. `127.0.0.1 (IPv4, loopback)` |
| `ServerAddressResolver.localInterfaceAddresses()` | none | `List<String>` | `isUp()` interfaces only via `NetworkInterface` |
| `PlainSocketFactory.connect(address, timeoutMs)` | `InetSocketAddress`, timeout ms | connected `Socket` | sets `TCP_NODELAY` and `SO_KEEPALIVE`; closes socket if setup fails |
| `TrainClient.connect(host, port)` | host string, TCP port | connects and sends `REGISTER` | retries `client.reconnect.attempts` × `client.reconnect.delay.ms` |
| `TrainClient.send(message)` | `Message` | writes one UTF-8 line + `\n` | synchronized on `writeLock`; throws `IllegalStateException` if disconnected |
| `TrainClient.disconnect()` | none | closes socket, stops reconnect | wakes retry wait immediately; notifies `onDisconnected` at most once |
| `TrainClientListener` | — | callbacks | `onConnected`, `onMessage`, `onDisconnected`, `onError` |
| `TrainSimulator` | `NetworkConfig`, `TrainClient` | drives route protocol | handles `ASSIGN_ROUTE`, `SECTION_GRANTED/DENIED`, `HOLD`, `RESUME`, `EMERGENCY_STOP`, `ARRIVED` |

## Configuration keys used

| Key | Default | Description |
|---|---|---|
| `client.server.host` | `localhost` | Server host/IP passed to `ServerAddressResolver` |
| `client.transport` | `PLAIN` | `PLAIN` (`PlainSocketFactory`) or `TLS` (`TlsSocketFactory`) |
| `client.default.train.id` | `TR01` | Default train ID when no CLI arg is given |
| `client.connect.timeout.ms` | `3000` | Socket connect timeout and `SO_TIMEOUT` for `readLine` |
| `client.reconnect.attempts` | `3` | Maximum connect/reconnect attempts per cycle |
| `client.reconnect.delay.ms` | `2000` | Delay between reconnect attempts |
| `client.simulator.step.ms` | `2000` | Fixed-delay interval for `TrainSimulator` steps |

## Errors and how they are handled

| Condition | Behaviour |
|---|---|
| Unknown or blank host | `ServerAddressResolver` throws `UnknownHostException("Unknown host: ...")`; `TrainClient` reports it via `onError` without stack traces |
| Connection refused | `TrainClient` reports `Could not connect to <host>:<port> (...)` via `onError` and retries up to `client.reconnect.attempts` |
| Connect timeout | `TrainClient` catches `SocketTimeoutException` and reports `Timed out connecting to <host>:<port>` |
| Malformed server line | `ProtocolCodec.decode` throws `ProtocolException`; `TrainClient` reports `Malformed message ignored: ...` via `onError` and keeps reading |
| Dropped connection (EOF / `IOException`) | `TrainClient` closes the socket, notifies `onDisconnected("connection closed")` once, and starts a reconnect attempt |
| `send()` while disconnected | Throws `IllegalStateException("Not connected")`; `TrainSimulator` skips the step and waits for reconnect |

## Tests

```bash
mvn -q test "-Dtest=ServerAddressResolverTest,TrainClientTest"
```

- `ServerAddressResolverTest` (5 tests): `localhost` resolution (`resolve`/`resolveAll`), IPv4 & IPv6 literal resolution (`127.0.0.1`, `::1`), scope classification (`loopback`, `site-local/private`, `link-local`, `multicast`, `public`), unknown/blank/null host errors, and `localHost()` + `localInterfaceAddresses()`.
- `TrainClientTest` (9 tests): `REGISTER` transmission, multi-line decoding, malformed line resilience, single-notification `disconnect()`, unknown host / refused port / timeout errors, reconnect after server restart, concurrent route conflict (`SECTION_DENIED`), and `HOLD` / `EMERGENCY_STOP` / `RESUME` / `ARRIVED` with `RELEASE_SECTION`.

## Evidence

- `mvn -o test "-Dtest=ServerAddressResolverTest,TrainClientTest"`:
  - `ServerAddressResolverTest`: `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`
  - `TrainClientTest`: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`
- Observed log excerpt from `TrainClientTest`:
  ```
  [TRAIN] resolved 127.0.0.1 -> 127.0.0.1 (IPv4, loopback)
  [TRAIN] -> REGISTER|TR01|ST01
  [NIO] Accepted 127.0.0.1:65399
  [NIO] REGISTER TR01 at ST01 from 127.0.0.1:65399
  [NIO] HOLD TR01
  [NIO] EMERGENCY_STOP TR01
  [NIO] RESUME TR01
  [TRAIN] -> POSITION|TR01|ST03|ARRIVED
  [TRAIN] -> RELEASE_SECTION|TR01|SEC_B
  ```

## Limitations

- Once `TrainClient.disconnect()` is called, that client instance is permanently closed; a new session creates a new `TrainClient` (which matches `TrainClientApp`'s process lifecycle).
- Control Center Trains UI (`TrainsPanel`, per-train consoles and controls) is tracked separately in issue **UI-2**; `TcpClientPanel` remains an unused placeholder from the pre-Control-Center design.
