# Architecture

## Processes

| Process | Launcher | Contains |
|---|---|---|
| Central railway control server | `app/ServerApp` | All server services + server window |
| Train client (one per train, 2–3 running) | `app/TrainClientApp <TRAIN_ID>` | TCP/TLS client, simulator, heartbeat sender, alert subscriber, train window |
| Controller / administrator dashboard | `app/ControllerApp` | UDP discovery, URLConnection client, RMI client, smartrail:// + content handlers |

## Server wiring (`ServerApp`)

```
NetworkConfig + RailwayData + EventLog
        │
MulticastAlertPublisher (M3, AlertService)
        │
RailwayServiceImpl (M1, RailwayService)  ← authoritative state
        │
HeartbeatMonitor (M3, HeartbeatTracker) ── markInactive() ──► RailwayService
        │
TrainMessageRouter (M1, MessageHandler) ── recordHeartbeat() ──► HeartbeatTracker
        │
ServerContext ──► every NetworkService:
   NioTrainServer (M1)        TCP  nio.tcp.port        Selector, non-blocking
   TlsTrainServer (M4)        TLS  tls.port            SSLServerSocket, thread per client
   DiscoveryResponder (M3)    UDP  udp.discovery.port
   MulticastAlertPublisher(M3)UDP  multicast.group:port
   HeartbeatMonitor (M3)      timer
   ResourceHttpServer (M4)    HTTP http.resource.port  /api/...
   SmartRailResourceServer(M5)TCP  smartrail.resource.port
   RmiAdminServer (M5)        RMI  rmi.registry.port / rmi.service.port
```

Each service is started independently; a failure is logged as `NOT AVAILABLE` and does not stop the others.

## Why two train-control channels (NIO + TLS)

The course requires **Java NIO**, and the design requires **TLS** for train traffic. TLS on a NIO `Selector`
needs `SSLEngine`, which is complex and would force Members 1 and 4 to edit the same code. Instead:

- **Member 1** runs the plain channel on a real NIO `Selector` server.
- **Member 4** runs the secure channel on `SSLServerSocket`.
- Both decode the same line protocol and hand every message to the **same** `MessageHandler`
  (`TrainMessageRouter`), wrapping their socket in a `TrainConnection`. Server logic exists once.
- The train client chooses with `client.transport=PLAIN|TLS` through the `ClientSocketFactory` interface.

## Shared contracts (frozen after Phase 0)

| Contract | Purpose | Implemented by | Used by |
|---|---|---|---|
| `RailwayService` | state queries + commands | M1 `RailwayServiceImpl` | M3, M4, M5, UI |
| `MessageHandler` | process decoded train messages | M1 `TrainMessageRouter` | M1 NIO, M4 TLS |
| `TrainConnection` | reply/push to a train | M1 `NioTrainConnection`, M4 `TlsTrainConnection` | M1 |
| `ClientSocketFactory` | open plain/TLS client socket | M2 `PlainSocketFactory`, M4 `TlsSocketFactory` | M2 `TrainClient` |
| `AlertService` | emergency alerts | M3 `MulticastAlertPublisher` | M1 |
| `HeartbeatTracker` | liveness | M3 `HeartbeatMonitor` | M1 router |
| `NetworkService` | start/stop a server service | all server services | `ServerApp` |
| `ResourceResolver` / `ResourceCodec` | resource payloads | shared (baseline) | M4 HTTP, M5 smartrail:// + content handlers |

Until Member 1's implementation is ready, other members test against `testsupport/FakeRailwayService`.

## End-to-end flows

1. **Discover** – train/controller broadcasts `DISCOVER` (UDP, M3) → `DISCOVER_REPLY` with host and ports; manual IP fallback (M2).
2. **Connect** – `InetAddress` resolution (M2) → plain socket (M2) or TLS handshake (M4).
3. **Register** – `REGISTER` → `REGISTER_OK` + `ASSIGN_ROUTE` (M1).
4. **Run** – `REQUEST_SECTION` → `SECTION_GRANTED/DENIED` (atomic, M1); `POSITION` → `ACK`; `HEARTBEAT` every interval (M3).
5. **Fail** – client stops → no heartbeat → `INACTIVE` after timeout (M3 → M1).
6. **Obstruction** – admin `reportObstruction` over RMI (M5) → `RailwayService` (M1) → multicast `EMERGENCY_ALERT` (M3) → all trains.
7. **Control** – admin `requestSimulatedHold(TR02)` over RMI (M5) → M1 pushes `HOLD|TR02` over that train's TCP/TLS connection → train window shows HELD (M2).
8. **Resources** – dashboard GET `/api/schedules` via `URLConnection` (M4); `smartrail://host:5100/trains/TR01` via custom handler (M5); the content handler (M5) turns `application/x-smartrail-*` into `TrainInfo`/`TrackInfo`/`ScheduleInfo`.

## Threads (no blocking on the Swing EDT)

| Thread | Owner |
|---|---|
| NIO selector loop (1 thread) | M1 |
| TLS accept loop + one thread per TLS client | M4 |
| Train client reader thread, simulator scheduler | M2 |
| UDP discovery loop, multicast receive loop, heartbeat schedulers | M3 |
| HttpServer executor, smartrail resource accept loop, RMI runtime threads | M4, M5 |
| UI background work: `UiThreads.runInBackground`; UI updates: `UiThreads.onEdt` | all |

## Foundational concepts covered in the report (not separate features)

LAN and client-server model (the architecture itself), OSI/TCP-IP layers (where TCP, UDP, TLS, HTTP, RMI sit),
public/private IPv4 and IPv6 (M2 address description), MAC addresses (link layer; deliberately not used as
train identity), DNS (through `InetAddress` resolution), and transmission media (LAN/Wi-Fi test environment).
