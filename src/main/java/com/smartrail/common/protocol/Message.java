package com.smartrail.common.protocol;

import java.util.List;

/**
 * One protocol message: a type plus its fields. Construction validates the field count and
 * that no field is empty or contains the delimiter / a line break, so an invalid message can
 * never be sent. SHARED CONTRACT (frozen).
 */
public record Message(MessageType type, List<String> fields) {

    public Message {
        if (type == null) {
            throw new IllegalArgumentException("type is required");
        }
        fields = List.copyOf(fields);
        if (fields.size() != type.fieldCount()) {
            throw new IllegalArgumentException(type + " needs " + type.fieldCount() + " fields, got " + fields.size());
        }
        for (String field : fields) {
            if (field.isEmpty() || field.contains(ProtocolCodec.DELIMITER) || field.indexOf('\n') >= 0
                    || field.indexOf('\r') >= 0) {
                throw new IllegalArgumentException("Illegal field value in " + type + ": '" + field + "'");
            }
        }
    }

    public static Message of(MessageType type, String... fields) {
        return new Message(type, List.of(fields));
    }

    /** Builds {@code ERROR|code|detail}; the detail is sanitised so it is always a legal field. */
    public static Message error(ErrorCode code, String detail) {
        String safe = detail == null ? "" : detail.replace(ProtocolCodec.DELIMITER, "/").replaceAll("[\\r\\n]", " ").trim();
        return of(MessageType.ERROR, code.name(), safe.isEmpty() ? code.name() : safe);
    }

    /** Field by zero-based index (the type is not counted). */
    public String field(int index) {
        return fields.get(index);
    }

    @Override
    public String toString() {
        return ProtocolCodec.encode(this);
    }
}
