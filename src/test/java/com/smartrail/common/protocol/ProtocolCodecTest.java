package com.smartrail.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolCodecTest {

    @Test
    void roundTrip() throws ProtocolException {
        Message m = Message.of(MessageType.SECTION_DENIED, "TR01", "SEC_B", "RESERVED_BY_TR02");
        assertEquals("SECTION_DENIED|TR01|SEC_B|RESERVED_BY_TR02", ProtocolCodec.encode(m));
        assertEquals(m, ProtocolCodec.decode(ProtocolCodec.encode(m)));
    }

    @Test
    void wireBytesEndWithNewline() {
        assertArrayEquals("HEARTBEAT|TR01\n".getBytes(ProtocolCodec.CHARSET),
                ProtocolCodec.toWireBytes(Message.of(MessageType.HEARTBEAT, "TR01")));
    }

    @Test
    void decodeIgnoresCrLf() throws ProtocolException {
        assertEquals(MessageType.HOLD, ProtocolCodec.decode("HOLD|TR01\r\n").type());
    }

    @Test
    void rejectsUnknownType() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("FLY|TR01"));
        assertEquals(ErrorCode.UNKNOWN_TYPE, e.code());
    }

    @Test
    void rejectsWrongFieldCount() {
        ProtocolException e = assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("REGISTER|TR01"));
        assertEquals(ErrorCode.BAD_FIELD_COUNT, e.code());
    }

    @Test
    void rejectsEmptyField() {
        assertThrows(ProtocolException.class, () -> ProtocolCodec.decode("REGISTER||ST01"));
    }

    @Test
    void cannotBuildMessageWithDelimiterInField() {
        assertThrows(IllegalArgumentException.class, () -> Message.of(MessageType.HEARTBEAT, "TR|01"));
    }

    @Test
    void errorMessageSanitisesDetail() {
        Message e = Message.error(ErrorCode.INVALID_MESSAGE, "bad|line\nhere");
        assertEquals("ERROR|INVALID_MESSAGE|bad/line here", ProtocolCodec.encode(e));
    }
}
