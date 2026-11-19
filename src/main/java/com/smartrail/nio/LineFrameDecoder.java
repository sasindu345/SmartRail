package com.smartrail.nio;

import com.smartrail.common.protocol.ProtocolException;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * OWNER: Member 1 - CONCEPT: message framing over a TCP byte stream with NIO {@link ByteBuffer}.
 *
 * <p>TCP has no message boundaries: one read may hold half a message or several. This decoder keeps
 * leftover bytes between reads and returns every COMPLETE {@code \n}-terminated line (UTF-8).
 * <ul>
 *   <li>TODO(M1) accumulate bytes; split on '\n'; keep the incomplete tail for the next call</li>
 *   <li>TODO(M1) throw ProtocolException(MESSAGE_TOO_LARGE) when a line exceeds maxMessageBytes
 *       ({@code protocol.max.message.bytes}) and discard it</li>
 * </ul>
 */
public final class LineFrameDecoder {

    private final int maxMessageBytes;

    public LineFrameDecoder(int maxMessageBytes) {
        this.maxMessageBytes = maxMessageBytes;
    }

    /**
     * @param readBuffer buffer in READ mode (already flipped) containing newly received bytes
     * @return complete lines without the line terminator (may be empty)
     */
    public List<String> decode(ByteBuffer readBuffer) throws ProtocolException {
        throw new UnsupportedOperationException("TODO(M1): partial/multiple line framing, max " + maxMessageBytes + " bytes");
    }
}
