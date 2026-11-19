# Member 5 — RMI administration, `smartrail://` protocol handler & content handlers

**Concepts you demonstrate:** Java RMI (remote interface, export, registry, lookup, `RemoteException`); custom
protocol handler (`URLStreamHandler` — *how* a `smartrail://` resource is accessed); content handlers
(`ContentHandler` — *how* returned `application/x-smartrail-*` content is interpreted into typed objects).

**Your files:** `rmi/RailwayAdminRemote`, `rmi/RailwayAdminService`, `rmi/RmiAdminServer`, `rmi/RmiAdminClient`,
`protocolhandler/SmartRailURLStreamHandlerProvider`, `protocolhandler/SmartRailURLStreamHandler`,
`protocolhandler/SmartRailURLConnection`, `protocolhandler/SmartRailResourceServer`,
`contenthandler/SmartRailContentHandlerFactory`, `contenthandler/{Train,Track,Schedule}ContentHandler`,
`META-INF/services/java.net.spi.URLStreamHandlerProvider`, `ui/panels/RmiHandlerPanel`,
tests in `src/test/java/com/smartrail/{rmi,protocolhandler,contenthandler}/`, `docs/modules/m5-rmi-handlers.md`.

**You provide:** remote admin API, `smartrail://` URLs, the content handler factory (used by M4's `fetchContent`).
**You use:** `RailwayService` (M1; `FakeRailwayService` until C1), `ResourceResolver`/`ResourceCodec` (shared).
**Agent:** `.claude/agents/m5-rmi-handlers.md` · **Branch prefix:** `m5/`

## Phase 0 — Setup
- [x] Clone, JDK 21, `mvn verify` green; create branch `m5/rmi-admin`
- [x] Read `AGENTS.md`, `docs/protocol.md` §4–5, `RailwayAdminRemote`, `ResourceResolver`
- [x] Agree with M4: same MIME types/paths; your content handlers serve both http:// and smartrail://

## Phase 1 — RMI
- [x] `RailwayAdminService`: delegate to `RailwayService`; unknown id → `RemoteException` with message; log caller host
- [x] `RmiAdminServer`: `java.rmi.server.hostname` from `server.advertised.host` (if set), `createRegistry(rmi.registry.port)`, `exportObject(service, rmi.service.port)`, `rebind(rmi.service.name)`; stop = unbind + unexport
- [x] `RmiAdminClient.connect`: `getRegistry(client.server.host, port).lookup(name)`
- [x] Tests: `RailwayAdminServiceTest` — real registry on a free port + `FakeRailwayService`: getActiveTrains, hold reaches the service (`calls()`), unknown train → `RemoteException`

## Phase 2 — Protocol handler + content handlers
- [x] `SmartRailResourceServer`: `ServerSocket` on `smartrail.resource.port`; `GET <path>` → `ResourceResolver` → `<status> <mime>\n<body>`; 400 on bad request
- [x] `SmartRailURLStreamHandlerProvider` returns the handler for `ResourceTypes.SCHEME`
- [x] `SmartRailURLStreamHandler.openConnection` → `SmartRailURLConnection`; default port from config
- [x] `SmartRailURLConnection`: socket + timeout `smartrail.resource.timeout.ms`, request line, status line → `getContentType`, body → `getInputStream`, non-200 → `IOException`
- [x] `SmartRailContentHandlerFactory` + Train/Track/Schedule handlers: UTF-8 read → `ResourceCodec.decode` → typed list; malformed → `IOException`
- [x] Tests: `SmartRailUrlTest` (URL accepted via `new URI(...).toURL()`, fetch TR01 from a server on a free port, 404 throws), `ContentHandlerTest` (payload → `TrainInfo`/`TrackInfo`, malformed throws)

## Phase 3 — Integration
- [x] C6 with M1/M2: `requestSimulatedHold(TR02)` → TR02 shows HELD; resume works
- [x] C7 with M1/M3: `reportObstruction(SEC_B, OBSTRUCTION)` → alert on all trains
- [x] C8 with M4: `ResourceClient.fetchContent` uses your factory; `smartrail://<host>:5100/trains/TR01` from the dashboard
- [x] RMI from a second machine on the LAN (hostname setting verified)

## Phase 4 — UI & evidence
- [x] `RmiHandlerPanel`: Connect, Active trains table, Track status, Hold/Resume/Emergency stop, Report obstruction, Event log; RemoteException shown clearly
- [x] `smartrail://` box: URL field, Open → content type + typed object's fields
- [x] Screenshots: remote call result, hold reaching the train, typed object from a smartrail URL

## Phase 5 — Test, docs, demo
- [x] Fill `docs/modules/m5-rmi-handlers.md`
- [x] Your rows in `docs/test-plan.md`
- [x] 2-minute demo slot (8:00–10:00): RMI admin, smartrail:// URL, content-handler conversion
- [x] Report section: RMI stubs/registry/serialization; protocol handler vs content handler

## Done when
An admin dashboard on another process (ideally another machine) calls remote methods that change the simulation;
`smartrail://…/trains/TR01` is resolved by your own server and turned into a `TrainInfo` by your content handler.

## Out of scope
Using RMI for telemetry, RMI security managers/codebase downloading, wrapping http:// inside the smartrail handler.
