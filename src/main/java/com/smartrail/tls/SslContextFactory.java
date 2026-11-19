package com.smartrail.tls;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * OWNER: Member 4 - CONCEPT: TLS configuration (keystore, truststore, SSLContext).
 *
 * <p>APIs: {@code KeyStore}, {@code KeyManagerFactory}, {@code TrustManagerFactory}, {@code SSLContext}.
 * <ul>
 *   <li>TODO(M4) serverContext(): load {@link ConfigKeys#TLS_KEYSTORE_PATH} (type {@link ConfigKeys#TLS_KEYSTORE_TYPE},
 *       password via config.secret({@link ConfigKeys#TLS_KEYSTORE_PASSWORD_ENV}))</li>
 *   <li>TODO(M4) clientContext(): load {@link ConfigKeys#TLS_TRUSTSTORE_PATH}; NEVER a trust-all TrustManager</li>
 *   <li>TODO(M4) clear error when the keystore file or password env var is missing</li>
 * </ul>
 */
public final class SslContextFactory {

    private final NetworkConfig config;

    public SslContextFactory(NetworkConfig config) {
        this.config = config;
    }

    public SSLContext serverContext() throws GeneralSecurityException, IOException {
        throw new UnsupportedOperationException("TODO(M4): server SSLContext from " + config.get(ConfigKeys.TLS_KEYSTORE_PATH));
    }

    public SSLContext clientContext() throws GeneralSecurityException, IOException {
        throw new UnsupportedOperationException("TODO(M4): client SSLContext from " + config.get(ConfigKeys.TLS_TRUSTSTORE_PATH));
    }
}
