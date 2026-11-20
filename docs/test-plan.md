# Test plan

Automated tests: `mvn verify` (JUnit 5, one test class set per member, `testsupport` fakes, port `0` from
`TestConfig.withOverrides`). Manual/LAN tests below are executed in Phase 5; record **Result** and **Evidence**
(screenshot/log file name) in the table.

| # | Test | Expected result | Owner | Automated test | Result | Evidence |
|---|---|---|---|---|---|---|
| 1 | Multiple TCP clients connect | 3 trains register; server tracks each separately | 1 & 2 | `NioTrainServerTest` | | |
| 2 | Partial / merged TCP reads | partial lines buffered, 2 lines in one read both handled | 1 | `LineFrameDecoderTest` | | |
| 3 | Malformed / oversized message | `ERROR\|code\|detail`, connection survives (or closed for oversize) | 1 | `TrainMessageRouterTest` | | |
| 4 | Reservation race | only one of two concurrent requests is granted | 1 | `TrackReservationServiceTest` | | |
| 5 | Wrong host / port | clear message, UI stays responsive | 2 | `TrainClientTest` | | |
| 6 | IPv4 / IPv6 resolution | address type shown correctly | 2 | `ServerAddressResolverTest` | | |
| 7 | Reconnect | client reconnects after server restart within configured attempts | 2 | `TrainClientTest` | | |
| 8 | UDP discovery | reply with host + ports | 3 | `DiscoveryTest` | PASS | `mvn verify` green; real 2-process run: `DiscoveredServer[host=192.168.8.106, plainPort=5000, tlsPort=5443, protocolVersion=1]` (see `docs/modules/m3-udp-multicast.md`) |
| 9 | UDP discovery timeout | "no response" + manual IP entry | 3 | `DiscoveryTest` | PASS | `timeoutReturnsEmpty` green; `UdpMulticastPanel` shows "No response - timed out. Enter the server IP manually..." |
| 10 | Multicast alert | 2 subscribers receive one alert; fallback documented | 3 | `MulticastAlertTest` | PARTIAL | Logic verified: `nonAlertDatagramsAreIgnored` and `unicastFallbackReachesConnectedTrains` PASS. `twoSubscribersReceiveOneAlert` (real multicast send/receive) **skipped** on the dev machine used — no multicast-capable route (active VPN). Fallback documented in `docs/modules/m3-udp-multicast.md`; re-run on the real demo LAN to get a full PASS |
| 11 | Heartbeat timeout | stopped client INACTIVE after `heartbeat.timeout.ms` | 3 | `HeartbeatMonitorTest` | PASS | `silentTrainIsMarkedInactive` and `heartbeatKeepsTrainAlive` green against `FakeRailwayService`; full server-side trigger (`RailwayServiceImpl.markInactive`) blocked on Member 1's implementation |
| 12 | TLS trusted certificate | handshake succeeds; protocol/cipher shown | 4 | `TlsChannelTest` | | |
| 13 | TLS untrusted certificate | connection rejected, no bypass | 4 | `TlsChannelTest` | | |
| 14 | URLConnection resource | schedules/tracks fetched; 404 handled | 4 | `ResourceHttpServerTest` | | |
| 15 | RMI remote method | expected result; RemoteException handled | 5 | `RailwayAdminServiceTest` | PASS | Unit tests verify active trains retrieval, simulated hold/resume/emergency command delegation, unknown ID RemoteException, and caller host logging. |
| 16 | smartrail:// URL | resolves a real resource from the SmartRail server | 5 | `SmartRailUrlTest` | PASS | Unit tests verify URLStreamHandlerProvider ServiceLoader registration, TCP GET request/status parsing, and 404/IOException on missing resources. |
| 17 | Content handler | payload → typed `TrainInfo`/`TrackInfo` | 5 | `ContentHandlerTest` | PASS | Unit tests verify MIME type factory mapping, UTF-8 payload parsing to TrainInfo/TrackInfo/ScheduleInfo, and IOException on malformed payload. |
| 18 | Full integrated run (LAN, ≥2 machines) | checkpoints C1–C8 all work together | All | manual | | |

Shared contract tests (baseline, must always pass): `ProtocolCodecTest`, `NetworkConfigTest`, `RailwayDataTest`,
`ResourceCodecTest`.
