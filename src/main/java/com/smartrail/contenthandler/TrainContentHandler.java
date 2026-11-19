package com.smartrail.contenthandler;

import java.io.IOException;
import java.net.ContentHandler;
import java.net.URLConnection;

/**
 * OWNER: Member 5 - CONCEPT: content handler for {@code application/x-smartrail-train}.
 * <ul>
 *   <li>TODO(M5) read connection.getInputStream() as UTF-8; ResourceCodec.decode -> ResourceCodec.toTrain</li>
 *   <li>TODO(M5) return {@code List<TrainInfo>} (one or many records)</li>
 *   <li>TODO(M5) malformed payload -> IOException with a clear message</li>
 * </ul>
 * Works for BOTH http:// (Member 4) and smartrail:// (Member 5) connections - it only sees the content.
 */
public final class TrainContentHandler extends ContentHandler {

    @Override
    public Object getContent(URLConnection connection) throws IOException {
        throw new UnsupportedOperationException("TODO(M5): parse TRAIN payload from " + connection.getURL());
    }
}
