package com.smartrail.protocolhandler;

import com.smartrail.common.resource.ResourceTypes;

import java.net.URLStreamHandler;
import java.net.spi.URLStreamHandlerProvider;

/**
 * OWNER: Member 5 - CONCEPT: registering a custom URL protocol handler.
 *
 * <p>Registered in {@code src/main/resources/META-INF/services/java.net.spi.URLStreamHandlerProvider},
 * so {@code new URI("smartrail://host:5100/trains/TR01").toURL()} finds it automatically (ServiceLoader,
 * system class path).
 * <ul>
 *   <li>TODO(M5) return a SmartRailURLStreamHandler for {@link ResourceTypes#SCHEME}, null otherwise</li>
 * </ul>
 */
public final class SmartRailURLStreamHandlerProvider extends URLStreamHandlerProvider {

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (ResourceTypes.SCHEME.equals(protocol)) {
            return null; // TODO(M5): return new SmartRailURLStreamHandler(...)
        }
        return null;
    }
}
