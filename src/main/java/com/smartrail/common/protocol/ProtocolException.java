package com.smartrail.common.protocol;

/** A message could not be decoded or validated. SHARED CONTRACT (frozen). */
public class ProtocolException extends Exception {

    private final ErrorCode code;

    public ProtocolException(ErrorCode code, String detail) {
        super(code + ": " + detail);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    /** Ready-to-send {@code ERROR|code|detail} reply. */
    public Message toErrorMessage() {
        return Message.error(code, getMessage());
    }
}
