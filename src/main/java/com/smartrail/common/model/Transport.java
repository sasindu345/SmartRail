package com.smartrail.common.model;

/** Which train-control channel a train uses. SHARED CONTRACT (frozen). */
public enum Transport {
    /** Plain TCP to Member 1's Java NIO server. */
    PLAIN,
    /** TLS to Member 4's SSLServerSocket server. */
    TLS
}
