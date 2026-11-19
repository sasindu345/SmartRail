package com.smartrail.contenthandler;

import com.smartrail.common.resource.ResourceTypes;

import java.net.ContentHandler;
import java.net.ContentHandlerFactory;

/**
 * OWNER: Member 5 - CONCEPT: content handler factory - maps a MIME type to the handler that
 * INTERPRETS the returned content. Installed once per JVM by ControllerApp with
 * {@code URLConnection.setContentHandlerFactory(...)}.
 * <ul>
 *   <li>{@link ResourceTypes#MIME_TRAIN} -> TrainContentHandler,
 *       {@link ResourceTypes#MIME_TRACK} -> TrackContentHandler,
 *       {@link ResourceTypes#MIME_SCHEDULE} -> ScheduleContentHandler; anything else -> null (JDK default)</li>
 * </ul>
 */
public final class SmartRailContentHandlerFactory implements ContentHandlerFactory {

    @Override
    public ContentHandler createContentHandler(String mimeType) {
        if (mimeType == null) {
            return null;
        }
        return switch (mimeType) {
            case ResourceTypes.MIME_TRAIN -> new TrainContentHandler();
            case ResourceTypes.MIME_TRACK -> new TrackContentHandler();
            case ResourceTypes.MIME_SCHEDULE -> new ScheduleContentHandler();
            default -> null;
        };
    }
}
