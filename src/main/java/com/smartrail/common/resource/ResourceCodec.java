package com.smartrail.common.resource;

import com.smartrail.common.model.ScheduleInfo;
import com.smartrail.common.model.SectionState;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.common.model.Transport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The SmartRail resource representation (body of every {@code application/x-smartrail-*} payload):
 * UTF-8 text, one {@code key=value} per line, records separated by one blank line. Empty value = null.
 *
 * <pre>
 * trainId=TR01
 * status=MOVING
 *
 * trainId=TR02
 * status=HELD
 * </pre>
 *
 * Member 4's HTTP server and Member 5's smartrail:// server ENCODE with this class; Member 5's
 * ContentHandlers DECODE with it. SHARED CONTRACT (frozen).
 */
public final class ResourceCodec {

    private ResourceCodec() {
    }

    // ---------- text <-> records ----------

    public static String encode(List<Map<String, String>> records) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            for (Map.Entry<String, String> e : records.get(i).entrySet()) {
                String value = e.getValue() == null ? "" : e.getValue();
                if (value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
                    throw new IllegalArgumentException("Line break in value of " + e.getKey());
                }
                sb.append(e.getKey()).append('=').append(value).append('\n');
            }
        }
        return sb.toString();
    }

    public static List<Map<String, String>> decode(String body) {
        List<Map<String, String>> records = new ArrayList<>();
        Map<String, String> current = new LinkedHashMap<>();
        for (String rawLine : body.split("\n", -1)) {
            String line = rawLine.endsWith("\r") ? rawLine.substring(0, rawLine.length() - 1) : rawLine;
            if (line.isEmpty()) {
                if (!current.isEmpty()) {
                    records.add(current);
                    current = new LinkedHashMap<>();
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("Malformed resource line: " + line);
            }
            String value = line.substring(eq + 1);
            current.put(line.substring(0, eq), value.isEmpty() ? null : value);
        }
        if (!current.isEmpty()) {
            records.add(current);
        }
        return records;
    }

    // ---------- model -> record ----------

    public static Map<String, String> toRecord(TrainInfo t) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("trainId", t.trainId());
        m.put("name", t.name());
        m.put("status", t.status() == null ? null : t.status().name());
        m.put("location", t.location());
        m.put("routeId", t.routeId());
        m.put("transport", t.transport() == null ? null : t.transport().name());
        m.put("remoteAddress", t.remoteAddress());
        m.put("lastSeenMillis", Long.toString(t.lastSeenMillis()));
        return m;
    }

    public static Map<String, String> toRecord(TrackInfo t) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("sectionId", t.sectionId());
        m.put("from", t.fromStationId());
        m.put("to", t.toStationId());
        m.put("state", t.state().name());
        m.put("holder", t.holderTrainId());
        return m;
    }

    public static Map<String, String> toRecord(ScheduleInfo s) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("trainId", s.trainId());
        m.put("stationId", s.stationId());
        m.put("time", s.time());
        return m;
    }

    public static Map<String, String> toRecord(StationInfo s) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("stationId", s.stationId());
        m.put("name", s.name());
        return m;
    }

    // ---------- record -> model ----------

    public static TrainInfo toTrain(Map<String, String> m) {
        String status = m.get("status");
        String transport = m.get("transport");
        String lastSeen = m.get("lastSeenMillis");
        return new TrainInfo(require(m, "trainId"), m.get("name"),
                status == null ? null : TrainStatus.valueOf(status), m.get("location"), m.get("routeId"),
                transport == null ? null : Transport.valueOf(transport), m.get("remoteAddress"),
                lastSeen == null ? 0L : Long.parseLong(lastSeen));
    }

    public static TrackInfo toTrack(Map<String, String> m) {
        return new TrackInfo(require(m, "sectionId"), m.get("from"), m.get("to"),
                SectionState.valueOf(require(m, "state")), m.get("holder"));
    }

    public static ScheduleInfo toSchedule(Map<String, String> m) {
        return new ScheduleInfo(require(m, "trainId"), require(m, "stationId"), require(m, "time"));
    }

    public static StationInfo toStation(Map<String, String> m) {
        return new StationInfo(require(m, "stationId"), m.get("name"));
    }

    private static String require(Map<String, String> m, String key) {
        String v = m.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing field '" + key + "' in resource record");
        }
        return v;
    }
}
