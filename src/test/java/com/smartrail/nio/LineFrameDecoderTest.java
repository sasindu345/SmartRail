package com.smartrail.nio;

import com.smartrail.common.protocol.ErrorCode;
import com.smartrail.common.protocol.ProtocolException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OWNER: Member 1 - framing over a byte stream: partial reads, merged reads, size limit. */
class LineFrameDecoderTest {

    @Test
    void partialMessageIsBufferedUntilNewline() throws Exception {
        LineFrameDecoder decoder = new LineFrameDecoder(1024);
        assertTrue(decoder.decode(bytes("REGIS")).isEmpty());
        assertEquals(List.of("REGISTER|TR01|ST01"), decoder.decode(bytes("TER|TR01|ST01\n")));
    }

    @Test
    void twoMessagesInOneReadAreBothReturned() throws Exception {
        LineFrameDecoder decoder = new LineFrameDecoder(1024);
        List<String> lines = decoder.decode(bytes("REGISTER|TR01|ST01\nHEARTBEAT|TR01\n"));
        assertEquals(List.of("REGISTER|TR01|ST01", "HEARTBEAT|TR01"), lines);
    }

    @Test
    void oversizedLineIsRejected() {
        LineFrameDecoder decoder = new LineFrameDecoder(8);
        ProtocolException error = assertThrows(ProtocolException.class, () -> decoder.decode(bytes("0123456789\n")));
        assertEquals(ErrorCode.MESSAGE_TOO_LARGE, error.code());
    }

    private static ByteBuffer bytes(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
    }
}
