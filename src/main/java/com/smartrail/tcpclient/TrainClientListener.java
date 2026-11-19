package com.smartrail.tcpclient;

import com.smartrail.common.protocol.Message;

/**
 * OWNER: Member 2. Callbacks from {@link TrainClient}. Called on the client's network thread -
 * UI code must switch to the EDT ({@code UiThreads.onEdt}).
 */
public interface TrainClientListener {

    default void onConnected(String serverAddress) {
    }

    default void onMessage(Message message) {
    }

    default void onDisconnected(String reason) {
    }

    default void onError(String error) {
    }
}
