# 10-minute demo script

Setup before the session: server machine + 1–2 client machines on the same LAN; `mvn verify` done;
dev certificates generated (server SAN includes its LAN IP); firewall ports open (docs/configuration.md);
multicast tested — enable `multicast.unicast.fallback.enabled` if blocked.

Start order: `scripts/run.sh server` → `scripts/run.sh train TR01` → `train TR02 -Dclient.transport=TLS` →
`train TR03` → `scripts/run.sh controller`.

| Time | Member | Show | Say (networking concept) |
|---|---|---|---|
| 0:00–2:00 | 1 | Server window: 3 trains on one selector thread; TR01 vs TR03 → `SECTION_DENIED` | NIO Selector vs thread-per-client; framing a byte stream; atomic reservation |
| 2:00–4:00 | 2 | Resolve host (IPv4/IPv6), connect TR01, register, position updates; wrong port error | Client-server, IP addresses/ports, TCP streams |
| 4:00–6:00 | 3 | Discover button; obstruction alert reaching 2 trains; kill TR03 → INACTIVE | UDP vs TCP, broadcast vs multicast, heartbeat |
| 6:00–8:00 | 4 | TR02 over TLS (cipher/cert shown); untrusted cert rejected; fetch `/api/schedules` | TLS handshake + trust; URL/URLConnection |
| 8:00–10:00 | 5 | RMI active trains + hold TR02 (train shows HELD); open `smartrail://…/trains/TR01` → typed fields | RMI; protocol handler vs content handler |

Each member: concept in one sentence → show the code file (see docs/ownership.md) → show it running → what the
application gains. No time on UI styling.

## Fallbacks
- Multicast blocked → unicast fallback flag.
- Discovery blocked → manual IP (part of the demo).
- One laptop only → all processes on localhost; mention LAN behaviour from rehearsal evidence.
