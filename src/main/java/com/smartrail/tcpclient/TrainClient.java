package com.smartrail.tcpclient;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.model.TrainDefinition;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.MessageType;
import com.smartrail.common.protocol.ProtocolCodec;
import com.smartrail.common.protocol.ProtocolException;
import com.smartrail.common.service.ClientSocketFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * OWNER: Member 2 - CONCEPT: TCP client + Java I/O streams.
 *
 * <p>One train's connection to the central server (plain or TLS, decided by the injected
 * {@link ClientSocketFactory}). The socket is a byte stream: a {@link BufferedReader} turns it
 * into lines, {@link ProtocolCodec} turns each line into a {@link Message}, and {@link #send}
 * writes one UTF-8 line and flushes it. The first line after connect is {@code REGISTER}.
 * A dropped connection is retried {@code client.reconnect.attempts} times.
 */
public final class TrainClient {

    private final NetworkConfig config;
    private final TrainDefinition train;
    private final ClientSocketFactory socketFactory;
    private final ServerAddressResolver resolver = new ServerAddressResolver();
    private final List<TrainClientListener> listeners = new CopyOnWriteArrayList<>();
    private final Object connectLock = new Object();
    private final Object writeLock = new Object();

    private Socket socket;
    private BufferedWriter writer;
    private volatile boolean closed;
    private String connectedHost;
    private int connectedPort;

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

    /**
     * Resolves {@code host}, connects, and sends {@code REGISTER}. Retries while the process is
     * still running. Connection failures are reported through {@link TrainClientListener#onError}
     * as a single sentence.
     */
    public void connect(String host, int port) {
        synchronized (connectLock) {
            if (closed || isConnected()) {
                return;
            }
            connectedHost = host;
            connectedPort = port;
            int attempts = Math.max(1, config.getInt(ConfigKeys.CLIENT_RECONNECT_ATTEMPTS));
            int delayMs = config.getInt(ConfigKeys.CLIENT_RECONNECT_DELAY_MS);
            for (int attempt = 1; attempt <= attempts && !closed; attempt++) {
                try {
                    open(host, port);
                    return;
                } catch (IOException e) {
                    notifyError(failure(host, port, e));
                    if (attempt < attempts && !closed) {
                        sleep(delayMs);
                    }
                }
            }
        }
    }

    public void send(Message message) {
        synchronized (writeLock) {
            BufferedWriter out = writer;
            if (out == null) {
                throw new IllegalStateException("Not connected");
            }
            try {
                out.write(ProtocolCodec.encode(message));
                out.write('\n');
                out.flush();
            } catch (IOException e) {
                throw new IllegalStateException("Send failed: " + e.getMessage(), e);
            }
        }
        System.out.println("[TRAIN] -> " + message);
    }

    public boolean isConnected() {
        Socket current = socket;
        return current != null && current.isConnected() && !current.isClosed();
    }

    public void disconnect() {
        synchronized (connectLock) {
            closed = true;
            closeSocket();
        }
        notifyDisconnected("disconnected");
    }

    private void open(String host, int port) throws IOException {
        InetAddress address = resolver.resolve(host);
        int timeoutMs = config.getInt(ConfigKeys.CLIENT_CONNECT_TIMEOUT_MS);
        Socket opened = socketFactory.connect(new InetSocketAddress(address, port), timeoutMs);
        opened.setSoTimeout(timeoutMs);
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(opened.getOutputStream(), ProtocolCodec.CHARSET));
        BufferedReader in = new BufferedReader(new InputStreamReader(opened.getInputStream(), ProtocolCodec.CHARSET));
        socket = opened;
        writer = out;
        try {
            send(Message.of(MessageType.REGISTER, train.trainId(), train.startStationId()));
        } catch (RuntimeException e) {
            closeSocket();
            throw new IOException(e.getMessage(), e);
        }
        Thread reader = new Thread(() -> readLoop(in), "train-reader-" + train.trainId());
        reader.setDaemon(true);
        reader.start();
        notifyConnected(address.getHostAddress() + ":" + port);
    }

    private void readLoop(BufferedReader in) {
        boolean dropped = false;
        try {
            while (!closed) {
                String line;
                try {
                    line = in.readLine();
                } catch (SocketTimeoutException e) {
                    continue;
                }
                if (line == null) {
                    dropped = !closed;
                    break;
                }
                try {
                    Message message = ProtocolCodec.decode(line);
                    for (TrainClientListener listener : listeners) {
                        listener.onMessage(message);
                    }
                } catch (ProtocolException e) {
                    notifyError(e.getMessage());
                }
            }
        } catch (IOException e) {
            dropped = !closed;
            if (dropped) {
                notifyError("Connection lost: " + e.getMessage());
            }
        } finally {
            synchronized (connectLock) {
                if (dropped) {
                    closeSocket();
                }
            }
            if (dropped) {
                notifyDisconnected("connection closed");
                Thread reconnect = new Thread(() -> connect(connectedHost, connectedPort),
                        "train-reconnect-" + train.trainId());
                reconnect.setDaemon(true);
                reconnect.start();
            }
        }
    }

    private void closeSocket() {
        Socket current = socket;
        socket = null;
        writer = null;
        if (current != null) {
            try {
                current.close();
            } catch (IOException ignored) {
                // Closing a failed socket is best-effort.
            }
        }
    }

    private void notifyConnected(String serverAddress) {
        for (TrainClientListener listener : listeners) {
            listener.onConnected(serverAddress);
        }
    }

    private void notifyDisconnected(String reason) {
        for (TrainClientListener listener : listeners) {
            listener.onDisconnected(reason);
        }
    }

    private void notifyError(String error) {
        for (TrainClientListener listener : listeners) {
            listener.onError(error);
        }
    }

    private static String failure(String host, int port, IOException error) {
        if (error instanceof UnknownHostException) {
            return error.getMessage();
        }
        if (error instanceof SocketTimeoutException) {
            return "Timed out connecting to " + host + ":" + port;
        }
        String detail = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return "Could not connect to " + host + ":" + port + " (" + detail + ")";
    }

    private static void sleep(int delayMs) {
        try {
            Thread.sleep(Math.max(0, delayMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
