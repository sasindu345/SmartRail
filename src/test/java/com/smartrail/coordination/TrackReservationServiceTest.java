package com.smartrail.coordination;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.ReservationResult;
import com.smartrail.common.model.SectionState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OWNER: Member 1 - atomic check-and-reserve. Two threads cannot both win one section. */
class TrackReservationServiceTest {

    private final RailwayData data = RailwayData.load(Path.of("config/railway-data.properties"));

    @Test
    void freeSectionIsGranted() {
        TrackReservationService sections = new TrackReservationService(data);
        ReservationResult result = sections.reserve("TR01", "SEC_A");
        assertTrue(result.granted());
        assertEquals(SectionState.RESERVED, sections.snapshot("SEC_A").orElseThrow().state());
        assertEquals("TR01", sections.snapshot("SEC_A").orElseThrow().holderTrainId());
    }

    @Test
    void reservedSectionIsDeniedWithHolder() {
        TrackReservationService sections = new TrackReservationService(data);
        assertTrue(sections.reserve("TR01", "SEC_A").granted());
        ReservationResult denied = sections.reserve("TR03", "SEC_A");
        assertFalse(denied.granted());
        assertEquals("TR01", denied.holderTrainId());
        assertEquals("RESERVED_BY_TR01", denied.reason());
    }

    @Test
    void concurrentRequestsOnlyOneWins() throws Exception {
        for (int i = 0; i < 100; i++) {
            TrackReservationService sections = new TrackReservationService(data);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(2);
            AtomicInteger granted = new AtomicInteger();
            startRacer(sections, "TR01", start, done, granted);
            startRacer(sections, "TR03", start, done, granted);
            start.countDown();
            done.await();
            assertEquals(1, granted.get(), "iteration " + i);
        }
    }

    @Test
    void releaseFreesSection() {
        TrackReservationService sections = new TrackReservationService(data);
        sections.reserve("TR01", "SEC_A");
        assertTrue(sections.release("TR01", "SEC_A"));
        assertEquals(SectionState.FREE, sections.snapshot("SEC_A").orElseThrow().state());
        assertTrue(sections.reserve("TR03", "SEC_A").granted());
    }

    private static void startRacer(TrackReservationService sections, String trainId, CountDownLatch start,
                                   CountDownLatch done, AtomicInteger granted) {
        Thread thread = new Thread(() -> {
            try {
                start.await();
                if (sections.reserve(trainId, "SEC_A").granted()) {
                    granted.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                done.countDown();
            }
        });
        thread.start();
    }
}
