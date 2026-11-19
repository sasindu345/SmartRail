package com.smartrail.urlconnection;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;

import java.io.IOException;

/**
 * OWNER: Member 4 - CONCEPT: URL + URLConnection (HttpURLConnection) client.
 * <ul>
 *   <li>TODO(M4) build the URL from {@link ConfigKeys#CLIENT_SERVER_HOST}, {@link ConfigKeys#HTTP_RESOURCE_PORT},
 *       {@link ConfigKeys#HTTP_RESOURCE_BASE_PATH} + path (use URI -> toURL)</li>
 *   <li>TODO(M4) openConnection; set connect/read timeouts ({@link ConfigKeys#HTTP_CLIENT_CONNECT_TIMEOUT_MS},
 *       {@link ConfigKeys#HTTP_CLIENT_READ_TIMEOUT_MS}); check response code; read headers (Content-Type)</li>
 *   <li>TODO(M4) fetchText(): body as UTF-8 text; fetchContent(): connection.getContent() so Member 5's
 *       ContentHandlerFactory turns the payload into TrainInfo / TrackInfo / ScheduleInfo lists</li>
 * </ul>
 */
public final class ResourceClient {

    private final NetworkConfig config;

    public ResourceClient(NetworkConfig config) {
        this.config = config;
    }

    /** Raw body of e.g. {@code "tracks/SEC_A"}. */
    public String fetchText(String resourcePath) throws IOException {
        throw new UnsupportedOperationException("TODO(M4): URLConnection GET " + resourcePath + " from " + config.get(ConfigKeys.CLIENT_SERVER_HOST));
    }

    /** Typed object via URLConnection.getContent() (needs Member 5's content handler factory). */
    public Object fetchContent(String resourcePath) throws IOException {
        throw new UnsupportedOperationException("TODO(M4): URLConnection.getContent " + resourcePath);
    }
}
