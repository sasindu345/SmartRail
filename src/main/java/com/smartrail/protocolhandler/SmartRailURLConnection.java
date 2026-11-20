package com.smartrail.protocolhandler;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.resource.ResourceTypes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;

/**
 * OWNER: Member 5 - CONCEPT: custom URLConnection speaking the SmartRail resource protocol
 * (docs/protocol.md, section "smartrail:// resource protocol") over a plain TCP Socket.
 * <ul>
 *   <li>connect(): Socket to url host:port (timeout {@link ConfigKeys#SMARTRAIL_RESOURCE_TIMEOUT_MS});
 *       send {@code GET <path>\n}; read the status line {@code <code> <mimeType>}</li>
 *   <li>getContentType() -> mime from the status line (drives the ContentHandler choice)</li>
 *   <li>getInputStream() -> remaining body; non-200 -> IOException / FileNotFoundException</li>
 * </ul>
 * It resolves the resource from the SmartRail server over a plain TCP connection.
 */
public final class SmartRailURLConnection extends URLConnection {

    private final NetworkConfig config;
    private int responseCode = -1;
    private String contentType;
    private byte[] responseBody;

    public SmartRailURLConnection(URL url) {
        this(url, null);
    }

    public SmartRailURLConnection(URL url, NetworkConfig config) {
        super(url);
        this.config = config;
    }

    private NetworkConfig getConfig() {
        return config != null ? config : NetworkConfig.load();
    }

    @Override
    public synchronized void connect() throws IOException {
        if (connected) {
            return;
        }

        int port = url.getPort();
        if (port <= 0) {
            port = url.getDefaultPort();
        }
        if (port <= 0) {
            port = getConfig().getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT);
        }

        String host = url.getHost();
        if (host == null || host.isBlank()) {
            host = "localhost";
        }

        int timeout = getConnectTimeout();
        if (timeout <= 0) {
            timeout = getConfig().getInt(ConfigKeys.SMARTRAIL_RESOURCE_TIMEOUT_MS);
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeout);
            int readTimeout = getReadTimeout();
            socket.setSoTimeout(readTimeout > 0 ? readTimeout : timeout);

            String path = url.getPath();
            if (path == null) {
                path = "";
            }
            while (path.startsWith("/")) {
                path = path.substring(1);
            }

            OutputStream out = socket.getOutputStream();
            String request = "GET " + path + "\n";
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();

            InputStream in = socket.getInputStream();
            ByteArrayOutputStream lineBuf = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    break;
                }
                lineBuf.write(b);
            }

            String statusLine = lineBuf.toString(StandardCharsets.UTF_8).trim();
            if (statusLine.isEmpty()) {
                throw new IOException("Empty response from SmartRail resource server: " + url);
            }

            String[] parts = statusLine.split("\\s+", 2);
            try {
                this.responseCode = Integer.parseInt(parts[0]);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid status code in response line: " + statusLine, e);
            }
            this.contentType = parts.length > 1 ? parts[1].trim() : ResourceTypes.MIME_ERROR;

            this.responseBody = in.readAllBytes();
            this.connected = true;
        }
    }

    @Override
    public String getContentType() {
        if (!connected) {
            try {
                connect();
            } catch (IOException e) {
                return null;
            }
        }
        return contentType;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (!connected) {
            connect();
        }
        if (responseCode == 404) {
            String msg = responseBody != null ? new String(responseBody, StandardCharsets.UTF_8).trim() : "";
            throw new FileNotFoundException("Resource not found (404) for " + url + ": " + msg);
        }
        if (responseCode != 200) {
            String msg = responseBody != null ? new String(responseBody, StandardCharsets.UTF_8).trim() : "";
            throw new IOException("Server error (" + responseCode + ") for " + url + ": " + msg);
        }
        return new ByteArrayInputStream(responseBody != null ? responseBody : new byte[0]);
    }

    @Override
    public String getHeaderField(String name) {
        if ("content-type".equalsIgnoreCase(name)) {
            return getContentType();
        }
        return super.getHeaderField(name);
    }

    public int getResponseCode() throws IOException {
        if (!connected) {
            connect();
        }
        return responseCode;
    }
}
