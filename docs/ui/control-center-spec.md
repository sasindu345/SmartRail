# SmartRail Control Center — UI specification

> **This file is the authoritative description of the SmartRail user interface.**
> The files listed in §6 are *pre-approved* for creation by their named owner — creating them is the
> one exception to the "never create files" rule in `AGENTS.md` §3.2.

## 1. Why this exists

Until now the demo needed four terminals and opened three bare windows full of
`"(not implemented yet)"` labels. Nobody watching could *see* TCP coordination, a multicast
fan-out or a TLS handshake happen. The Control Center replaces that with **one window** in which
**every button triggers a real network event**, so each member can demonstrate their own concept
without touching a terminal.

**Guiding principle — every widget must visualise a network event. No decorative-only UI.**
A member does not start UI work before their networking concept passes its tests.

## 2. Process model

The Control Center is a single window in a single JVM. It does two things:

1. **Hosts the server services in-process.** The `Start Server` button runs exactly the wiring that
   `app/ServerApp.java` does today (`NetworkConfig` → `RailwayData` → `EventLog` →
   `MulticastAlertPublisher` → `RailwayServiceImpl` → `HeartbeatMonitor` → `TrainMessageRouter` →
   `ServerContext` → the `List<NetworkService>`), starting each service and recording whether it
   started, failed or is stopped. Real ports are bound.
2. **Launches each train as a real, separate JVM process** through `TrainProcessSupervisor`
   (`ProcessBuilder`, `java.home/bin/java`, the current `java.class.path`, running the existing
   `com.smartrail.app.TrainClientApp` with `-Dui.train.window.enabled=false`). Each child's stdout
   is piped back and shown in its own console view inside the window.

```
┌─ SmartRail Control Center  (ONE window, one JVM) ────────────────────────────┐
│ TopBar      SmartRail │ Railway Control     ● Simulation Online   20 Nov 12:44│
├──────────────┬───────────────────────────────────────────────────────────────┤
│ NavSidebar   │ Content (CardLayout)                                          │
│  Overview    │                                                               │
│  Trains      │                                                               │
│  Track Sect. │              … the selected screen …                          │
│  Alerts      │                                                               │
│  Network     │                                                               │
│  Security    │                                                               │
│  Admin       │                                                               │
│  Event Log   │                                                               │
├──────────────┴───────────────────────────────────────────────────────────────┤
│ SimulationControlBar  [▶ Start Server] [+TR01] [+TR02] [+TR03 TLS] [■ Stop All]│
├──────────────────────────────────────────────────────────────────────────────┤
│ NetworkStatusBar    ● TCP/NIO  ● UDP  ● TLS  ● Multicast  ● RMI  ● HTTP       │
└──────────────────────────────────────────────────────────────────────────────┘
        │ in-process (real bound ports)          │ ProcessBuilder (real JVMs)
        ▼                                        ▼
  NioTrainServer · TlsTrainServer ·        TR01.jvm   TR02.jvm   TR03.jvm
  DiscoveryResponder · AlertPublisher ·    (headless TrainClientApp,
  HeartbeatMonitor · ResourceHttpServer ·   stdout piped into the window)
  SmartRailResourceServer · RmiAdminServer
```

**This is still genuine network programming.** The trains are real operating-system processes with
real TCP/TLS sockets to real bound ports; UDP discovery, multicast and RMI all cross real process
boundaries. Nothing is simulated in-memory.

`ServerApp`, `TrainClientApp` and `ControllerApp` keep working unchanged, so a multi-machine LAN
demonstration is still possible (`scripts/run.sh server` on one laptop, trains on another).

## 3. Window chrome (owner: M2)

| Element | Content | Behaviour |
|---|---|---|
| **TopBar** | Product mark, "Railway Control / Railway Traffic Coordination Simulator", a `Simulation Online/Offline` pill, live date-time | the pill reflects whether the server services are running; clock on a `javax.swing.Timer` |
| **NavSidebar** | Overview · Trains · Track Sections · Alerts · Network · Security · Admin · Event Log | selects the screen in a `CardLayout`; selected item highlighted |
| **SimulationControlBar** | `Start/Stop Server`, `+TR01`, `+TR02`, `+TR03 (TLS)`, `Stop All` | the only controls that spawn or stop processes; buttons disable while an action is in flight |
| **NetworkStatusBar** | `TCP/NIO`, `UDP`, `TLS`, `Multicast`, `RMI`, `HTTP` dots | green = service started, red = failed, grey = not started; fed from each service's `start()`/`stop()` result |

Every network action runs through `UiThreads.runInBackground`; every widget update through
`UiThreads.onEdt`. The Swing EDT is never blocked.

## 4. Screens

Each screen belongs to exactly one member, is reached from the sidebar, and must make that member's
networking concept observable.

### 4.1 Overview — owner M1
- **Network map**: the three stations and four sections from `config/railway-data.properties`,
  drawn with `Graphics2D`. Section colour = `SectionState` (FREE grey, RESERVED amber, OCCUPIED
  green). Train chips sit on the section or station they occupy.
- **Train Overview table**: train, current station, next section, status dot.
- **Selected Train card**: ID, `Connected (PLAIN)` / `Connected (TLS)` badge, last update, current
  station, next section, status, and `Hold` / `Resume` / `Emergency Stop` buttons.
- **Attention list**: current problems (denied reservations, inactive trains, active obstructions).
- Refreshes from `RailwayService` on a `javax.swing.Timer`.

### 4.2 Trains — owner M2
- Full train table: status, location, route, transport, remote IP:port, last seen.
- **Per-train console**: one tab or split per launched train, streaming that child JVM's stdout —
  this is what makes a multicast fan-out visible (one alert, three consoles).
- Start/stop individual trains; show the resolved server address (`InetAddress`) actually used.

### 4.3 Track Sections — owner M1
- Section table: ID, from, to, state, holder train.
- `Run conflict demo`: drives TR01 and TR03 (whose routes share `SEC_A`) at the same section so one
  receives `SECTION_DENIED`; the denial is highlighted in the table and on the map.

### 4.4 Alerts — owner M3
- Live feed of received `EMERGENCY_ALERT` messages with arrival time and section.
- `Subscribe` / `Unsubscribe` to the multicast group; `Trigger obstruction` publishes one.
- States the delivery path actually used (multicast, or the unicast fallback when the network
  blocks multicast) — see `docs/modules/m3-udp-multicast.md`.

### 4.5 Network — owner M3 (discovery) + M2 (addressing)
- `Discover server`: sends the UDP broadcast and shows the reply, or the timeout message and the
  manual-entry fallback.
- Services & ports table (name + endpoint for every `NetworkService`).
- This machine's network interfaces with IPv4/IPv6 classification.

### 4.6 Security & Resources — owner M4
- TLS session panel: protocol, cipher suite, peer certificate subject and validity for a connected
  TLS train.
- `Test untrusted certificate`: shows the connection being refused (never bypassed).
- `Fetch schedules` / `Fetch tracks`: retrieves through `URL`/`URLConnection`, showing the HTTP
  status, `Content-Type` and the parsed rows.

### 4.7 Administration — owner M5
- The existing `RmiHandlerPanel` content, restyled into the shell: RMI connect, active trains,
  track status, `Hold`/`Resume`/`Emergency Stop`, report obstruction, server event log.
- `smartrail://` inspector: URL field, `Fetch & Parse`, the resolved `Content-Type` and the typed
  object produced by the content handler.

### 4.8 Event Log — owner M1
- The `EventLog` stream, filterable by source (`NIO`, `UDP`, `MULTICAST`, `TLS`, `RMI`, `SERVER`).

## 5. Concept priority

| Priority | Concepts | Why |
|---|---|---|
| **P0** | NIO server, TCP client, atomic reservation (M1, M2) | nothing else is visible until trains can connect |
| **P1** | UDP discovery (M3), multicast fan-out (M3), TLS badge (M4) | the highest-impact visuals in the demo |
| **P2** | heartbeat → `INACTIVE` (M3), RMI hold → train reacts (M5) | clear cause→effect across real processes |
| **P3** | URLConnection schedules (M4), `smartrail://` typed object (M5) | informational screens |

## 6. Pre-approved files

Creating these files is explicitly allowed for the named owner; everything else still follows
`AGENTS.md` §3.2.

| Owner | Files |
|---|---|
| **M2** (UI-0) | `app/ControlCenterApp.java`; `ui/shell/ControlCenterWindow.java`, `ui/shell/SimulationController.java`, `ui/shell/TrainProcessSupervisor.java`; `ui/theme/Theme.java`; `ui/components/Card.java`, `StatusPill.java`, `NavSidebar.java`, `TopBar.java`, `SimulationControlBar.java`, `NetworkStatusBar.java`, `TableStyles.java` |
| **M1** (UI-1) | `ui/panels/NetworkMapPanel.java`, `ui/panels/TrackSectionsPanel.java`, `ui/panels/EventLogPanel.java` |
| **M2** (UI-2) | `ui/panels/TrainsPanel.java` |
| **M3** (UI-3) | `ui/panels/AlertsPanel.java` |
| **M4 / M5** | none — `TlsResourcePanel` and `RmiHandlerPanel` become their screens |

Paths are relative to `src/main/java/com/smartrail/`.

## 7. Approved changes to otherwise frozen files

Agreed once, as part of this upgrade. Nothing else in `common/**`, `app/**` or `config/**` changes,
and no networking code changes at all.

| File | Change |
|---|---|
| `pom.xml` | add the FlatLaf dependency (the first runtime dependency) |
| `config/smartrail.properties` + `common/config/ConfigKeys.java` | add `ui.train.window.enabled` (default `true`) |
| `app/TrainClientApp.java` | honour that flag when creating `TrainClientWindow` |
| `scripts/run.sh` | add the `controlcenter` role |

`common/service/**`, `common/protocol/**` and `common/model/**` are **not** touched — in particular
`NetworkService` keeps its current four methods; the Control Center records service status itself.

## 8. Build order

`UI-0` (M2's shell) must land first — every other screen plugs into it. `UI-3` waits for PR #7 to
merge to avoid a conflict in `UdpMulticastPanel`. The rest are independent of each other.
