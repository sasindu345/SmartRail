package com.smartrail.tcpclient;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.model.TrainDefinition;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.service.ClientSocketFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OWNER: Member 2 - CONCEPT: TCP client + Java I/O streams.
 *
 * <p>One train's connection to the central server (plain or TLS, decided by the injected
 * {@link ClientSocketFactory}). APIs: {@code Socket}, {@code InputStream/OutputStream},
 * {@code BufferedReader/InputStreamReader}, {@code BufferedWriter/OutputStreamWriter} (UTF-8).
 * <ul>
 *   <li>TODO(M2) connect(host, port): resolve via ServerAddressResolver, open socket via factory with
 *       {@link ConfigKeys#CLIENT_CONNECT_TIMEOUT_MS}, start a reader thread, send REGISTER</li>
 *   <li>TODO(M2) reader thread: readLine -> ProtocolCodec.decode -> listeners.onMessage; EOF/IOException -> onDisconnected</li>
 *   <li>TODO(M2) send(): thread-safe write of ProtocolCodec.encode(message) + '\n' and flush</li>
 *   <li>TODO(M2) reconnect: {@link ConfigKeys#CLIENT_RECONNECT_ATTEMPTS} x {@link ConfigKeys#CLIENT_RECONNECT_DELAY_MS}</li>
 *   <li>TODO(M2) never call from the Swing EDT (use UiThreads.runInBackground)</li>
 * </ul>
 */
public final class TrainClient {

    private final NetworkConfig config;
    private final TrainDefinition train;
    private final ClientSocketFactory socketFactory;
    private final List<TrainClientListener> listeners = new CopyOnWriteArrayList<>();

    public TrainClient(NetworkConfig config, TrainDefinition train, ClientSocketFactory socketFactory) {
        this.config = config;
        this.train = train;
        this.socketFactory = socketFactory;
    }

    public TrainDefinition train() {
        return train;
    }

    public ClientSocketFactory socketFactory() {
        return socketFactory;
    }

    public void addListener(TrainClientListener listener) {
        listeners.add(listener);
    }

    public void connect(String host, int port) {
        throw new UnsupportedOperationException("TODO(M2): connect " + train.trainId() + " to " + host + ":" + port);
    }

    public void send(Message message) {
        throw new UnsupportedOperationException("TODO(M2): write " + message);
    }

    public boolean isConnected() {
        return false; // TODO(M2)
    }

    public void disconnect() {
        // TODO(M2): close socket and stop reader thread
    }
}
