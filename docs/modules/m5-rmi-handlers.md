# Module: RMI, Protocol & Content Handlers (Member 5)

## Purpose and Networking Concepts
Member 5 implements and demonstrates three fundamental networking concepts from the Java standard library:
1. **Java RMI (Remote Method Invocation)**:
   - Dedicated remote RPC mechanism for administrative inspection and control (`RailwayAdminRemote`, `RailwayAdminService`, `RmiAdminServer`, `RmiAdminClient`).
   - Remote interface with serializable parameters/returns, exported on `rmi.service.port` and registered with `LocateRegistry` on `rmi.registry.port`.
   - Thread-safe delegation to authoritative `RailwayService` with client IP/host audit logging via `RemoteServer.getClientHost()`.
2. **Custom URL Protocol Handler (`smartrail://`)**:
   - Registered through Java SPI `java.net.spi.URLStreamHandlerProvider` so `new URI("smartrail://...").toURL()` works seamlessly.
   - `SmartRailURLStreamHandler` and `SmartRailURLConnection` encapsulate socket communication over plain TCP (`smartrail.resource.port`), issuing `GET <path>\n` requests and parsing the `<status> <mimeType>\n` response header.
   - `SmartRailResourceServer`: dedicated server socket serving resource requests with `ResourceResolver`.
3. **SmartRail Content Handlers (`ContentHandler`)**:
   - `SmartRailContentHandlerFactory` maps custom MIME types (`application/x-smartrail-train`, `application/x-smartrail-track`, `application/x-smartrail-schedule`) to typed content handlers.
   - Decoupled from transport: handles payloads from both `smartrail://` (Member 5) and `http://` (Member 4), transforming UTF-8 key-value records into typed domain models (`TrainInfo`, `TrackInfo`, `ScheduleInfo`).

## How to Start / See It
```bash
# Terminal 1: Start Central Server (includes RMI registry and smartrail:// resource server)
scripts/run.sh server

# Terminal 2: Start Controller Dashboard (contains Member 5's RmiHandlerPanel)
scripts/run.sh controller

# Connect via the top panel to localhost:1099, trigger hold/resume commands,
# or inspect resources via smartrail://localhost:5100/trains
```

## Public Classes and Methods
| Class / Method | Input | Output | Notes |
|---|---|---|---|
| `RailwayAdminRemote` | Interface | Interface | Remote interface defining administrative RPCs. All methods throw `RemoteException`. |
| `RailwayAdminService.getActiveTrains()` | none | `List<TrainInfo>` | Queries train snapshots; logs client host. |
| `RailwayAdminService.getTrainInfo(trainId)` | `String trainId` | `TrainInfo` | Returns train details; throws `RemoteException` if train is unknown. |
| `RailwayAdminService.requestSimulatedHold(trainId)` | `String trainId` | `boolean` | Issues hold command to train; throws `RemoteException` if train unknown. |
| `RailwayAdminService.requestResume(trainId)` | `String trainId` | `boolean` | Issues resume command to train. |
| `RailwayAdminService.requestEmergencyStop(trainId)` | `String trainId` | `boolean` | Issues emergency stop command to train. |
| `RailwayAdminService.reportObstruction(secId, reason)` | `String, String` | `void` | Reports track obstruction and triggers multicast alert. |
| `RailwayAdminService.getEventLog()` | none | `List<EventLog.Entry>` | Snapshot of recent server events. |
| `RmiAdminServer.start() / stop()` | none | `void` | Manages `LocateRegistry` lifecycle and remote object export/unexport. |
| `RmiAdminClient.connect(host, port)` | `String, int` | `RailwayAdminRemote` | Performs RMI registry lookup. |
| `SmartRailResourceServer.start() / stop()` | none | `void` | Multi-threaded TCP server on `smartrail.resource.port`. |
| `SmartRailURLStreamHandlerProvider.createURLStreamHandler(proto)` | `String` | `URLStreamHandler` | SPI factory returning handler for `smartrail` scheme. |
| `SmartRailURLConnection.connect()` | none | `void` | Connects TCP socket, sends `GET <path>`, reads status/mime line and body. |
| `SmartRailURLConnection.getContentType()` | none | `String` | Returns MIME type extracted from response status line. |
| `SmartRailURLConnection.getInputStream()` | none | `InputStream` | Returns body stream; throws `FileNotFoundException` (404) or `IOException`. |
| `SmartRailContentHandlerFactory.createContentHandler(mime)` | `String` | `ContentHandler` | Maps MIME type to `TrainContentHandler`, `TrackContentHandler`, or `ScheduleContentHandler`. |
| `TrainContentHandler.getContent(conn)` | `URLConnection` | `List<TrainInfo>` | Decodes payload to typed list; throws `IOException` on malformed payload. |
| `TrackContentHandler.getContent(conn)` | `URLConnection` | `List<TrackInfo>` | Decodes track records into typed `TrackInfo` list. |
| `ScheduleContentHandler.getContent(conn)` | `URLConnection` | `List<ScheduleInfo>` | Decodes schedule records into typed `ScheduleInfo` list. |
| `RmiHandlerPanel` | `NetworkConfig` | `JPanel` | GUI for controller dashboard using `UiThreads` for background execution and EDT safety. |

## Configuration Keys Used
| Configuration Key | Purpose | Default |
|---|---|---|
| `rmi.enabled` | Enables RMI admin service on server | `true` |
| `rmi.registry.port` | Port for RMI `LocateRegistry` | `1099` |
| `rmi.service.port` | Export port for `RailwayAdminService` (0 = anonymous) | `1100` |
| `rmi.service.name` | Binding name in RMI registry | `RailwayAdmin` |
| `server.advertised.host` | Hostname set in `java.rmi.server.hostname` for multi-machine LAN demos | empty (auto) |
| `client.server.host` | Host for client RMI registry lookup | `localhost` |
| `smartrail.resource.enabled` | Enables custom `smartrail://` TCP resource server | `true` |
| `smartrail.resource.port` | TCP port for `smartrail://` resource server | `5100` |
| `smartrail.resource.timeout.ms` | Socket read/connect timeout for resource connections | `3000` |

## Errors and How They Are Handled
- **Server Down / Network Failure**: RMI client calls catch `RemoteException` and display user-friendly notices without crashing or blocking the Swing EDT.
- **Unknown Train / Section**: Methods validate ID existence against `RailwayService` and throw a descriptive `RemoteException` ("Unknown train: ...").
- **Local / Test Invocations**: `RemoteServer.getClientHost()` catches `ServerNotActiveException` and falls back gracefully to `"local"`.
- **404 Not Found / 400 Bad Request**: `SmartRailURLConnection` detects non-200 status codes; 404 throws `FileNotFoundException` and other non-200 statuses throw `IOException`.
- **Malformed MIME Payloads**: `ContentHandler` implementations catch `IllegalArgumentException` from `ResourceCodec` and throw `IOException("Malformed ... payload")` as required by the Java URLConnection API.

## Tests
```bash
mvn test "-Dtest=RailwayAdminServiceTest,SmartRailUrlTest,ContentHandlerTest" "-Dmaven.compiler.release=17"
```

## Evidence (Screenshots / Logs)
- Log entry on RMI RPC: `[RMI] getActiveTrains() from 127.0.0.1`
- Log entry on simulated hold: `[RMI] requestSimulatedHold(TR01) from 127.0.0.1`
- Log entry on smartrail resource request: `[RESOURCE] Request: GET trains/TR01 from /127.0.0.1:4892`
- Test suite results: 13 unit tests across `RailwayAdminServiceTest` (5), `SmartRailUrlTest` (3), and `ContentHandlerTest` (5) passing cleanly with 0 failures and 0 errors.

## Limitations
- Java RMI is intentionally scoped to low-volume administrator commands and status queries; train telemetry and continuous coordinates are transmitted over high-throughput TCP/TLS streams.
- The `smartrail://` protocol operates on a single request-response exchange per TCP connection (connection is closed by server after sending body).
