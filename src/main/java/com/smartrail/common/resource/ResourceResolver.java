package com.smartrail.common.resource;

import com.smartrail.common.service.RailwayService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a resource path into a SmartRail payload using {@link RailwayService}. This is the single
 * piece of resource "business logic", shared by Member 4's HTTP endpoints ({@code /api/...}) and
 * Member 5's {@code smartrail://} resource server, so neither duplicates it. SHARED CONTRACT (frozen).
 *
 * <p>Supported paths: {@code trains}, {@code trains/{id}}, {@code tracks}, {@code tracks/{id}},
 * {@code schedules}, {@code schedules/{trainId}}, {@code stations}, {@code stations/{id}}.
 */
public final class ResourceResolver {

    /** Result with HTTP-style status codes (200, 400, 404); transports map them as they need. */
    public record Response(int status, String mimeType, String body) {
        public static final int OK = 200;
        public static final int BAD_REQUEST = 400;
        public static final int NOT_FOUND = 404;

        public boolean isOk() {
            return status == OK;
        }
    }

    private final RailwayService railway;

    public ResourceResolver(RailwayService railway) {
        this.railway = railway;
    }

    public Response resolve(String rawPath) {
        ResourcePath path;
        try {
            path = ResourcePath.parse(rawPath);
        } catch (IllegalArgumentException e) {
            return error(Response.BAD_REQUEST, e.getMessage());
        }
        String mime = ResourceTypes.mimeFor(path.collection());
        if (mime == null) {
            return error(Response.NOT_FOUND, "Unknown collection: " + path.collection());
        }
        List<Map<String, String>> records = switch (path.collection()) {
            case ResourceTypes.TRAINS -> path.isCollection()
                    ? railway.getTrains().stream().map(ResourceCodec::toRecord).toList()
                    : one(railway.getTrain(path.id()).map(ResourceCodec::toRecord));
            case ResourceTypes.TRACKS -> path.isCollection()
                    ? railway.getTracks().stream().map(ResourceCodec::toRecord).toList()
                    : one(railway.getTrack(path.id()).map(ResourceCodec::toRecord));
            case ResourceTypes.SCHEDULES -> railway.getSchedules().stream()
                    .filter(s -> path.isCollection() || s.trainId().equals(path.id()))
                    .map(ResourceCodec::toRecord).toList();
            case ResourceTypes.STATIONS -> railway.getStations().stream()
                    .filter(s -> path.isCollection() || s.stationId().equals(path.id()))
                    .map(ResourceCodec::toRecord).toList();
            default -> List.of();
        };
        if (!path.isCollection() && records.isEmpty()) {
            return error(Response.NOT_FOUND, "No resource " + path);
        }
        return new Response(Response.OK, mime, ResourceCodec.encode(records));
    }

    private static List<Map<String, String>> one(Optional<Map<String, String>> record) {
        return record.map(List::of).orElse(List.of());
    }

    private static Response error(int status, String message) {
        return new Response(status, ResourceTypes.MIME_ERROR, message + "\n");
    }
}
