# Module: TLS & URL/URLConnection (Member 4)

## Purpose and networking concept

Two distinct networking concepts demonstrated together:

1. **TLS (Transport Layer Security)** — trains can connect to the central server over an encrypted, authenticated channel using `SSLContext`, `SSLServerSocket`, and `SSLSocket`. The server holds a private key in a PKCS12 keystore; each client verifies the server's certificate against a truststore. Hostname verification (via `setEndpointIdentificationAlgorithm("HTTPS")`) is enforced — a certificate for the wrong host is rejected. The handshake logs the negotiated protocol (`TLSv1.3`) and cipher suite.

2. **URL / URLConnection** — the `ResourceHttpServer` exposes railway data (`/api/trains`, `/api/tracks`, `/api/schedules`, `/api/stations`) over HTTP using the JDK's built-in `com.sun.net.httpserver.HttpServer`. `ResourceClient` reads those endpoints using the standard `URL` → `HttpURLConnection` lifecycle, applying configured connect/read timeouts. `fetchContent()` uses `URLConnection.getContent()` so Member 5's `ContentHandlerFactory` can return typed objects (`List<TrainInfo>`, `List<TrackInfo>`) once installed.

```
Train process                         Server process
─────────────                         ──────────────
TlsSocketFactory.connect()            TlsTrainServer (SSLServerSocket)
  → SSLSocket                           → startHandshake()
  → setEndpointIdentificationAlgorithm  → log proto + cipher
  → startHandshake()                    → BufferedReader line loop
  → (throws if cert untrusted)          → ProtocolCodec.decode → MessageHandler

ResourceClient.fetchText("tracks")    ResourceHttpServer (HttpServer /api)
  → URI.create → toURL()                → ResourceResolver.resolve(path)
  → HttpURLConnection                   → ResourceCodec.encode(records)
  → setConnectTimeout / setReadTimeout  → Content-Type: application/x-smartrail-track
  → readAllBytes → UTF-8 String         → 404 / 405 for bad paths / non-GET
```

## How to start / see it

**Generate certificates once** (project root, Git Bash):
```bash
export SMARTRAIL_KEYSTORE_PASSWORD=devpass
export SMARTRAIL_TRUSTSTORE_PASSWORD=devpass
bash scripts/generate-dev-certs.sh localhost 127.0.0.1 <your-LAN-IP>
# produces: certs/server-keystore.p12  certs/client-truststore.p12  certs/untrusted-keystore.p12
```

**Run the demo:**
```bash
scripts/run.sh controlcenter   # starts all services including TLS server on :5443, HTTP on :8080
# or headless:
scripts/run.sh server
scripts/run.sh train TR02 -Dclient.transport=TLS    # train connects over TLS
```

**Demonstrate untrusted cert rejection:**
- Set `tls.keystore.path=certs/untrusted-keystore.p12` on the server (or point a client truststore at untrusted-keystore)
- The client throws `SSLHandshakeException`; the server logs `Handshake FAILED`.

**Fetch a resource with URLConnection:**
```bash
# with the server running on :8080
curl http://localhost:8080/api/schedules
curl http://localhost:8080/api/trains
curl http://localhost:8080/api/bogus   # → 404
```

## Public classes and methods

| Class / method | Input | Output | Notes |
|---|---|---|---|
| `SslContextFactory.serverContext()` | config (keystore path/type, password env var) | `SSLContext` with `KeyManager` | throws `IOException` if file missing; `IllegalStateException` if env var unset |
| `SslContextFactory.clientContext()` | config (truststore path, password env var) | `SSLContext` with `TrustManager` | never trust-all; rejects unknown CAs |
| `TlsSocketFactory.connect(addr, timeout)` | `InetSocketAddress`, timeout ms | `SSLSocket` (connected + handshaked) | enforces hostname via HTTPS endpoint ID; re-throws `SSLHandshakeException` |
| `TlsTrainServer.start()` | `ServerContext` (config, messageHandler, eventLog) | listens on `tls.port`; bound port via `boundPort()` | one virtual accept thread + one virtual thread per client |
| `TlsTrainConnection.send(message)` | `Message` | writes `ProtocolCodec.toWireBytes` over TLS | `synchronized`; closes socket on `IOException` |
| `ResourceHttpServer.start()` | `ServerContext` (config, railwayService) | `HttpServer` on `http.resource.port` | delegates routing entirely to `ResourceResolver` |
| `ResourceClient.fetchText(path)` | resource path e.g. `"tracks/SEC_A"` | UTF-8 response body | applies connect + read timeouts from config |
| `ResourceClient.fetchContent(path)` | resource path | typed object (needs M5 ContentHandlerFactory) | falls back to `InputStream` without the factory |

## Configuration keys used

| Key | Default | Description |
|---|---|---|
| `tls.port` | `5443` | Port for `SSLServerSocket` |
| `tls.protocols` | `TLSv1.3,TLSv1.2` | Enabled protocols (`setEnabledProtocols`) |
| `tls.keystore.type` | `PKCS12` | KeyStore format |
| `tls.keystore.path` | `certs/server-keystore.p12` | Server private key + cert |
| `tls.keystore.password.env` | `SMARTRAIL_KEYSTORE_PASSWORD` | Name of env var holding the password |
| `tls.truststore.path` | `certs/client-truststore.p12` | Client's trusted server cert |
| `tls.truststore.password.env` | `SMARTRAIL_TRUSTSTORE_PASSWORD` | Name of env var holding the password |
| `http.resource.port` | `8080` | Port for `HttpServer` |
| `http.resource.base.path` | `/api` | URL prefix for all resource endpoints |
| `http.client.connect.timeout.ms` | `3000` | `HttpURLConnection.setConnectTimeout` |
| `http.client.read.timeout.ms` | `3000` | `HttpURLConnection.setReadTimeout` |

## Errors and how they are handled

| Condition | Behaviour |
|---|---|
| Keystore file missing | `IOException` from `SslContextFactory` with the path in the message |
| Password env var not set | `IllegalStateException`: `Secret not set: export SMARTRAIL_KEYSTORE_PASSWORD=...` |
| Client presents untrusted cert | `SSLHandshakeException`; server logs `Handshake FAILED <addr>: ...`; not fatal |
| Hostname in cert doesn't match | `SSLHandshakeException` on the client; server sees an abrupt close |
| HTTP 404 (unknown resource) | `ResourceResolver` returns status 404 + `text/plain` error body |
| HTTP 405 (non-GET method) | Server responds 405 with no body |
| URLConnection timeout | `SocketTimeoutException` propagates to the caller |

## Tests

```bash
mvn -q test -Dtest=TlsChannelTest,ResourceHttpServerTest
```

`TlsChannelTest` — four tests, all on port `0` with in-process keytool-generated keystores:
- `trustedCertificateHandshakeSucceeds` — full TLS handshake completes
- `untrustedCertificateIsRejected` — server cert not in client truststore → `SSLHandshakeException`
- `hostnameMismatchIsRejected` — cert SAN covers only `wronghost`, client connects to `localhost` → rejected
- `registerWorksOverTls` — `REGISTER|TR01|ST01` sent over TLS without error

`ResourceHttpServerTest` — four tests, real `HttpServer` on port `0` with `FakeRailwayService`:
- `fetchesSchedulesAsText` — `GET /api/schedules` returns non-empty body
- `unknownResourceGives404` — `GET /api/bogus` returns 404
- `contentTypeHeaderIsSmartRailMime` — `GET /api/trains` Content-Type contains `smartrail-train`
- `nonGetMethodGives405` — `POST /api/trains` returns 405

## Evidence

From `mvn test` output:
```
05:31:36.186 [TLS] Selector listening on TLS 127.0.0.1:62932
05:31:36.645 [TLS] Handshake OK /127.0.0.1:62933 proto=TLSv1.3 cipher=TLS_AES_256_GCM_SHA384
05:31:36.856 [TLS] Disconnected /127.0.0.1:62933 (client closed)
05:31:42.950 [HTTP] Resource server started on HTTP *:62942/api
05:31:42.976 [HTTP] GET schedules from /127.0.0.1:62943
05:31:43.028 [HTTP] POST trains from /127.0.0.1:62949
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 (TlsChannelTest)
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0 (ResourceHttpServerTest)
```

Cert generation output (LAN IP `192.168.8.160` included in SAN):
```
[M4] Generating server keystore (SAN=dns:localhost,ip:127.0.0.1,ip:192.168.8.160)...
[M4] Done. Files in certs/: server-keystore.p12, client-truststore.p12, untrusted-keystore.p12
```

## Limitations

- `fetchContent()` returns a raw `InputStream` until Member 5's `ContentHandlerFactory` is registered via the `java.content.handler.pkgs` system property at process startup.
- TLS uses one virtual thread per connected train client (blocking I/O, not `SSLEngine`). Member 1's NIO server handles the plain channel.
- The `TlsResourcePanel` UI screen is implemented in `ui/panels/TlsResourcePanel.java` (UI-4). It shows the live TLS session (protocol, cipher, cert), the untrusted-cert rejection demo, and URL/URLConnection resource fetches with parsed rows.
- Generated keystores are dev-only self-signed certs (validity 365 days). Do not use in production.
