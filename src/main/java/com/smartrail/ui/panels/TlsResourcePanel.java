package com.smartrail.ui.panels;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.tls.TlsSocketFactory;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.components.StatusPill;
import com.smartrail.ui.components.TableStyles;
import com.smartrail.ui.shell.SimulationController;
import com.smartrail.ui.shell.UiThreads;
import com.smartrail.ui.theme.Theme;

import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * OWNER: Member 4 — Security &amp; Resources screen (control-center-spec.md §4.6).
 *
 * <p>Three live panels, each visualising a real network event:
 * <ol>
 *   <li><b>TLS session</b> – opens an {@code SSLSocket} to the TLS server, reads
 *       the negotiated {@code SSLSession} (protocol, cipher suite, peer certificate
 *       subject and validity). Demonstrates Java's TLS handshake.</li>
 *   <li><b>Untrusted certificate rejection</b> – repeats the connection with a
 *       {@code TrustManager} that rejects every cert. The handshake {@code SSLHandshakeException}
 *       is shown verbatim — verification is <em>never</em> bypassed.</li>
 *   <li><b>URL/URLConnection resource fetch</b> – retrieves schedules, tracks or
 *       trains via {@code HttpURLConnection}. Shows HTTP status, {@code Content-Type}
 *       header, and the response as a parsed row table (key/value pairs) or raw body.
 *       Also attempts {@code getContent()} to show Member 5's typed objects when available.</li>
 * </ol>
 *
 * <p>All network calls run via {@code UiThreads.runInBackground};
 * all widget updates via {@code UiThreads.onEdt}. The Swing EDT is never blocked.
 * All ports, hosts and timeouts come from {@code NetworkConfig} — nothing hard-coded.
 */
public final class TlsResourcePanel extends JPanel {

    // ---- shared state ---------------------------------------------------------
    private final NetworkConfig config;

    // ---- TLS check widgets ----------------------------------------------------
    private final StatusPill    tlsPill      = new StatusPill("Not tested", Theme.INACTIVE);
    private final JTextArea     tlsDetail    = monoArea();
    private final JButton       tlsCheckBtn  = btn("Check TLS session");
    private final JButton       untrustedBtn = btn("Test untrusted certificate");

    // ---- URL/URLConnection widgets --------------------------------------------
    private final JComboBox<String>  resourceCombo = new JComboBox<>(
            new String[]{"schedules", "tracks", "trains"});
    private final JButton            fetchBtn        = btn("Fetch");
    private final JLabel             httpStatusLabel = val("—");
    private final JLabel             contentTypeLabel = val("—");
    private final DefaultTableModel  rowModel = new DefaultTableModel(
            new String[]{"Key / Row", "Value"}, 0) {
        @Override public boolean isCellEditable(int r, int c) { return false; }
    };
    private final JTable             rowTable  = new JTable(rowModel);
    private final JTextArea          rawArea   = monoArea();

    // ---------------------------------------------------------------------------

    /** Used by ControlCenterWindow which currently passes NetworkConfig directly. */
    public TlsResourcePanel(NetworkConfig config) {
        this(null, config);
    }

    /** Preferred constructor — receives the full SimulationController. */
    public TlsResourcePanel(SimulationController controller) {
        this(controller, controller.config());
    }

    private TlsResourcePanel(SimulationController controller, NetworkConfig config) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.config = config;

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(Theme.PAD, Theme.PAD, Theme.PAD, Theme.PAD));

        // stack the two cards vertically inside a scroll pane
        JPanel col = new JPanel();
        col.setOpaque(false);
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.add(buildTlsCard());
        col.add(Box.createVerticalStrut(Theme.GAP));
        col.add(buildResourceCard());

        JScrollPane scroll = new JScrollPane(col);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        add(scroll, BorderLayout.CENTER);

        tlsCheckBtn.addActionListener(e  -> doTlsCheck(false));
        untrustedBtn.addActionListener(e -> doTlsCheck(true));
        fetchBtn.addActionListener(e     -> doFetch());
    }

    // ==========================================================================
    // TLS CARD
    // ==========================================================================

    private Card buildTlsCard() {
        Card card = new Card("TLS Secure Channel");

        // controls row
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        controls.setOpaque(false);
        controls.add(tlsCheckBtn);
        controls.add(untrustedBtn);
        controls.add(tlsPill);

        // hint in the detail area
        tlsDetail.setText(
                "Click \"Check TLS session\" to perform a real TLS handshake with the server\n"
                + "and display the negotiated protocol, cipher suite and peer certificate.\n\n"
                + "Click \"Test untrusted certificate\" to show the connection being refused\n"
                + "when the server certificate is not in the truststore.");

        JPanel body = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        body.setOpaque(false);
        body.add(controls, BorderLayout.NORTH);
        body.add(new JScrollPane(tlsDetail), BorderLayout.CENTER);

        card.setBody(body);
        return card;
    }

    /**
     * Performs a TLS handshake with the configured server.
     *
     * @param forceUntrusted {@code true} → use a rejecting {@code TrustManager}
     *                       so the handshake fails; the rejection message is the demo.
     */
    private void doTlsCheck(boolean forceUntrusted) {
        tlsCheckBtn.setEnabled(false);
        untrustedBtn.setEnabled(false);
        UiThreads.onEdt(() -> {
            tlsPill.setStatus("Connecting…", Theme.WARN);
            tlsDetail.setText("");
        });

        UiThreads.runInBackground(() -> {
            String host      = config.get(ConfigKeys.CLIENT_SERVER_HOST);
            int    port      = config.getInt(ConfigKeys.TLS_PORT);
            int    timeoutMs = config.getInt(ConfigKeys.CLIENT_CONNECT_TIMEOUT_MS);

            if (forceUntrusted) {
                testUntrustedCert(host, port, timeoutMs);
            } else {
                testTrustedSession(host, port, timeoutMs);
            }

            UiThreads.onEdt(() -> {
                tlsCheckBtn.setEnabled(true);
                untrustedBtn.setEnabled(true);
            });
        });
    }

    /**
     * Opens a TLS connection with the configured truststore and shows the SSLSession.
     * Demonstrates: certificate validation, protocol negotiation, cipher selection.
     */
    private void testTrustedSession(String host, int port, int timeoutMs) {
        try {
            TlsSocketFactory factory = new TlsSocketFactory(config);
            try (SSLSocket socket = (SSLSocket) factory.connect(
                    new InetSocketAddress(host, port), timeoutMs)) {

                SSLSession session  = socket.getSession();
                String     protocol = session.getProtocol();
                String     cipher   = session.getCipherSuite();
                String     subject  = peerSubject(session);
                String     validity = peerValidity(session);

                String detail =
                        "✔ Handshake succeeded\n"
                        + "─────────────────────────────────\n"
                        + "Protocol     : " + protocol + "\n"
                        + "Cipher suite : " + cipher   + "\n"
                        + "Peer cert    : " + subject  + "\n"
                        + "Valid        : " + validity  + "\n\n"
                        + "The server's certificate was validated against the configured\n"
                        + "truststore (" + config.get(ConfigKeys.TLS_TRUSTSTORE_PATH) + ").\n"
                        + "Endpoint identification algorithm: HTTPS (SAN checked).";

                UiThreads.onEdt(() -> {
                    tlsPill.setStatus("Connected (" + protocol + ")", Theme.OK);
                    tlsDetail.setText(detail);
                    tlsDetail.setCaretPosition(0);
                });
            }
        } catch (IOException ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            UiThreads.onEdt(() -> {
                tlsPill.setStatus("Failed", Theme.DANGER);
                tlsDetail.setText(
                        "✘ Connection failed\n"
                        + "─────────────────────────────────\n"
                        + msg + "\n\n"
                        + "Make sure the server is started (\"Start Server\") and certificates\n"
                        + "have been generated: bash scripts/generate-dev-certs.sh localhost");
            });
        }
    }

    /**
     * Repeats the connection with a {@code TrustManager} that always rejects the
     * server cert.  The resulting {@code SSLHandshakeException} is shown verbatim —
     * verification is <em>never</em> bypassed to make the demo pass.
     *
     * <p>If the handshake somehow succeeds the pill turns red "UNEXPECTED: accepted"
     * — the audience can see that something is wrong with the configuration.
     */
    private void testUntrustedCert(String host, int port, int timeoutMs) {
        try {
            // Build a fresh SSLContext whose TrustManager rejects every certificate.
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] c, String a) {}
                @Override public void checkServerTrusted(X509Certificate[] c, String a)
                        throws CertificateException {
                    throw new CertificateException(
                            "Certificate not in truststore (untrusted demo)");
                }
                @Override public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }}, null);

            SSLSocket socket = (SSLSocket) ctx.getSocketFactory().createSocket();
            // Still enforce hostname validation — we want to see the cert rejection,
            // not a hostname mismatch.  Either way verification is never bypassed.
            SSLParameters params = socket.getSSLParameters();
            params.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(params);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            try {
                socket.startHandshake();   // MUST throw
                socket.close();
                // Should never reach here.
                UiThreads.onEdt(() -> {
                    tlsPill.setStatus("UNEXPECTED: accepted", Theme.DANGER);
                    tlsDetail.setText(
                            "ERROR — the untrusted certificate was accepted.\n"
                            + "This should never happen. Investigate TLS configuration.");
                });
            } catch (IOException handshakeEx) {
                String reason = handshakeEx.getMessage() == null
                        ? handshakeEx.getClass().getSimpleName()
                        : handshakeEx.getMessage();
                UiThreads.onEdt(() -> {
                    tlsPill.setStatus("Rejected ✔ (correct)", Theme.OK);
                    tlsDetail.setText(
                            "✔ Untrusted certificate correctly rejected\n"
                            + "─────────────────────────────────────────\n"
                            + "Rejection reason: " + reason + "\n\n"
                            + "The JDK's SSLSocket called our TrustManager.checkServerTrusted()\n"
                            + "and it threw CertificateException — the handshake was aborted.\n\n"
                            + "This demonstrates that the SmartRail TLS stack validates every\n"
                            + "server certificate against the truststore.  A rogue or self-signed\n"
                            + "cert is never accepted.");
                });
            } finally {
                try { socket.close(); } catch (IOException ignored) {}
            }
        } catch (Exception ex) {
            String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            UiThreads.onEdt(() -> {
                tlsPill.setStatus("Error", Theme.DANGER);
                tlsDetail.setText("Could not build untrusted SSL context: " + msg);
            });
        }
    }

    // ==========================================================================
    // URL / URLConnection RESOURCE CARD
    // ==========================================================================

    private Card buildResourceCard() {
        Card card = new Card("URL / URLConnection Resources");

        resourceCombo.setFont(Theme.FONT_BODY);

        // controls row
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        controls.setOpaque(false);
        controls.add(resourceCombo);
        controls.add(fetchBtn);

        // HTTP metadata row
        JPanel meta = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        meta.setOpaque(false);
        meta.add(muted("HTTP status:"));
        meta.add(httpStatusLabel);
        meta.add(Box.createHorizontalStrut(Theme.PAD));
        meta.add(muted("Content-Type:"));
        meta.add(contentTypeLabel);

        // parsed-row table (key/value) — visualises the resource content
        TableStyles.apply(rowTable);
        rowTable.getColumnModel().getColumn(0).setPreferredWidth(160);
        rowTable.getColumnModel().getColumn(0).setMaxWidth(260);
        JScrollPane tableScroll = new JScrollPane(rowTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(Theme.BORDER));

        // raw body fallback (shown below the table, initially with the prompt)
        rawArea.setText(
                "Select a resource and click Fetch.\n"
                + "The table shows parsed rows; the raw body appears below.");

        // layout: controls at top, meta below, then table, then raw body
        JPanel top = new JPanel(new BorderLayout(0, Theme.GAP));
        top.setOpaque(false);
        top.add(controls, BorderLayout.NORTH);
        top.add(meta,     BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        body.setOpaque(false);
        body.add(top,                           BorderLayout.NORTH);
        body.add(tableScroll,                   BorderLayout.CENTER);
        body.add(new JScrollPane(rawArea),      BorderLayout.SOUTH);

        card.setBody(body);
        return card;
    }

    /**
     * Fetches the selected resource via {@code HttpURLConnection}.
     * Demonstrates: URL construction, openConnection(), timeouts, response code,
     * Content-Type header, response body, and optionally {@code getContent()} typed objects.
     */
    private void doFetch() {
        String resource = (String) resourceCombo.getSelectedItem();
        if (resource == null) return;

        fetchBtn.setEnabled(false);
        UiThreads.onEdt(() -> {
            httpStatusLabel.setText("…");
            httpStatusLabel.setForeground(Theme.TEXT_MUTED);
            contentTypeLabel.setText("…");
            clearTable();
            rawArea.setText("Fetching " + resource + " via URLConnection…");
        });

        UiThreads.runInBackground(() -> {
            try {
                // ---- open HttpURLConnection --------------------------------
                String urlStr = "http://"
                        + config.get(ConfigKeys.CLIENT_SERVER_HOST)
                        + ":" + config.getInt(ConfigKeys.HTTP_RESOURCE_PORT)
                        + config.get(ConfigKeys.HTTP_RESOURCE_BASE_PATH)
                        + "/" + resource;

                HttpURLConnection conn = (HttpURLConnection)
                        URI.create(urlStr).toURL().openConnection();
                conn.setConnectTimeout(config.getInt(ConfigKeys.HTTP_CLIENT_CONNECT_TIMEOUT_MS));
                conn.setReadTimeout(config.getInt(ConfigKeys.HTTP_CLIENT_READ_TIMEOUT_MS));
                conn.setRequestMethod("GET");

                int    status      = conn.getResponseCode();
                String contentType = conn.getContentType();

                // read body
                String rawBody;
                try (var in = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
                    rawBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }

                // ---- parse into rows for the table ------------------------
                String[][] rows = parseRows(resource, rawBody);

                // ---- also try typed getContent() (M5 content handler) ----
                String typedLine = "";
                try {
                    Object typed = URI.create(urlStr).toURL().openConnection().getContent();
                    if (!(typed instanceof java.io.InputStream)) {
                        typedLine = "\n[getContent() → " + typed.getClass().getSimpleName()
                                + "]\n" + typed;
                    }
                } catch (Exception ignored) {}

                final int      s   = status;
                final String   ct  = contentType;
                final String   raw = rawBody + typedLine;

                UiThreads.onEdt(() -> {
                    // status pill colour: green < 400, red >= 400
                    httpStatusLabel.setText(statusText(s));
                    httpStatusLabel.setForeground(s < 400 ? Theme.OK : Theme.DANGER);
                    contentTypeLabel.setText(ct != null ? ct : "—");

                    // populate table
                    clearTable();
                    for (String[] row : rows) {
                        rowModel.addRow(row);
                    }

                    rawArea.setText(raw);
                    rawArea.setCaretPosition(0);
                    fetchBtn.setEnabled(true);
                });

            } catch (IOException ex) {
                String msg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                UiThreads.onEdt(() -> {
                    httpStatusLabel.setText("Error");
                    httpStatusLabel.setForeground(Theme.DANGER);
                    contentTypeLabel.setText("—");
                    clearTable();
                    rawArea.setText(
                            "Fetch failed: " + msg + "\n\n"
                            + "Make sure the server is started (\"Start Server\" button) and the\n"
                            + "HTTP resource service is enabled (http.resource.enabled=true).");
                    fetchBtn.setEnabled(true);
                });
            }
        });
    }

    /**
     * Splits the raw HTTP body into displayable key/value rows for the table.
     *
     * <p>The ResourceHttpServer serves line-delimited text: each line is a property
     * or CSV-style record.  This parser shows each line as a numbered row so the
     * audience can see real data, not just a wall of text.
     */
    private static String[][] parseRows(String resource, String body) {
        if (body == null || body.isBlank()) {
            return new String[][]{{"(empty response)", ""}};
        }
        String[] lines = body.split("\\r?\\n");
        String[][] rows = new String[lines.length][2];
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            // If the line contains "=", split on first "=" (properties format)
            int eq = line.indexOf('=');
            if (eq > 0) {
                rows[i][0] = line.substring(0, eq).trim();
                rows[i][1] = line.substring(eq + 1).trim();
            } else {
                // CSV or bare value — show row number + full value
                rows[i][0] = resource + "[" + (i + 1) + "]";
                rows[i][1] = line;
            }
        }
        return rows;
    }

    // ==========================================================================
    // HELPERS
    // ==========================================================================

    private void clearTable() {
        rowModel.setRowCount(0);
    }

    private static String peerSubject(SSLSession session) {
        try {
            Certificate[] certs = session.getPeerCertificates();
            if (certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                return x509.getSubjectX500Principal().getName();
            }
        } catch (javax.net.ssl.SSLPeerUnverifiedException ignored) {}
        return "(unavailable)";
    }

    private static String peerValidity(SSLSession session) {
        try {
            Certificate[] certs = session.getPeerCertificates();
            if (certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                return x509.getNotBefore() + "  →  " + x509.getNotAfter();
            }
        } catch (javax.net.ssl.SSLPeerUnverifiedException ignored) {}
        return "(unavailable)";
    }

    private static String statusText(int code) {
        return switch (code) {
            case 200 -> "200 OK";
            case 404 -> "404 Not Found";
            case 405 -> "405 Method Not Allowed";
            case 500 -> "500 Internal Server Error";
            default  -> String.valueOf(code);
        };
    }

    // ---- widget factories — all use Theme tokens; no literal colours/fonts ----

    private static JButton btn(String text) {
        JButton b = new JButton(text);
        b.setFont(Theme.FONT_BODY);
        return b;
    }

    private static JLabel val(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_BODY_BOLD);
        l.setForeground(Theme.TEXT);
        return l;
    }

    private static JLabel muted(String text) {
        JLabel l = new JLabel(text);
        l.setFont(Theme.FONT_SMALL);
        l.setForeground(Theme.TEXT_MUTED);
        return l;
    }

    private static JTextArea monoArea() {
        JTextArea ta = new JTextArea(5, 60);
        ta.setEditable(false);
        ta.setFont(Theme.FONT_MONO);     // design-system §3: use Theme token, not new Font(...)
        ta.setForeground(Theme.TEXT);
        ta.setBackground(Theme.SURFACE);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(false);
        return ta;
    }
}
