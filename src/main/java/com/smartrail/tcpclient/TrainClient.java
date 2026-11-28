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
import java.net.Inet6Address;
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
    private final Object stateLock = new Object();
    private final Object writeLock = new Object();

    private volatile Socket socket;
    private volatile BufferedWriter writer;
    private volatile boolean closed;
    private volatile boolean connected;
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
        if (listener != null) {
            listeners.add(listener);
        }
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
            synchronized (stateLock) {
                connectedHost = host;
                connectedPort = port;
            }
            int attempts = Math.max(1, config.getInt(ConfigKeys.CLIENT_RECONNECT_ATTEMPTS));
            int delayMs = config.getInt(ConfigKeys.CLIENT_RECONNECT_DELAY_MS);
            for (int attempt = 1; attempt <= attempts && !closed; attempt++) {
                try {
                    open(host, port);
                    return;
                } catch (IOException e) {
                    if (!closed) {
                        notifyError(failure(host, port, e));
                    }
                    if (attempt < attempts && !closed) {
                        waitBeforeRetry(delayMs);
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
        synchronized (stateLock) {
            Socket current = socket;
            return connected && current != null && current.isConnected() && !current.isClosed();
        }
    }

    public void disconnect() {
        closed = true;
        boolean wasConnected;
        synchronized (stateLock) {
            wasConnected = connected;
            connected = false;
            closeSocketLocked();
            stateLock.notifyAll();
        }
        if (wasConnected) {
            notifyDisconnected("disconnected");
        }
    }

    private void open(String host, int port) throws IOException {
        InetAddress address = resolver.resolve(host);
        System.out.println("[TRAIN] resolved " + host + " -> " + resolver.describe(address));
        int timeoutMs = config.getInt(ConfigKeys.CLIENT_CONNECT_TIMEOUT_MS);
        Socket opened = socketFactory.connect(new InetSocketAddress(address, port), timeoutMs);
        try {
            opened.setSoTimeout(timeoutMs);
            if (closed) {
                closeQuietly(opened);
                return;
            }
            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(opened.getOutputStream(), ProtocolCodec.CHARSET));
            BufferedReader in = new BufferedReader(new InputStreamReader(opened.getInputStream(), ProtocolCodec.CHARSET));
            synchronized (stateLock) {
                if (closed) {
                    closeQuietly(opened);
                    return;
                }
                socket = opened;
                writer = out;
            }
            try {
                send(Message.of(MessageType.REGISTER, train.trainId(), train.startStationId()));
            } catch (RuntimeException e) {
                synchronized (stateLock) {
                    closeSocketLocked();
                }
                throw new IOException(e.getMessage(), e);
            }
            synchronized (stateLock) {
                if (closed) {
                    closeSocketLocked();
                    return;
                }
                connected = true;
            }
            Thread reader = new Thread(() -> readLoop(in, opened), "train-reader-" + train.trainId());
            reader.setDaemon(true);
            reader.start();
            notifyConnected(formatEndpoint(address, port));
        } catch (IOException e) {
            closeQuietly(opened);
            throw e;
        }
    }

    private void readLoop(BufferedReader in, Socket activeSocket) {
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
                    notifyError("Malformed message ignored: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            dropped = !closed;
            if (dropped) {
                notifyError("Connection lost: " + e.getMessage());
            }
        } finally {
            boolean notifyDrop = false;
            String hostToReconnect = null;
            int portToReconnect = 0;
            synchronized (stateLock) {
                if (dropped && socket == activeSocket) {
                    notifyDrop = connected;
                    connected = false;
                    closeSocketLocked();
                    hostToReconnect = connectedHost;
                    portToReconnect = connectedPort;
                }
            }
            if (notifyDrop) {
                notifyDisconnected("connection closed");
                String targetHost = hostToReconnect;
                int targetPort = portToReconnect;
                Thread reconnect = new Thread(() -> connect(targetHost, targetPort),
                        "train-reconnect-" + train.trainId());
                reconnect.setDaemon(true);
                reconnect.start();
            }
        }
    }

    private void closeSocketLocked() {
        Socket current = socket;
        socket = null;
        writer = null;
        closeQuietly(current);
    }

    private static void closeQuietly(Socket target) {
        if (target != null) {
            try {
                target.close();
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

    private static String formatEndpoint(InetAddress address, int port) {
        String hostAddress = address.getHostAddress();
        return address instanceof Inet6Address ? "[" + hostAddress + "]:" + port : hostAddress + ":" + port;
    }

    private static String failure(String host, int port, IOException error) {
        if (error instanceof UnknownHostException) {
            return error.getMessage();
        }
        if (error instanceof SocketTimeoutException) {
            return "Timed out connecting to " + host + ":" + port;
        }
        String detail = error.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = "connection failed";
        }
        return "Could not connect to " + host + ":" + port + " (" + detail + ")";
    }

    private void waitBeforeRetry(int delayMs) {
        if (delayMs <= 0) {
            return;
        }
        synchronized (stateLock) {
            long deadline = System.currentTimeMillis() + delayMs;
            while (!closed) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    break;
                }
                try {
                    stateLock.wait(remaining);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }
}
