# SmartRail — Railway Traffic Coordination Simulator

A five-member **Java Network Programming** project. A central Java server coordinates independent train
clients, predefined routes and exclusive track sections; a controller dashboard monitors and administers
the network.

> **Educational simulation only.** SmartRail is not railway signalling or safety software and must never be
> connected to real railway equipment.

## Networking concepts and who demonstrates them

| # | Member focus | Concepts (Java APIs) | Package(s) |
|---|---|---|---|
| 1 | NIO server & coordination | **Java NIO** `ServerSocketChannel`, `Selector`, `ByteBuffer`; TCP framing; atomic reservations | `nio`, `coordination` |
| 2 | Train client & IP | **TCP client** `Socket` + I/O streams; **IP addressing** `InetAddress`, IPv4/IPv6, ports | `tcpclient`, `ui/shell`, `ui/theme`, `ui/components` |
| 3 | UDP & multicast | **UDP** `DatagramSocket` discovery; **multicast** alerts; **heartbeat** timeout | `udp`, `multicast`, `heartbeat` |
| 4 | Security & resources | **TLS** `SSLContext`, `SSLServerSocket`, certificates; **URL/URLConnection** + HTTP | `tls`, `urlconnection` |
| 5 | Remote admin & handlers | **RMI**; custom **protocol handler** `smartrail://`; **content handlers** | `rmi`, `protocolhandler`, `contenthandler` |

## Architecture in one picture

```
 Train client (M2)  --plain TCP-->  :5000  NioTrainServer (M1, Selector) --+
 Train client (M2)  --TLS------->   :5443  TlsTrainServer (M4)          --+--> TrainMessageRouter (M1)
        ^   |                                                                    |
        |   +--UDP DISCOVER--> :5001 DiscoveryResponder (M3)                      v
        +------multicast 230.0.0.1:5002 <-- MulticastAlertPublisher (M3) <-- RailwayServiceImpl (M1)
                                                 HeartbeatMonitor (M3) -------->  ^   ^   ^
 Controller dashboard --URLConnection--> :8080/api  ResourceHttpServer (M4) -----+   |   |
                      --smartrail://---> :5100      SmartRailResourceServer (M5) ----+   |
                      --RMI-----------> :1099/1100  RmiAdminServer (M5) ------------------+
```
All ports above are defaults from `config/smartrail.properties` — nothing is hard-coded.
Details: [docs/architecture.md](docs/architecture.md).

## Quick start

Requirements: **JDK 21**, **Maven 3.9+**. Run everything from the project root.

```bash
mvn verify                          # build + tests
scripts/run.sh controlcenter        # the single demo window - everything else is a button
```
The Control Center hosts the server services in-process and launches each train as a **real separate
JVM process**, streaming its console into the window; no terminal is needed during a demonstration.
See [docs/ui/demo-flow.md](docs/ui/demo-flow.md).

For a multi-machine LAN run the original launchers still work:
```bash
scripts/run.sh server               # machine 1: central server
scripts/run.sh train TR01           # machine 2
scripts/run.sh controller           # controller dashboard
```
Windows / IDE: run the `main` classes in `com.smartrail.app` with the project root as working directory, or
`java -cp target/classes com.smartrail.app.ServerApp` after `mvn compile`.

Override any setting per process: `scripts/run.sh train TR02 -Dclient.transport=TLS -Dclient.server.host=192.168.1.20`.
TLS needs local dev certificates first — see [docs/configuration.md](docs/configuration.md#tls-material).

Unimplemented services are reported as `NOT AVAILABLE` at server start-up; everything else keeps running, so
members can integrate one by one.

## Project layout

```
config/                 smartrail.properties (ports, hosts, timeouts) · railway-data.properties (model)
scripts/                run.sh · generate-dev-certs.sh (M4)
certs/                  locally generated keystores (git-ignored)
docs/                   architecture · protocol · configuration · ownership · git-workflow · test-plan · demo-script
docs/plan/              implementation plan: phases + per-member checklists
docs/modules/           one module README per member
docs/ui/                Control Center spec · design system · demo flow
src/main/java/com/smartrail/
  common/               SHARED, frozen contracts: config, protocol, model, service interfaces, resource, log
  app/                  launchers: ServerApp, TrainClientApp, ControllerApp (shared) + ControlCenterApp (M2)
  nio/  coordination/   Member 1
  tcpclient/            Member 2
  udp/  multicast/  heartbeat/          Member 3
  tls/  urlconnection/                  Member 4
  rmi/  protocolhandler/  contenthandler/   Member 5
  ui/shell/ (M2 windows + launcher + shared UiThreads) · ui/theme/ · ui/components/ (M2)
  ui/panels/            one screen per member (Overview/Map M1 · Trains M2 · Alerts+Network M3 · Security M4 · Admin M5)
src/test/java/com/smartrail/   same packages + testsupport/ (fakes for isolated testing)
```

## Team workflow

- UI & demo: [docs/ui/control-center-spec.md](docs/ui/control-center-spec.md) · [design system](docs/ui/design-system.md) · [demo flow](docs/ui/demo-flow.md)
- Plan and checklists: [docs/plan/README.md](docs/plan/README.md)
- Who owns which file: [docs/ownership.md](docs/ownership.md)
- Branches, PRs, contract changes: [docs/git-workflow.md](docs/git-workflow.md)
- AI assistant rules: [AGENTS.md](AGENTS.md), [CLAUDE.md](CLAUDE.md), member agents in `.claude/agents/`
