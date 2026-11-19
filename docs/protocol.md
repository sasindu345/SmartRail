# Protocols (shared contract — frozen after Phase 0)

## 1. Train-control line protocol (TCP via NIO, or TLS)

- UTF-8 text, **one message per line**, terminated by `\n` (a trailing `\r` is tolerated).
- Format: `TYPE|field1|field2…` — fields may not be empty or contain `|`, `\r`, `\n`.
- Maximum line size: `protocol.max.message.bytes` (reject + `ERROR|MESSAGE_TOO_LARGE|…`).
- TCP is a byte stream: receivers **must** buffer partial lines and handle several lines per read.
- Code: `MessageType` (names + field counts), `Message` (validated), `ProtocolCodec` (encode/decode one line).
- The server is authoritative: clients *request*; only the server changes section ownership.

| Message | Direction | Fields | Example |
|---|---|---|---|
| `REGISTER` | train → server | trainId, stationId | `REGISTER\|TR01\|ST01` |
| `REGISTER_OK` | server → train | trainId | `REGISTER_OK\|TR01` |
| `ASSIGN_ROUTE` | server → train | trainId, routeId, sections (comma list) | `ASSIGN_ROUTE\|TR01\|R01\|SEC_A,SEC_B` |
| `POSITION` | train → server | trainId, location, status | `POSITION\|TR01\|SEC_A\|MOVING` |
| `ACK` | server → train | trainId, acknowledged type | `ACK\|TR01\|POSITION` |
| `REQUEST_SECTION` | train → server | trainId, sectionId | `REQUEST_SECTION\|TR01\|SEC_B` |
| `SECTION_GRANTED` | server → train | trainId, sectionId | `SECTION_GRANTED\|TR01\|SEC_B` |
| `SECTION_DENIED` | server → train | trainId, sectionId, reason | `SECTION_DENIED\|TR01\|SEC_B\|RESERVED_BY_TR02` |
| `RELEASE_SECTION` | train → server | trainId, sectionId | `RELEASE_SECTION\|TR01\|SEC_A` (reply `ACK\|TR01\|RELEASE_SECTION`) |
| `HEARTBEAT` | train → server | trainId | `HEARTBEAT\|TR01` (no reply) |
| `HOLD` / `RESUME` / `EMERGENCY_STOP` | server → train | trainId | `HOLD\|TR02` |
| `ERROR` | either | code (`ErrorCode`), detail | `ERROR\|NOT_REGISTERED\|send REGISTER first` |

`status` values = `TrainStatus` enum. Denial reasons: `RESERVED_BY_<trainId>`, `UNKNOWN_SECTION`, `NOT_ON_ROUTE`.

**Validation (Member 1 router):** known type and field count (codec); first message on a connection must be
`REGISTER`; trainId must match the train registered on *that* connection (`IDENTITY_MISMATCH`); trains may only
send train→server types (`ILLEGAL_STATE`); unknown train/section → `UNKNOWN_TRAIN` / `UNKNOWN_SECTION`.

### Typical session
```
→ REGISTER|TR01|ST01
← REGISTER_OK|TR01
← ASSIGN_ROUTE|TR01|R01|SEC_A,SEC_B
→ REQUEST_SECTION|TR01|SEC_A
← SECTION_GRANTED|TR01|SEC_A
→ POSITION|TR01|SEC_A|MOVING
← ACK|TR01|POSITION
→ HEARTBEAT|TR01                (every heartbeat.interval.ms)
← HOLD|TR01                     (pushed when the admin holds TR01)
```

## 2. UDP discovery (Member 3)

One datagram each way, same text format (no newline needed).

| Datagram | Sent to | Example |
|---|---|---|
| `DISCOVER\|serviceName\|version` | `udp.discovery.broadcast.address`:`udp.discovery.port` | `DISCOVER\|SMARTRAIL\|1` |
| `DISCOVER_REPLY\|serviceName\|version\|host\|plainPort\|tlsPort` | the requester's address/port | `DISCOVER_REPLY\|SMARTRAIL\|1\|192.168.1.20\|5000\|5443` |

Service name and version come from `protocol.service.name` / `protocol.version`; mismatches are ignored.

## 3. Multicast alerts (Member 3)

`EMERGENCY_ALERT|sectionId|reason` sent to `multicast.group`:`multicast.port` with TTL `multicast.ttl`.
Unicast fallback (`multicast.unicast.fallback.enabled=true`): the same datagram is also sent to every connected
train's IP on `multicast.port`. Example: `EMERGENCY_ALERT|SEC_B|OBSTRUCTION`.

## 4. Resource representation (Members 4 and 5)

Code: `common/resource` (`ResourceTypes`, `ResourcePath`, `ResourceCodec`, `ResourceResolver`).

Paths: `trains`, `trains/{id}`, `tracks`, `tracks/{id}`, `schedules`, `schedules/{trainId}`, `stations`, `stations/{id}`.

| MIME type | Typed object (content handler) |
|---|---|
| `application/x-smartrail-train` | `List<TrainInfo>` |
| `application/x-smartrail-track` | `List<TrackInfo>` |
| `application/x-smartrail-schedule` | `List<ScheduleInfo>` |
| `application/x-smartrail-station` | `List<StationInfo>` (optional handler) |

Body: UTF-8, one `key=value` per line, records separated by a blank line, empty value = null.
```
sectionId=SEC_B
from=ST02
to=ST03
state=RESERVED
holder=TR01
```

### 4a. HTTP endpoints (Member 4)
`GET http://<host>:<http.resource.port><http.resource.base.path>/<path>` → status from `ResourceResolver`
(200/400/404), `Content-Type` = MIME type above. Non-GET → 405.

### 4b. `smartrail://` resource protocol (Member 5)
URL: `smartrail://<host>:<smartrail.resource.port>/<path>` (e.g. `smartrail://localhost:5100/trains/TR01`).
Over a plain TCP socket, one request per connection:
```
client → GET trains/TR01\n
server → 200 application/x-smartrail-train\n
server → <body>            (then closes the connection)
```
Errors: `400 text/plain` / `404 text/plain` + message. The client `URLConnection` maps non-200 to an `IOException`.

## 5. RMI (Member 5)

Registry `rmi.registry.port`, name `rmi.service.name`, object exported on `rmi.service.port`.
Interface `rmi/RailwayAdminRemote` — all parameter/return types are `Serializable` records from `common.model`.
RMI is for low-volume administration only; train telemetry never goes over RMI.
