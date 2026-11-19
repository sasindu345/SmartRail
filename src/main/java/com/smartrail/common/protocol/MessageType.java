package com.smartrail.common.protocol;

/**
 * Every message of the SmartRail line protocol and its fixed field count (excluding the type).
 * See docs/protocol.md for direction and meaning. SHARED CONTRACT (frozen).
 */
public enum MessageType {
    // ---- Train <-> server (TCP via NIO or TLS) ----
    /** REGISTER|trainId|stationId */
    REGISTER(2),
    /** REGISTER_OK|trainId */
    REGISTER_OK(1),
    /** ASSIGN_ROUTE|trainId|routeId|SEC_A,SEC_B */
    ASSIGN_ROUTE(3),
    /** POSITION|trainId|location|status */
    POSITION(3),
    /** ACK|trainId|acknowledgedType */
    ACK(2),
    /** REQUEST_SECTION|trainId|sectionId */
    REQUEST_SECTION(2),
    /** SECTION_GRANTED|trainId|sectionId */
    SECTION_GRANTED(2),
    /** SECTION_DENIED|trainId|sectionId|reason */
    SECTION_DENIED(3),
    /** RELEASE_SECTION|trainId|sectionId */
    RELEASE_SECTION(2),
    /** HEARTBEAT|trainId */
    HEARTBEAT(1),
    /** HOLD|trainId (server to train) */
    HOLD(1),
    /** RESUME|trainId (server to train) */
    RESUME(1),
    /** EMERGENCY_STOP|trainId (server to train) */
    EMERGENCY_STOP(1),
    /** ERROR|code|detail */
    ERROR(2),

    // ---- Multicast / unicast alert (UDP) ----
    /** EMERGENCY_ALERT|sectionId|reason */
    EMERGENCY_ALERT(2),

    // ---- UDP discovery ----
    /** DISCOVER|serviceName|protocolVersion */
    DISCOVER(2),
    /** DISCOVER_REPLY|serviceName|protocolVersion|host|plainPort|tlsPort */
    DISCOVER_REPLY(5);

    private final int fieldCount;

    MessageType(int fieldCount) {
        this.fieldCount = fieldCount;
    }

    public int fieldCount() {
        return fieldCount;
    }
}
