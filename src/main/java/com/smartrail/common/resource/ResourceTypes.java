package com.smartrail.common.resource;

/**
 * Protocol constants for railway resources: the custom URL scheme, resource collections and the
 * SmartRail MIME types. Used by Member 4 (HTTP + URLConnection) and Member 5 (protocol + content
 * handlers) so both address and label resources identically. SHARED CONTRACT (frozen).
 */
public final class ResourceTypes {

    private ResourceTypes() {
    }

    /** Custom URL scheme handled by Member 5: {@code smartrail://host:port/trains/TR01}. */
    public static final String SCHEME = "smartrail";

    // Collections (first path segment)
    public static final String TRAINS = "trains";
    public static final String TRACKS = "tracks";
    public static final String SCHEDULES = "schedules";
    public static final String STATIONS = "stations";

    // MIME types interpreted by Member 5's ContentHandlers
    public static final String MIME_TRAIN = "application/x-smartrail-train";
    public static final String MIME_TRACK = "application/x-smartrail-track";
    public static final String MIME_SCHEDULE = "application/x-smartrail-schedule";
    public static final String MIME_STATION = "application/x-smartrail-station";
    public static final String MIME_ERROR = "text/plain";

    /** MIME type for a collection name, or {@code null} if unknown. */
    public static String mimeFor(String collection) {
        return switch (collection) {
            case TRAINS -> MIME_TRAIN;
            case TRACKS -> MIME_TRACK;
            case SCHEDULES -> MIME_SCHEDULE;
            case STATIONS -> MIME_STATION;
            default -> null;
        };
    }
}
