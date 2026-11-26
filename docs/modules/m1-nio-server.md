# Module: Java NIO server & coordination (Member 1)

## Purpose and networking concept

One selector thread accepts every plain train client (`ServerSocketChannel`, `SocketChannel`, `Selector`,
`ByteBuffer`). TCP is a byte stream, so `LineFrameDecoder` keeps a partial line and returns every
complete `\n` line in a read. `TrainMessageRouter` is the single handler for both this server and the
TLS server. `TrackReservationService.reserve` is an atomic check-and-reserve: two trains cannot both
be granted the same section.

```
clients ──TCP──► ServerSocketChannel
                    │  OP_ACCEPT
                    ▼
              one nio-selector thread
                    │  OP_READ / OP_WRITE
                    ▼
              LineFrameDecoder ──► ProtocolCodec ──► TrainMessageRouter
                                                              │
                                                              ▼
                                                    RailwayServiceImpl
                                                    (registry + routes + reservations)
```

## How to start / see it

```bash
scripts/run.sh controlcenter
```

Click **Start Server**. The status bar TCP/NIO dot turns green and the log shows
`Selector listening on TCP 0.0.0.0:5000`. Overview shows the map and train table.
Track Sections → **Run conflict demo** sends `REQUEST_SECTION|TR01|SEC_A` and
`REQUEST_SECTION|TR03|SEC_A`. One reply is `SECTION_DENIED` and that section id is shown in red.

## Public classes and methods

| Class / method | Input | Output | Notes |
|---|---|---|---|
| `NioTrainServer.start/stop` | `ServerContext` | bound TCP port | non-blocking accept/read/write on one thread |
| `LineFrameDecoder.decode` | flipped `ByteBuffer` | complete lines, or `MESSAGE_TOO_LARGE` | keeps the incomplete tail |
| `NioTrainConnection.send` | `Message` | queued bytes + selector wakeup | flush handles a short write |
| `TrainMessageRouter.onMessage` | connection + `Message` | `REGISTER_OK`, `ASSIGN_ROUTE`, `ACK`, `SECTION_GRANTED` / `SECTION_DENIED`, or `ERROR` | first message must be `REGISTER` |
| `TrackReservationService.reserve` | trainId, sectionId | `ReservationResult` | denial reason `RESERVED_BY_<id>` |
| `RailwayServiceImpl.holdTrain` / `resumeTrain` / `emergencyStop` | trainId | pushes `HOLD` / `RESUME` / `EMERGENCY_STOP` | no-op when that train has no open connection |
| `RailwayServiceImpl.reportObstruction` | sectionId, reason | calls `AlertService.publishAlert` | also logs `OBSTRUCTION` |

## Configuration keys used

`server.bind.address`, `nio.tcp.port`, `nio.selector.timeout.ms`, `protocol.max.message.bytes`.
Train, station and section ids come from `config/railway-data.properties`.

## Errors and how they are handled

| Condition | Reply |
|---|---|
| Line longer than `protocol.max.message.bytes` | `ERROR\|MESSAGE_TOO_LARGE\|…`, then the connection is closed |
| Bad line (unknown type, field count, empty field) | `ERROR\|code\|detail`, connection stays open |
| Any message before `REGISTER` | `ERROR\|NOT_REGISTERED\|send REGISTER first` |
| trainId does not match the train on that connection | `ERROR\|IDENTITY_MISMATCH\|…` |
| Unknown train, or a second live connection for the same train | `ERROR\|UNKNOWN_TRAIN` or `ERROR\|ILLEGAL_STATE` |
| Section not on the train's route | `SECTION_DENIED\|…\|NOT_ON_ROUTE` |
| Section already held | `SECTION_DENIED\|…\|RESERVED_BY_<trainId>` |

## Tests

```bash
mvn -q test -Dtest=LineFrameDecoderTest,NioTrainServerTest,TrainMessageRouterTest,TrackReservationServiceTest
```

`NioTrainServerTest` connects three clients to one selector thread on port `0`.
`TrackReservationServiceTest` runs two threads against one section, 100 times; exactly one grant each time.

## Evidence

`NioTrainServerTest` log: three `REGISTER` lines, then `INACTIVE` when each client closes.
`LineFrameDecoderTest` covers a split `REGISTER` and two lines in one buffer.
`TrackReservationServiceTest.reservedSectionIsDeniedWithHolder` expects `RESERVED_BY_TR01`.

## Limitations

TLS is a different server (Member 4) and calls this same router. This server does not use `SSLEngine`.
A stopped train becomes `INACTIVE` and its sections are released. The train process itself is Member 2;
until that client sends `REGISTER` on its own, the conflict demo opens the two TCP clients directly.
