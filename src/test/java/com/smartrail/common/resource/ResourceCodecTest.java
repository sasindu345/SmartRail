package com.smartrail.common.resource;

import com.smartrail.common.model.SectionState;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.model.Transport;
import com.smartrail.testsupport.FakeRailwayService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCodecTest {

    @Test
    void trainRoundTrip() {
        TrainInfo t = new TrainInfo("TR01", "Express", TrainStatus.MOVING, "SEC_A", "R01", Transport.TLS, "10.0.0.5:5555", 42L);
        String body = ResourceCodec.encode(List.of(ResourceCodec.toRecord(t)));
        assertEquals(t, ResourceCodec.toTrain(ResourceCodec.decode(body).get(0)));
    }

    @Test
    void nullsSurvive() {
        TrackInfo s = new TrackInfo("SEC_A", "ST01", "ST02", SectionState.FREE, null);
        List<Map<String, String>> records = ResourceCodec.decode(ResourceCodec.encode(List.of(ResourceCodec.toRecord(s))));
        assertEquals(s, ResourceCodec.toTrack(records.get(0)));
    }

    @Test
    void resolverServesCollectionsAndItems() {
        ResourceResolver resolver = new ResourceResolver(FakeRailwayService.withDefaultData());
        ResourceResolver.Response tracks = resolver.resolve("/tracks");
        assertTrue(tracks.isOk());
        assertEquals(ResourceTypes.MIME_TRACK, tracks.mimeType());
        assertEquals(ResourceResolver.Response.NOT_FOUND, resolver.resolve("tracks/NOPE").status());
        assertEquals(ResourceResolver.Response.NOT_FOUND, resolver.resolve("planes").status());
        assertEquals("TR01", ResourceCodec.toTrain(ResourceCodec.decode(resolver.resolve("trains/TR01").body()).get(0)).trainId());
    }
}
