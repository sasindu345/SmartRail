# Member 4 — TLS secure channel & URL/URLConnection resources

**Concepts you demonstrate:** TLS secure networking (certificates, keystore/truststore, `SSLContext`, handshake,
certificate + host name validation); `URL` / `URLConnection` / `HttpURLConnection` resource access against a small
HTTP service.

**Your files:** `tls/SslContextFactory`, `tls/TlsTrainServer`, `tls/TlsTrainConnection`, `tls/TlsSocketFactory`,
`urlconnection/ResourceHttpServer`, `urlconnection/ResourceClient`, `ui/panels/TlsResourcePanel`,
`scripts/generate-dev-certs.sh`, tests in `src/test/java/com/smartrail/{tls,urlconnection}/`,
`docs/modules/m4-tls-urlconnection.md`.

**You provide:** `TlsSocketFactory` (used by M2's client), the secure train channel, `/api/*` endpoints.
**You use:** `MessageHandler` (M1 router), `ResourceResolver`/`ResourceCodec` (shared), M5's content handler factory for `getContent()`.
**Agent:** `.claude/agents/m4-tls-urlconnection.md` · **Branch prefix:** `m4/`

## Phase 0 — Setup
- [ ] Clone, JDK 21, `mvn verify` green; create branch `m4/tls`
- [ ] Read `AGENTS.md`, `docs/architecture.md` (why NIO + TLS are separate), `docs/protocol.md` §1 and §4
- [ ] Confirm with M1 that `MessageHandler`/`TrainConnection` fit a thread-per-client TLS server
- [ ] Agree with M5 that both servers use `ResourceResolver` (no duplicated resource logic)

## Phase 1 — Certificates + TLS handshake
- [ ] `scripts/generate-dev-certs.sh`: keytool keystore with SAN from args, export cert, client truststore, plus an **untrusted** second keystore for the negative demo; passwords only from env vars
- [ ] `SslContextFactory.serverContext/clientContext` from config paths/types + `config.secret(...)`; clear errors for missing file/env var
- [ ] `TlsSocketFactory.connect`: `SSLSocket`, timeout, `setEndpointIdentificationAlgorithm("HTTPS")`, `startHandshake`
- [ ] Tests: `TlsChannelTest` — trusted handshake succeeds; untrusted cert rejected; host name mismatch rejected (temp keystores generated in the test or by the script)

## Phase 2 — Secure train server + HTTP resources
- [ ] `TlsTrainServer`: `SSLServerSocket` on `tls.port`, protocols from `tls.protocols`, accept thread, per-client thread
- [ ] Per client: handshake, log protocol + cipher, line reader with `protocol.max.message.bytes`, `ProtocolCodec.decode` → `messageHandler`
- [ ] `TlsTrainConnection`: synchronized send + flush; close; remote address
- [ ] `ResourceHttpServer`: `HttpServer` on `http.resource.port`, base path from config, GET → `ResourceResolver`, Content-Type + status, 405 for non-GET, request log
- [ ] `ResourceClient.fetchText`: URL from config, `openConnection`, timeouts, response code, headers, UTF-8 body
- [ ] `ResourceClient.fetchContent`: `getContent()` (works once M5's factory is installed)
- [ ] Tests: `ResourceHttpServerTest` with `FakeRailwayService` on port `0` (schedules text, 404, MIME header, timeouts); TLS server + `FakeTrainConnection`-style handler

## Phase 3 — Integration
- [ ] C3 with M2/M1: `scripts/run.sh train TR02 -Dclient.transport=TLS` registers and runs
- [ ] Untrusted certificate (wrong truststore) → connection rejected with a clear message
- [ ] C8 with M5: `fetchContent("tracks")` returns `List<TrackInfo>`
- [ ] Two machines: certificate SAN includes the server LAN IP

## Phase 4 — UI & evidence
- [ ] `TlsResourcePanel`: resource picker + Fetch (status, Content-Type, body, typed view); TLS check button showing protocol, cipher suite, peer certificate subject/validity
- [ ] Screenshots: successful handshake details, rejected untrusted cert, schedule fetched via URLConnection
- [ ] Optional only if time permits: HTTPS for the resource service using the same `SSLContext` (`HttpsServer`)

## Phase 5 — Test, docs, demo
- [ ] Fill `docs/modules/m4-tls-urlconnection.md` (cert generation steps!)
- [ ] Your rows in `docs/test-plan.md`
- [ ] 2-minute demo slot (6:00–8:00): TLS connection + rejection, URLConnection fetch
- [ ] Report section: TLS handshake, certificates/trust, where TLS sits in TCP/IP; URL anatomy, URLConnection lifecycle

## Done when
A train runs entirely over TLS with a validated certificate; an untrusted certificate is refused; the dashboard
reads schedules/tracks through URLConnection.

## Out of scope
SSLEngine, mutual TLS/client certificates, trust-all managers, a web framework, browser frontend.
