package com.smartrail.tls;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * OWNER: Member 4 - CONCEPT: TLS configuration (keystore, truststore, SSLContext).
 *
 * <p>APIs: {@code KeyStore}, {@code KeyManagerFactory}, {@code TrustManagerFactory}, {@code SSLContext}.
 * Passwords are read from environment variables via {@code config.secret()} — never hard-coded.
 */
public final class SslContextFactory {

    private final NetworkConfig config;

    public SslContextFactory(NetworkConfig config) {
        this.config = config;
    }

    /** SSLContext loaded from the server keystore (private key + cert). */
    public SSLContext serverContext() throws GeneralSecurityException, IOException {
        KeyStore ks = KeyStore.getInstance(config.get(ConfigKeys.TLS_KEYSTORE_TYPE));
        char[] pass = config.secret(ConfigKeys.TLS_KEYSTORE_PASSWORD_ENV);
        try (InputStream in = Files.newInputStream(config.getPath(ConfigKeys.TLS_KEYSTORE_PATH))) {
            ks.load(in, pass);
        }
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, pass);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    /** SSLContext loaded from the client truststore (server cert only, no private key). */
    public SSLContext clientContext() throws GeneralSecurityException, IOException {
        // shortcut: truststore uses same PKCS12 type as keystore; no separate type config key exists
        KeyStore ts = KeyStore.getInstance(config.get(ConfigKeys.TLS_KEYSTORE_TYPE));
        char[] pass = config.secret(ConfigKeys.TLS_TRUSTSTORE_PASSWORD_ENV);
        try (InputStream in = Files.newInputStream(config.getPath(ConfigKeys.TLS_TRUSTSTORE_PATH))) {
            ts.load(in, pass);
        }
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ts);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx;
    }
}
