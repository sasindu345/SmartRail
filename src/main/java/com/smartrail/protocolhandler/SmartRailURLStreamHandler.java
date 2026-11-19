package com.smartrail.protocolhandler;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;

import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * OWNER: Member 5 - CONCEPT: custom protocol handler ({@code URLStreamHandler}) - decides HOW a
 * {@code smartrail://} resource is ACCESSED.
 * <ul>
 *   <li>openConnection(url) -> new SmartRailURLConnection(url, config)</li>
 *   <li>getDefaultPort() -> smartrail.resource.port (from NetworkConfig, not a literal)</li>
 * </ul>
 */
public final class SmartRailURLStreamHandler extends URLStreamHandler {

    private final NetworkConfig config;

    public SmartRailURLStreamHandler() {
        this(null);
    }

    public SmartRailURLStreamHandler(NetworkConfig config) {
        this.config = config;
    }

    private NetworkConfig getConfig() {
        return config != null ? config : NetworkConfig.load();
    }

    @Override
    protected int getDefaultPort() {
        return getConfig().getInt(ConfigKeys.SMARTRAIL_RESOURCE_PORT);
    }

    @Override
    protected URLConnection openConnection(URL url) throws IOException {
        return new SmartRailURLConnection(url, getConfig());
    }
}
