package com.smartrail.protocolhandler;

import com.smartrail.common.config.ConfigKeys;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * OWNER: Member 5 - CONCEPT: custom URLConnection speaking the SmartRail resource protocol
 * (docs/protocol.md, section "smartrail:// resource protocol") over a plain TCP Socket.
 * <ul>
 *   <li>TODO(M5) connect(): Socket to url host:port (timeout {@link ConfigKeys#SMARTRAIL_RESOURCE_TIMEOUT_MS});
 *       send {@code GET <path>\n}; read the status line {@code <code> <mimeType>}</li>
 *   <li>TODO(M5) getContentType() -> mime from the status line (drives the ContentHandler choice)</li>
 *   <li>TODO(M5) getInputStream() -> remaining body; non-200 -> IOException / FileNotFoundException</li>
 * </ul>
 * It must really resolve the resource from the SmartRail server - do NOT just wrap an http:// URL.
 */
public final class SmartRailURLConnection extends URLConnection {

    public SmartRailURLConnection(URL url) {
        super(url);
    }

    @Override
    public void connect() throws IOException {
        throw new UnsupportedOperationException("TODO(M5): connect to " + url);
    }

    @Override
    public String getContentType() {
        throw new UnsupportedOperationException("TODO(M5): content type from status line");
    }

    @Override
    public InputStream getInputStream() throws IOException {
        throw new UnsupportedOperationException("TODO(M5): body stream");
    }
}
