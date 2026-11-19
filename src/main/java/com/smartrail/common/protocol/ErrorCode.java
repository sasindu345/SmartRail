package com.smartrail.common.protocol;

/** Codes sent in {@code ERROR|code|detail}. SHARED CONTRACT (frozen). */
public enum ErrorCode {
    INVALID_MESSAGE,
    UNKNOWN_TYPE,
    BAD_FIELD_COUNT,
    MESSAGE_TOO_LARGE,
    NOT_REGISTERED,
    IDENTITY_MISMATCH,
    UNKNOWN_TRAIN,
    UNKNOWN_SECTION,
    ILLEGAL_STATE
}
