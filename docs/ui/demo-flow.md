# Demonstration flow — one window, buttons only

How the five members demonstrate their networking concepts from the single Control Center window.
No terminal is used during the demo. Screens and owners: `control-center-spec.md`.

## 1. Before the session

```bash
mvn verify                    # must be green
scripts/run.sh controlcenter  # the only command typed all session
```
Then: certificates generated (`scripts/generate-dev-certs.sh`, M4), firewall ports open, and — if
the venue blocks multicast — `multicast.unicast.fallback.enabled=true` in `config/smartrail.properties`.

Everything after this point is a button click.

## 2. The ten-minute run

| # | Click | What the audience sees | Concept | Member |
|---|---|---|---|---|
| 1 | `Start Server` | Status bar dots turn green one by one: TCP/NIO, UDP, TLS, Multicast, RMI, HTTP. Services & ports table fills. | server binds real ports; NIO `Selector` serves every client on one thread | M1 |
| 2 | `Discover server` (Network) | "Found 192.168.x.x (plain:5000 tls:5443)" — nobody typed an IP | UDP broadcast request/reply | M3 |
| 3 | `+TR01`, `+TR02` | Two real child JVMs start; trains appear on the map and in the table; their consoles stream `REGISTER → REGISTER_OK → ASSIGN_ROUTE` | TCP client, `InetAddress`, I/O streams | M2 |
| 4 | `+TR03 (TLS)` | TR03 connects on the secure port; Selected Train shows `Connected (TLS)`; Security screen shows protocol, cipher and certificate subject | TLS handshake and certificate validation | M4 |
| 5 | `Run conflict demo` (Track Sections) | TR01 and TR03 both want `SEC_A`; one is granted, the other gets `SECTION_DENIED`; the map shows who holds it | atomic check-and-reserve over concurrent TCP clients | M1 |
| 6 | `Trigger obstruction` (Alerts) | One `EMERGENCY_ALERT` appears **simultaneously in all three train consoles** and in the alert feed | IP multicast one-to-many (or the documented unicast fallback) | M3 |
| 7 | `Stop TR03` | TR03's heartbeats stop; after `heartbeat.timeout.ms` it flips to `INACTIVE` on the map and table | heartbeat availability monitoring | M3 |
| 8 | `Fetch schedules` (Security & Resources) | Timetable rows retrieved over HTTP with status and `Content-Type` shown | `URL` / `URLConnection` | M4 |
| 9 | `Connect RMI`, then `Hold TR02` | The remote call returns the live train list; `Hold` makes **TR02's own console** print `HOLD` and its status flip to `HELD` | Java RMI remote administration | M5 |
| 10 | `smartrail://trains/TR01` → `Fetch & Parse` | The custom URL resolves and the content handler turns the payload into a typed `TrainInfo` whose fields are listed | custom protocol handler + content handler | M5 |

Step 6 is the strongest moment in the demo: one send, three independent processes receive it at
once, visible side by side.

## 3. Per-member two-minute slots

Each member: name the concept in one sentence → show the code file (`docs/ownership.md`) → click
their buttons → say what the application gains. Do not talk about styling.

| Time | Member | Clicks | Says |
|---|---|---|---|
| 0:00–2:00 | **M1** | 1, 5 | NIO `Selector` vs thread-per-client; framing a TCP byte stream; why check-and-reserve must be atomic |
| 2:00–4:00 | **M2** | 3 (+ a deliberate wrong port) | client-server model; `InetAddress` and ports; I/O streams; how a failed connect is reported |
| 4:00–6:00 | **M3** | 2, 6, 7 | UDP vs TCP; broadcast vs multicast; why a heartbeat is needed when TCP alone stays silent |
| 6:00–8:00 | **M4** | 4, 8 (+ `Test untrusted certificate`) | the TLS handshake and trust chain; where TLS sits in TCP/IP; the `URLConnection` lifecycle |
| 8:00–10:00 | **M5** | 9, 10 | RMI stubs, registry and serialization; protocol handler (*how* a resource is fetched) vs content handler (*how* it is interpreted) |

## 4. Fallbacks

| Problem | Response |
|---|---|
| Venue blocks multicast | `multicast.unicast.fallback.enabled=true` — step 6 still works, and say why the fallback exists |
| Broadcast discovery fails (common on mobile hotspots) | set `udp.discovery.broadcast.address` to the LAN's own broadcast address, or use manual IP entry — that fallback is part of the demo |
| TLS certificate missing | regenerate with `scripts/generate-dev-certs.sh`; steps 1–3 and 5–10 are unaffected |
| A service shows red | say what it means, keep going — the status bar reporting a genuine failure is itself evidence the UI reflects real network state |
| Two machines available | run `scripts/run.sh server` on one and launch trains pointed at its IP from the other, to show it is a real LAN, not just localhost |
