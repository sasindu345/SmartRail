package com.smartrail.common.service;

import com.smartrail.common.protocol.Message;
import com.smartrail.common.protocol.ProtocolException;

/**
 * Transport-independent processing of decoded train messages. Implemented once by Member 1
 * ({@code TrainMessageRouter}) and called by BOTH the NIO server (Member 1) and the TLS server
 * (Member 4). SHARED CONTRACT (frozen).
 */
public interface MessageHandler {

    /** A complete, decoded message arrived from {@code connection}. */
    void onMessage(TrainConnection connection, Message message);

    /** A line could not be decoded; the handler decides whether to reply with ERROR or close. */
    void onProtocolError(TrainConnection connection, ProtocolException error);

    /** The connection closed (client quit, network error or server shutdown). */
    void onDisconnect(TrainConnection connection);
}
