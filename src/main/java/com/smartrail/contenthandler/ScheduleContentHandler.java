package com.smartrail.contenthandler;

import com.smartrail.common.model.ScheduleInfo;
import com.smartrail.common.resource.ResourceCodec;

import java.io.IOException;
import java.io.InputStream;
import java.net.ContentHandler;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OWNER: Member 5 - CONCEPT: content handler for {@code application/x-smartrail-schedule}.
 * <ul>
 *   <li>Reads connection.getInputStream() as UTF-8; ResourceCodec.decode -> ResourceCodec.toSchedule</li>
 *   <li>Returns {@code List<ScheduleInfo>} (one or many records)</li>
 *   <li>Malformed payload -> IOException with a clear message</li>
 * </ul>
 * Works for BOTH http:// (Member 4) and smartrail:// (Member 5) connections - it only sees the content.
 */
public final class ScheduleContentHandler extends ContentHandler {

    @Override
    public Object getContent(URLConnection connection) throws IOException {
        String body;
        try (InputStream in = connection.getInputStream()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            List<Map<String, String>> records = ResourceCodec.decode(body);
            List<ScheduleInfo> schedules = new ArrayList<>(records.size());
            for (Map<String, String> record : records) {
                schedules.add(ResourceCodec.toSchedule(record));
            }
            return List.copyOf(schedules);
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed schedule resource payload: " + e.getMessage(), e);
        }
    }
}
