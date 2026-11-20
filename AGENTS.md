# AGENTS.md — rules for every AI coding agent working on SmartRail

These rules apply to Claude Code, Codex, Copilot, Cursor and any other assistant.
Human team members follow the same rules (see `docs/git-workflow.md`).

## 1. What this project is

SmartRail is a **5-member university Java Network Programming project**: an *educational* railway
traffic coordination **simulator**. A central server coordinates simulated train clients and exclusive
track sections. It must never be described as, or connected to, real railway equipment.

Assessment requirements (these decide what is in scope):
- Uses the `java.net` package and **must demonstrate Java NIO** (Selector-based server, Member 1).
- **Each member individually implements and demonstrates at least one networking concept.**
- Swing frontend is allowed but is not the focus; networking behaviour must be observable.

The application is demonstrated through **one window**, the SmartRail Control Center: it hosts the
server services in-process and launches each train as a real separate JVM process, so the whole
demo is button clicks instead of terminal commands. Specification: `docs/ui/control-center-spec.md`.

**UI principle — every widget must visualise a network event. No decorative-only UI.**
No member starts UI work before their networking concept passes its tests.

## 2. Networking concept → member → package map (the core of the project)

| Member | Networking concept(s) | Packages / files they own |
|---|---|---|
| 1 | **Java NIO** (ServerSocketChannel, Selector, ByteBuffer), TCP server, message framing, server-side coordination | `nio/`, `coordination/`, `ui/panels/NioServerPanel.java`, `NetworkMapPanel.java`, `TrackSectionsPanel.java`, `EventLogPanel.java` |
| 2 | **TCP client sockets + I/O streams**, **IP addressing / InetAddress / ports** | `tcpclient/`, `ui/shell/ServerWindow.java`, `TrainClientWindow.java`, `ControllerDashboard.java`, `ui/panels/TcpClientPanel.java`, `TrainsPanel.java` |
| 3 | **UDP datagrams** (discovery), **IP multicast** (alerts), **heartbeat** availability, **Control Center shell foundation** | `udp/`, `multicast/`, `heartbeat/`, `ui/panels/UdpMulticastPanel.java`, `AlertsPanel.java`, `app/ControlCenterApp.java`, `ui/shell/ControlCenterWindow.java`, `SimulationController.java`, `TrainProcessSupervisor.java`, `ui/theme/`, `ui/components/` |
| 4 | **TLS** (SSLContext, SSLServerSocket/SSLSocket, keystores), **URL / URLConnection** + HTTP resources | `tls/`, `urlconnection/`, `scripts/generate-dev-certs.sh`, `ui/panels/TlsResourcePanel.java` |
| 5 | **Java RMI**, **custom protocol handler** (`smartrail://`), **content handlers** | `rmi/`, `protocolhandler/`, `contenthandler/`, `META-INF/services/…`, `ui/panels/RmiHandlerPanel.java` |
| Shared | Contracts used by everyone — **frozen** | `common/**`, `app/**` (except `ControlCenterApp`), `ui/shell/UiThreads.java`, `config/**`, `pom.xml` |

Full file-by-file list: `docs/ownership.md`. Each member's plan and checklist: `docs/plan/member-N-*.md`.

## 3. Hard rules

### 3.1 Scope — keep it simple
- Implement **only** what `docs/plan/` and `docs/ui/control-center-spec.md` describe. Do **not** add complex
  features unless the user explicitly asks: no database, no Spring/Netty/Jetty/any framework, no JSON/XML
  libraries, no web frontend, no login/auth system, no SSLEngine-over-NIO, no animation, no cloud deployment,
  no real railway data.
- **JDK APIs only** at runtime (`java.net`, `java.nio`, `javax.net.ssl`, `java.rmi`, `com.sun.net.httpserver`,
  Swing) **plus FlatLaf**, the one approved UI dependency (`docs/ui/design-system.md`). JUnit 5 is the only
  test dependency. Never add any other dependency to `pom.xml` without user approval.
- The railway **network map is required** (Member 1, `ui/panels/NetworkMapPanel.java`): it visualises track
  reservation state, which is a networking result, not decoration.
- Prefer the most direct use of the networking API being demonstrated — the code is read in a presentation.

### 3.2 Never change the file structure unless the user explicitly asks
- Do **not** create, move, rename or delete files, packages or directories. Implement inside the existing files.
- **One standing exception:** the Control Center files listed in `docs/ui/control-center-spec.md` §6 are
  pre-approved — but only for the owner named there, and only those exact paths.
- Do **not** rename public classes, the base package `com.smartrail`, or the `config/` keys.
- If something truly cannot fit, **stop and tell the user** what file you need and why.

### 3.3 Never hard-code anything
- Ports, hosts, IPs, multicast group, timeouts, intervals, file paths, train/station/section/route IDs,
  service names and versions come from `config/smartrail.properties` (via `NetworkConfig` + `ConfigKeys`) or
  `config/railway-data.properties` (via `RailwayData`).
- Secrets (keystore/truststore passwords) come from **environment variables** named in config
  (`config.secret(ConfigKeys.TLS_KEYSTORE_PASSWORD_ENV)`). Never write a password in code, config, tests or docs.
- Tests use `TestConfig.withOverrides(...)` (port `0` = OS-chosen free port) instead of literals.
- Protocol message names live only in `MessageType`; MIME types/scheme only in `ResourceTypes`.

### 3.4 Respect ownership and shared contracts
- Work only in the files of the member/concept the user is working on (see table above).
- `common/**`, `app/**`, `UiThreads`, `config/**` and `pom.xml` are **frozen shared contracts**. Do not edit them
  unless the user explicitly asks; then the change must be agreed by the team in one PR that updates the docs too.
- Four changes to those frozen files are **already agreed** for the Control Center and need no further sign-off
  (`docs/ui/control-center-spec.md` §7): the FlatLaf dependency in `pom.xml`; the `ui.train.window.enabled` key
  in `config/smartrail.properties` + `ConfigKeys`; honouring that flag in `app/TrainClientApp.java`; and the
  `controlcenter` role in `scripts/run.sh`. Nothing else there may change — in particular `common/service/**`,
  `common/protocol/**` and `common/model/**` stay exactly as they are.
- Cross-module calls go **only through the interfaces in `common/service`** (`RailwayService`, `MessageHandler`,
  `TrainConnection`, `ClientSocketFactory`, `AlertService`, `HeartbeatTracker`, `NetworkService`).
  Never reach into another member's internals.

### 3.5 Networking correctness
- **NIO is real NIO**: non-blocking `ServerSocketChannel`/`SocketChannel` + `Selector` + `ByteBuffer`. A blocking
  `ServerSocket` must never be described as NIO.
- TCP is a byte stream: buffer partial reads, handle several messages per read, enforce `protocol.max.message.bytes`.
- Every socket has a connect/read timeout from config. Close every socket/stream (try-with-resources).
- Shared server state is thread-safe (NIO thread, TLS threads, RMI threads and timers call it concurrently).
  Section reservation is an **atomic** check-and-reserve.
- **Never disable TLS certificate or host name verification** (no trust-all `TrustManager`, no
  `HostnameVerifier` returning `true`), even "just for the demo".
- Multicast is only for short alerts; train telemetry stays on TCP/TLS; RMI is only for low-volume admin calls.
- Use `InetAddress`/`InetSocketAddress`; do not assume IPv4-only.
- **Never block the Swing EDT** with network work: use `UiThreads.runInBackground` and `UiThreads.onEdt`.
- Log meaningful network events to `EventLog` (connections, handshakes, datagrams, remote calls, errors).

### 3.6 Control Center UI
Applies to every `ui/**` change; the full spec is `docs/ui/control-center-spec.md` and the visual rules are
`docs/ui/design-system.md`.
- **Every widget visualises a network event.** No decorative panels, no filler, no fake or placeholder data.
- A service that is unimplemented or failed to start is shown grey/red **with the reason** — never hidden.
- Compose the shared components (`Card`, `StatusPill`, `TableStyles`, …) and the `Theme` tokens. Do not set
  your own colours, fonts or borders in a panel; if a component is missing, ask Member 3 to add it.
- Child train processes are started **only** through Member 3's `TrainProcessSupervisor` — no other class
  may call `ProcessBuilder` or `Runtime.exec`.
- Nothing hard-coded in the UI either: ports, hosts, timeouts and train/section IDs come from `NetworkConfig`
  and `RailwayData`.
- Do not start UI work before your networking concept passes its tests.

### 3.7 Git
- **Never add `Co-Authored-By:` lines, "Generated with …" lines or any AI attribution** to commits or PRs.
- Commit only when the user asks. Never commit to `main` directly — use the member branch
  (`m<N>/<short-topic>`, e.g. `m3/multicast-alerts`) and a pull request.
- Never commit generated keystores (`certs/*.p12`), `target/`, IDE files or secrets.
- Keep `main` compiling: `mvn verify` must pass before a PR.

## 4. Commands

```bash
mvn verify                         # compile + all tests (must pass before every PR)
mvn -q test -Dtest=ProtocolCodecTest
scripts/run.sh controlcenter       # the single demo window (starts services + trains from buttons)
```
The three original launchers still work and are used for a multi-machine LAN run:
```bash
scripts/run.sh server              # central server only (+ server window)
scripts/run.sh train TR01          # a train client; TR02/TR03 in other terminals
scripts/run.sh train TR02 -Dclient.transport=TLS
scripts/run.sh controller          # controller window (UDP / URLConnection / RMI / smartrail://)
```
Run everything from the project root (config paths are relative to it). Java 21, Maven 3.9+.

## 5. Definition of done for any change
1. Lives in the owner's files — no structure change beyond the pre-approved list in
   `docs/ui/control-center-spec.md` §6; nothing hard-coded.
2. Demonstrates the member's networking API directly and is explained in a short Javadoc.
3. Unit tests in the member's existing test class (remove `@Disabled`), using `testsupport` fakes.
   UI-only changes need no new tests, but `mvn verify` must stay green.
4. `mvn verify` passes; manual check with `scripts/run.sh` described in the PR.
5. Relevant checklist item ticked in `docs/plan/member-N-*.md` and the module doc in `docs/modules/`.
