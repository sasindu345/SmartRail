package com.smartrail.common.protocol;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Text encoding of the SmartRail protocol: UTF-8, {@code TYPE|field1|field2}, one message per line
 * ({@code \n}). This class only converts between a single line and a {@link Message}; splitting a TCP
 * byte stream into lines (partial reads, several messages per read, size limit) is the job of each
 * transport (Member 1 NIO, Member 4 TLS, Member 2 client). SHARED CONTRACT (frozen).
 */
public final class ProtocolCodec {

    public static final String DELIMITER = "|";
    public static final char LINE_END = '\n';
    public static final Charset CHARSET = StandardCharsets.UTF_8;

    private ProtocolCodec() {
    }

    /** {@code TYPE|f1|f2} without the line terminator. */
    public static String encode(Message message) {
        StringBuilder sb = new StringBuilder(message.type().name());
        for (String field : message.fields()) {
            sb.append(DELIMITER).append(field);
        }
        return sb.toString();
    }

    /** Encoded line including the trailing {@code \n}, as UTF-8 bytes ready to write to a socket. */
    public static byte[] toWireBytes(Message message) {
        return (encode(message) + LINE_END).getBytes(CHARSET);
    }

    /**
     * Parses one line (a trailing {@code \r} or {@code \n} is ignored).
     *
     * @throws ProtocolException with a specific {@link ErrorCode} for any malformed input
     */
    public static Message decode(String line) throws ProtocolException {
        if (line == null) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "null line");
        }
        String trimmed = stripLineEnd(line);
        if (trimmed.isEmpty()) {
            throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "empty line");
        }
        List<String> parts = split(trimmed);
        MessageType type;
        try {
            type = MessageType.valueOf(parts.get(0));
        } catch (IllegalArgumentException e) {
            throw new ProtocolException(ErrorCode.UNKNOWN_TYPE, parts.get(0));
        }
        List<String> fields = parts.subList(1, parts.size());
        if (fields.size() != type.fieldCount()) {
            throw new ProtocolException(ErrorCode.BAD_FIELD_COUNT,
                    type + " expects " + type.fieldCount() + " fields but got " + fields.size());
        }
        for (String field : fields) {
            if (field.isEmpty()) {
                throw new ProtocolException(ErrorCode.INVALID_MESSAGE, "empty field in " + type);
            }
        }
        return new Message(type, fields);
    }

    private static String stripLineEnd(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\n' || line.charAt(end - 1) == '\r')) {
            end--;
        }
        return line.substring(0, end);
    }

    private static List<String> split(String line) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int idx;
        while ((idx = line.indexOf(DELIMITER, start)) >= 0) {
            parts.add(line.substring(start, idx));
            start = idx + DELIMITER.length();
        }
        parts.add(line.substring(start));
        return parts;
    }
}
