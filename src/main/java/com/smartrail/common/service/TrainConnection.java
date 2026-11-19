package com.smartrail.common.service;

import com.smartrail.common.model.Transport;
import com.smartrail.common.protocol.Message;

/**
 * One connected train client, independent of transport. Implemented by Member 1
 * ({@code NioTrainConnection}) and Member 4 ({@code TlsTrainConnection}) so the shared
 * {@link MessageHandler} and services can reply/push without knowing which channel is used.
 * SHARED CONTRACT (frozen).
 */
public interface TrainConnection {

    /** Queues a message for the train. Thread-safe; must not block the caller for long. */
    void send(Message message);

    /** Remote "ip:port" of the train client. */
    String remoteAddress();

    Transport transport();

    boolean isOpen();

    void close();
}
