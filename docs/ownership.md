# File ownership map

Every file has exactly one owner. Edit only your own files; shared files change only by team-agreed PR
(see `git-workflow.md`). Paths are relative to `src/main/java/com/smartrail/` unless stated.

## Shared (frozen after Phase 0 — changes need all 5 approvals)

| File | Purpose |
|---|---|
| `common/config/ConfigKeys.java`, `NetworkConfig.java`, `RailwayData.java` | configuration access |
| `common/protocol/MessageType.java`, `Message.java`, `ProtocolCodec.java`, `ProtocolException.java`, `ErrorCode.java` | line protocol |
| `common/model/*.java` | DTO records (Serializable) |
| `common/service/*.java` | cross-member interfaces + `ServerContext` |
| `common/resource/*.java` | resource MIME types, paths, codec, resolver |
| `common/log/EventLog.java` | event log |
| `app/ServerApp.java`, `TrainClientApp.java`, `ControllerApp.java` | launchers / wiring |
| `ui/shell/UiThreads.java` | EDT / background helper |
| `config/*.properties`, `pom.xml`, `scripts/run.sh`, `AGENTS.md`, `CLAUDE.md`, `docs/architecture.md`, `docs/protocol.md`, `docs/configuration.md` | project contracts |
| `src/test/java/com/smartrail/testsupport/*`, `src/test/java/com/smartrail/common/**` | shared test support |

## Member 1 — Java NIO server & coordination

| File | Concept shown |
|---|---|
| `nio/NioTrainServer.java` | `ServerSocketChannel`, `Selector`, non-blocking accept/read/write |
| `nio/NioTrainConnection.java` | per-client `SocketChannel`, write queue, OP_WRITE |
| `nio/LineFrameDecoder.java` | framing a TCP byte stream with `ByteBuffer` |
| `coordination/TrainMessageRouter.java` | application protocol handling (shared by NIO + TLS) |
| `coordination/RailwayServiceImpl.java` | authoritative server state |
| `coordination/TrainRegistry.java` | client registry |
| `coordination/TrackReservationService.java` | atomic check-and-reserve |
| `coordination/RouteService.java` | route assignment |
| `ui/panels/NioServerPanel.java` | server panel |
| tests: `nio/*Test`, `coordination/*Test` · doc: `docs/modules/m1-nio-server.md` | |

## Member 2 — TCP client, IP addressing & app shell

| File | Concept shown |
|---|---|
| `tcpclient/ServerAddressResolver.java` | `InetAddress`, IPv4/IPv6, `NetworkInterface` |
| `tcpclient/PlainSocketFactory.java` | `Socket` connect with timeout |
| `tcpclient/TrainClient.java` | TCP client, I/O streams, reader thread, reconnect |
| `tcpclient/TrainClientListener.java` | client callbacks |
| `tcpclient/TrainSimulator.java` | request/response driven train behaviour |
| `ui/shell/ServerWindow.java`, `TrainClientWindow.java`, `ControllerDashboard.java` | window shells (host others' panels) |
| `ui/panels/TcpClientPanel.java` | train client panel |
| tests: `tcpclient/*Test` · doc: `docs/modules/m2-tcp-client.md` | |

## Member 3 — UDP, multicast & heartbeat

| File | Concept shown |
|---|---|
| `udp/DiscoveryResponder.java` | `DatagramSocket` server, request/reply |
| `udp/DiscoveryClient.java` | broadcast + receive timeout |
| `udp/DiscoveredServer.java` | discovery result |
| `multicast/MulticastAlertPublisher.java` | multicast send, TTL, unicast fallback |
| `multicast/MulticastAlertSubscriber.java` | `joinGroup` / `leaveGroup` with `NetworkInterface` |
| `heartbeat/HeartbeatMonitor.java` | timeout detection |
| `heartbeat/HeartbeatSender.java` | periodic heartbeat |
| `ui/panels/UdpMulticastPanel.java` | discovery/alerts panel |
| tests: `udp/*Test`, `multicast/*Test`, `heartbeat/*Test` · doc: `docs/modules/m3-udp-multicast.md` | |

## Member 4 — TLS & URL/URLConnection

| File | Concept shown |
|---|---|
| `tls/SslContextFactory.java` | `KeyStore`, `KeyManagerFactory`, `TrustManagerFactory`, `SSLContext` |
| `tls/TlsTrainServer.java` | `SSLServerSocket`, handshake, session info |
| `tls/TlsTrainConnection.java` | TLS `TrainConnection` |
| `tls/TlsSocketFactory.java` | `SSLSocket`, host name verification |
| `urlconnection/ResourceHttpServer.java` | HTTP resource endpoints |
| `urlconnection/ResourceClient.java` | `URL`, `URLConnection`, `HttpURLConnection` |
| `ui/panels/TlsResourcePanel.java` | TLS/resource panel |
| `scripts/generate-dev-certs.sh`, `certs/README.md` | keytool dev certificates |
| tests: `tls/*Test`, `urlconnection/*Test` · doc: `docs/modules/m4-tls-urlconnection.md` | |

## Member 5 — RMI, protocol handler & content handlers

| File | Concept shown |
|---|---|
| `rmi/RailwayAdminRemote.java` | `Remote` interface |
| `rmi/RailwayAdminService.java` | remote object implementation |
| `rmi/RmiAdminServer.java` | registry + export |
| `rmi/RmiAdminClient.java` | registry lookup |
| `protocolhandler/SmartRailURLStreamHandlerProvider.java` + `src/main/resources/META-INF/services/java.net.spi.URLStreamHandlerProvider` | handler registration |
| `protocolhandler/SmartRailURLStreamHandler.java` | `URLStreamHandler` |
| `protocolhandler/SmartRailURLConnection.java` | custom `URLConnection` |
| `protocolhandler/SmartRailResourceServer.java` | server side of `smartrail://` |
| `contenthandler/SmartRailContentHandlerFactory.java` | `ContentHandlerFactory` |
| `contenthandler/TrainContentHandler.java`, `TrackContentHandler.java`, `ScheduleContentHandler.java` | `ContentHandler` |
| `ui/panels/RmiHandlerPanel.java` | admin/handler panel |
| tests: `rmi/*Test`, `protocolhandler/*Test`, `contenthandler/*Test` · doc: `docs/modules/m5-rmi-handlers.md` | |

## Team-shared documents (anyone may edit their own section)
`docs/test-plan.md` (each member fills their rows), `docs/demo-script.md` (each member their 2-minute slot),
`docs/plan/README.md` (team checkpoints).
