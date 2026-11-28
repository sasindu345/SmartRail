package com.smartrail.urlconnection;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * OWNER: Member 4 - CONCEPT: URL + URLConnection (HttpURLConnection) client.
 *
 * <p>
 * Builds the URL from config (host + port + base path + resource path), opens
 * an
 * {@code HttpURLConnection} with configured timeouts, and reads the response
 * body.
 * {@link #fetchContent(String)} uses {@code getContent()} so Member 5's
 * ContentHandlerFactory
 * turns the payload into typed model objects when it is installed.
 */
public final class ResourceClient {

    private final NetworkConfig config;

    public ResourceClient(NetworkConfig config) {
        this.config = config;
    }

    /**
     * Raw UTF-8 body of e.g. {@code "tracks/SEC_A"} from the HTTP resource server.
     */
    public String fetchText(String resourcePath) throws IOException {
        HttpURLConnection conn = open(resourcePath);
        int status = conn.getResponseCode();
        // getInputStream() throws on 4xx/5xx; use getErrorStream() for those
        try (var in = status < 400 ? conn.getInputStream() : conn.getErrorStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Typed object via {@code URLConnection.getContent()}.
     * Returns a typed list (e.g. {@code List<TrackInfo>}) once Member 5's
     * ContentHandlerFactory
     * is installed via the {@code java.content.handler.pkgs} system property;
     * otherwise an InputStream.
     */
    public Object fetchContent(String resourcePath) throws IOException {
        return buildUrl(resourcePath).openConnection().getContent();
    }

    private HttpURLConnection open(String resourcePath) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) buildUrl(resourcePath).openConnection();
        conn.setConnectTimeout(config.getInt(ConfigKeys.HTTP_CLIENT_CONNECT_TIMEOUT_MS));
        conn.setReadTimeout(config.getInt(ConfigKeys.HTTP_CLIENT_READ_TIMEOUT_MS));
        conn.setRequestMethod("GET");
        return conn;
    }

    private URL buildUrl(String resourcePath) throws IOException {
        String host = config.get(ConfigKeys.CLIENT_SERVER_HOST);
        int port = config.getInt(ConfigKeys.HTTP_RESOURCE_PORT);
        String base = config.get(ConfigKeys.HTTP_RESOURCE_BASE_PATH);
        // URI.create validates the string; toURL() converts to URL
        return URI.create("http://" + host + ":" + port + base + "/" + resourcePath).toURL();
    }
}
