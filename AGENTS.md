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

## 2. Networking concept → member → package map (the core of the project)

| Member | Networking concept(s) | Packages / files they own |
|---|---|---|
| 1 | **Java NIO** (ServerSocketChannel, Selector, ByteBuffer), TCP server, message framing, server-side coordination | `nio/`, `coordination/`, `ui/panels/NioServerPanel.java` |
| 2 | **TCP client sockets + I/O streams**, **IP addressing / InetAddress / ports**, app shell | `tcpclient/`, `ui/shell/` (except `UiThreads`), `ui/panels/TcpClientPanel.java` |
| 3 | **UDP datagrams** (discovery), **IP multicast** (alerts), **heartbeat** availability | `udp/`, `multicast/`, `heartbeat/`, `ui/panels/UdpMulticastPanel.java` |
| 4 | **TLS** (SSLContext, SSLServerSocket/SSLSocket, keystores), **URL / URLConnection** + HTTP resources | `tls/`, `urlconnection/`, `scripts/generate-dev-certs.sh`, `ui/panels/TlsResourcePanel.java` |
| 5 | **Java RMI**, **custom protocol handler** (`smartrail://`), **content handlers** | `rmi/`, `protocolhandler/`, `contenthandler/`, `META-INF/services/…`, `ui/panels/RmiHandlerPanel.java` |
| Shared | Contracts used by everyone — **frozen** | `common/**`, `app/**`, `ui/shell/UiThreads.java`, `config/**`, `pom.xml` |

Full file-by-file list: `docs/ownership.md`. Each member's plan and checklist: `docs/plan/member-N-*.md`.

## 3. Hard rules

### 3.1 Scope — keep it simple
- Implement **only** what `docs/plan/` describes. Do **not** add complex features unless the user explicitly asks:
  no database, no Spring/Netty/Jetty/any framework, no JSON/XML libraries, no web frontend, no login/auth
  system, no SSLEngine-over-NIO, no map/animation UI, no cloud deployment, no real railway data.
- **JDK APIs only** at runtime (`java.net`, `java.nio`, `javax.net.ssl`, `java.rmi`, `com.sun.net.httpserver`,
  Swing). JUnit 5 is the only (test) dependency. Never add a dependency to `pom.xml` without user approval.
- Prefer the most direct use of the networking API being demonstrated — the code is read in a presentation.

### 3.2 Never change the file structure unless the user explicitly asks
- Do **not** create, move, rename or delete files, packages or directories. Implement inside the existing files.
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

### 3.6 Git
- **Never add `Co-Authored-By:` lines, "Generated with …" lines or any AI attribution** to commits or PRs.
- Commit only when the user asks. Never commit to `main` directly — use the member branch
  (`m<N>/<short-topic>`, e.g. `m3/multicast-alerts`) and a pull request.
- Never commit generated keystores (`certs/*.p12`), `target/`, IDE files or secrets.
- Keep `main` compiling: `mvn verify` must pass before a PR.

## 4. Commands

```bash
mvn verify                         # compile + all tests (must pass before every PR)
mvn -q test -Dtest=ProtocolCodecTest
scripts/run.sh server              # central server (+ server window)
scripts/run.sh train TR01          # a train client; TR02/TR03 in other terminals
scripts/run.sh train TR02 -Dclient.transport=TLS
scripts/run.sh controller          # controller dashboard (UDP / URLConnection / RMI / smartrail://)
```
Run everything from the project root (config paths are relative to it). Java 21, Maven 3.9+.

## 5. Definition of done for any change
1. Lives in the owner's existing files; no structure change; nothing hard-coded.
2. Demonstrates the member's networking API directly and is explained in a short Javadoc.
3. Unit tests in the member's existing test class (remove `@Disabled`), using `testsupport` fakes.
4. `mvn verify` passes; manual check with `scripts/run.sh` described in the PR.
5. Relevant checklist item ticked in `docs/plan/member-N-*.md` and the module doc in `docs/modules/`.
