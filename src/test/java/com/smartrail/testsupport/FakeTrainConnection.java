package com.smartrail.testsupport;

import com.smartrail.common.model.Transport;
import com.smartrail.common.protocol.Message;
import com.smartrail.common.service.TrainConnection;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records every message "sent" to a train. Use it to unit-test handlers without sockets. */
public final class FakeTrainConnection implements TrainConnection {

    private final String remoteAddress;
    private final Transport transport;
    private final List<Message> sent = new CopyOnWriteArrayList<>();
    private volatile boolean open = true;

    public FakeTrainConnection(String remoteAddress, Transport transport) {
        this.remoteAddress = remoteAddress;
        this.transport = transport;
    }

    public List<Message> sent() {
        return sent;
    }

    @Override
    public void send(Message message) {
        sent.add(message);
    }

    @Override
    public String remoteAddress() {
        return remoteAddress;
    }

    @Override
    public Transport transport() {
        return transport;
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    @Override
    public void close() {
        open = false;
    }
}
