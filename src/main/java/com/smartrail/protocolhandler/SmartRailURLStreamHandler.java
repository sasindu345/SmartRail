package com.smartrail.protocolhandler;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * OWNER: Member 5 - CONCEPT: custom protocol handler ({@code URLStreamHandler}) - decides HOW a
 * {@code smartrail://} resource is ACCESSED.
 * <ul>
 *   <li>TODO(M5) openConnection(url) -> new SmartRailURLConnection(url, config)</li>
 *   <li>TODO(M5) getDefaultPort() -> smartrail.resource.port (from NetworkConfig, not a literal)</li>
 * </ul>
 */
public final class SmartRailURLStreamHandler extends URLStreamHandler {

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        throw new UnsupportedOperationException("TODO(M5): open smartrail connection for " + url);
    }
}
