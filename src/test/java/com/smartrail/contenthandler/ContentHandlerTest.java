package com.smartrail.contenthandler;

import com.smartrail.common.model.ScheduleInfo;
import com.smartrail.common.model.SectionState;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.resource.ResourceTypes;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * OWNER: Member 5 - tests for content handlers.
 * Verifies that UTF-8 MIME streams are decoded into typed model objects (TrainInfo, TrackInfo, ScheduleInfo)
 * and that malformed inputs throw appropriate IOExceptions.
 */
class ContentHandlerTest {

    @Test
    @SuppressWarnings("unchecked")
    void trainPayloadBecomesTrainInfo() throws Exception {
        String payload = """
                trainId=TR01
                name=Express 1
                status=MOVING
                location=SEC_A
                routeId=R01
                transport=PLAIN
                remoteAddress=127.0.0.1:5000
                lastSeenMillis=1700000000000
                """;

        URLConnection conn = fakeConnection(payload, ResourceTypes.MIME_TRAIN);
        TrainContentHandler handler = new TrainContentHandler();
        Object result = handler.getContent(conn);

        assertNotNull(result);
        assertInstanceOf(List.class, result);
        List<TrainInfo> trains = (List<TrainInfo>) result;
        assertEquals(1, trains.size());
        TrainInfo t = trains.get(0);
        assertEquals("TR01", t.trainId());
        assertEquals("Express 1", t.name());
        assertEquals(TrainStatus.MOVING, t.status());
        assertEquals("SEC_A", t.location());
    }

    @Test
    @SuppressWarnings("unchecked")
    void trackPayloadBecomesTrackInfo() throws Exception {
        String payload = """
                sectionId=SEC_A
                from=ST01
                to=ST02
                state=RESERVED
                holder=TR01
                """;

        URLConnection conn = fakeConnection(payload, ResourceTypes.MIME_TRACK);
        TrackContentHandler handler = new TrackContentHandler();
        Object result = handler.getContent(conn);

        assertNotNull(result);
        assertInstanceOf(List.class, result);
        List<TrackInfo> tracks = (List<TrackInfo>) result;
        assertEquals(1, tracks.size());
        TrackInfo track = tracks.get(0);
        assertEquals("SEC_A", track.sectionId());
        assertEquals("ST01", track.fromStationId());
        assertEquals("ST02", track.toStationId());
        assertEquals(SectionState.RESERVED, track.state());
        assertEquals("TR01", track.holderTrainId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void schedulePayloadBecomesScheduleInfo() throws Exception {
        String payload = """
                trainId=TR01
                stationId=ST01
                time=10:15
                """;

        URLConnection conn = fakeConnection(payload, ResourceTypes.MIME_SCHEDULE);
        ScheduleContentHandler handler = new ScheduleContentHandler();
        Object result = handler.getContent(conn);

        assertNotNull(result);
        assertInstanceOf(List.class, result);
        List<ScheduleInfo> schedules = (List<ScheduleInfo>) result;
        assertEquals(1, schedules.size());
        ScheduleInfo s = schedules.get(0);
        assertEquals("TR01", s.trainId());
        assertEquals("ST01", s.stationId());
        assertEquals("10:15", s.time());
    }

    @Test
    void malformedPayloadThrows() {
        String malformedMissingEquals = "invalid_line_without_equals\n";
        URLConnection badConn1 = fakeConnection(malformedMissingEquals, ResourceTypes.MIME_TRAIN);
        assertThrows(IOException.class, () -> new TrainContentHandler().getContent(badConn1));

        String missingRequiredField = "name=MissingTrainId\n";
        URLConnection badConn2 = fakeConnection(missingRequiredField, ResourceTypes.MIME_TRAIN);
        assertThrows(IOException.class, () -> new TrainContentHandler().getContent(badConn2));
    }

    @Test
    void factoryMapsMimeTypesCorrectly() {
        SmartRailContentHandlerFactory factory = new SmartRailContentHandlerFactory();
        assertInstanceOf(TrainContentHandler.class, factory.createContentHandler(ResourceTypes.MIME_TRAIN));
        assertInstanceOf(TrackContentHandler.class, factory.createContentHandler(ResourceTypes.MIME_TRACK));
        assertInstanceOf(ScheduleContentHandler.class, factory.createContentHandler(ResourceTypes.MIME_SCHEDULE));
        assertNull(factory.createContentHandler("application/unknown"));
        assertNull(factory.createContentHandler(null));
    }

    private static URLConnection fakeConnection(String body, String mimeType) {
        try {
            URL url = new URI("smartrail://localhost:5100/test").toURL();
            return new URLConnection(url) {
                @Override
                public void connect() {
                    connected = true;
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
                }

                @Override
                public String getContentType() {
                    return mimeType;
                }
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
