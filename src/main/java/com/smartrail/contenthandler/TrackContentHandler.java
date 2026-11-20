package com.smartrail.contenthandler;

import com.smartrail.common.model.TrackInfo;
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
 * OWNER: Member 5 - CONCEPT: content handler for {@code application/x-smartrail-track}.
 * <ul>
 *   <li>Reads connection.getInputStream() as UTF-8; ResourceCodec.decode -> ResourceCodec.toTrack</li>
 *   <li>Returns {@code List<TrackInfo>} (one or many records)</li>
 *   <li>Malformed payload -> IOException with a clear message</li>
 * </ul>
 * Works for BOTH http:// (Member 4) and smartrail:// (Member 5) connections - it only sees the content.
 */
public final class TrackContentHandler extends ContentHandler {

    @Override
    public Object getContent(URLConnection connection) throws IOException {
        String body;
        try (InputStream in = connection.getInputStream()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try {
            List<Map<String, String>> records = ResourceCodec.decode(body);
            List<TrackInfo> tracks = new ArrayList<>(records.size());
            for (Map<String, String> record : records) {
                tracks.add(ResourceCodec.toTrack(record));
            }
            return List.copyOf(tracks);
        } catch (IllegalArgumentException e) {
            throw new IOException("Malformed track resource payload: " + e.getMessage(), e);
        }
    }
}
