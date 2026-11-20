# 10-minute demo script

The demonstration runs from **one window** and nothing but button clicks. The full button-by-button
walkthrough, including per-member slots and fallbacks, lives in
[`docs/ui/demo-flow.md`](ui/demo-flow.md) — this file is the short stage plan.

## Setup before the session

```bash
mvn verify                    # must be green
scripts/run.sh controlcenter  # the only command typed all session
```
Also: TLS certificates generated (`scripts/generate-dev-certs.sh`), firewall ports open
(`docs/configuration.md`), and — if the venue blocks multicast — `multicast.unicast.fallback.enabled=true`.

The Control Center hosts the server services in-process and launches each train as a **real separate
JVM process**, streaming its console into the window. No terminals during the demo.

## Stage plan

| Time | Member | Clicks | Say (networking concept) |
|---|---|---|---|
| 0:00–2:00 | 1 | `Start Server`, `Run conflict demo` | NIO `Selector` vs thread-per-client; framing a TCP byte stream; why check-and-reserve must be atomic |
| 2:00–4:00 | 2 | `+TR01`, `+TR02`, plus a deliberate wrong port | client-server model; `InetAddress` and ports; I/O streams; how a failed connect is reported |
| 4:00–6:00 | 3 | `Discover server`, `Trigger obstruction`, `Stop TR03` | UDP vs TCP; broadcast vs multicast; why a heartbeat is needed when TCP stays silent |
| 6:00–8:00 | 4 | `+TR03 (TLS)`, `Test untrusted certificate`, `Fetch schedules` | TLS handshake and trust chain; where TLS sits in TCP/IP; the `URLConnection` lifecycle |
| 8:00–10:00 | 5 | `Connect RMI`, `Hold TR02`, `smartrail://trains/TR01` | RMI stubs, registry, serialization; protocol handler (*how* it is fetched) vs content handler (*how* it is interpreted) |

The strongest moment is Member 3's `Trigger obstruction`: one send, and the same alert appears in
three independent train consoles side by side.

Each member: concept in one sentence → show the code file (see `docs/ownership.md`) → click the
buttons → say what the application gains. Do not spend time on UI styling.

## Fallbacks

- **Multicast blocked** (common on hotspots and campus Wi-Fi) → `multicast.unicast.fallback.enabled=true`;
  the alert still reaches every train, and explaining why the fallback exists earns the point anyway.
- **Broadcast discovery fails** → set `udp.discovery.broadcast.address` to the LAN's own broadcast
  address, or use manual IP entry — that fallback is itself part of Member 3's demo.
- **A service shows red** → say what it means and continue; the status bar reporting a real failure
  proves the UI reflects real network state.
- **Only one laptop** → everything runs locally; trains are still separate OS processes with real
  sockets. With two machines, run `scripts/run.sh server` on one and point trains at its IP from the
  other to show a genuine LAN.
