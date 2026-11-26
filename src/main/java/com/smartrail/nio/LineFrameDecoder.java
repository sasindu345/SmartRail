package com.smartrail.nio;

import com.smartrail.common.protocol.ErrorCode;
import com.smartrail.common.protocol.ProtocolException;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * OWNER: Member 1 - CONCEPT: message framing over a TCP byte stream with NIO {@link ByteBuffer}.
 *
 * <p>TCP has no message boundaries: one read may hold half a message or several. This decoder keeps
 * leftover bytes between reads and returns every complete {@code \n}-terminated line (UTF-8).
 * A line longer than {@code protocol.max.message.bytes} is discarded and reported as
 * {@link ErrorCode#MESSAGE_TOO_LARGE}.
 */
public final class LineFrameDecoder {

    private final int maxMessageBytes;
    private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
    private boolean discarding;

    public LineFrameDecoder(int maxMessageBytes) {
        this.maxMessageBytes = maxMessageBytes;
    }

    /**
     * @param readBuffer buffer in READ mode (already flipped) containing newly received bytes
     * @return complete lines without the line terminator (may be empty)
     */
    public List<String> decode(ByteBuffer readBuffer) throws ProtocolException {
        List<String> lines = new ArrayList<>();
        ProtocolException tooLarge = null;
        while (readBuffer.hasRemaining()) {
            byte b = readBuffer.get();
            if (discarding) {
                if (b == '\n') {
                    discarding = false;
                }
                continue;
            }
            if (b == '\n') {
                String line = pending.toString(StandardCharsets.UTF_8);
                pending.reset();
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lines.add(line);
            } else if (pending.size() + 1 > maxMessageBytes) {
                pending.reset();
                discarding = true;
                tooLarge = new ProtocolException(ErrorCode.MESSAGE_TOO_LARGE,
                        "line exceeds " + maxMessageBytes + " bytes");
            } else {
                pending.write(b);
            }
        }
        if (tooLarge != null) {
            throw tooLarge;
        }
        return lines;
    }
}
