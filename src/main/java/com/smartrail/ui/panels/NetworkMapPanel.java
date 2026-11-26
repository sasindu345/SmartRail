package com.smartrail.ui.panels;

import com.smartrail.common.config.RailwayData;
import com.smartrail.common.model.SectionState;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.model.TrainStatus;
import com.smartrail.ui.theme.Theme;

import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OWNER: Member 1 - CONCEPT: the reservation result, drawn.
 *
 * <p>Stations and sections come from {@link RailwayData}. Colour follows {@link SectionState}:
 * free is grey, reserved is amber, occupied is green. The corridor bends inland instead of
 * sitting on one ruler line. Train icons sit on the station or section they last reported.
 */
public final class NetworkMapPanel extends JPanel {

    private final RailwayData data;
    private final List<StationInfo> stations;
    private final List<TrackInfo> layout;
    private List<TrackInfo> liveTracks = List.of();
    private List<TrainInfo> trains = List.of();
    private final List<TrainHit> hits = new ArrayList<>();
    private final List<TrackHit> trackHits = new ArrayList<>();
    private final Map<String, String> glideWhere = new HashMap<>();
    private final Map<String, Long> glideSince = new HashMap<>();
    private final long travelMs;
    private String hoveredTrainId;
    private String hoveredSection;
    private int pointerX = -1;
    private int pointerY = -1;

    public NetworkMapPanel(RailwayData data, long travelMs) {
        this.data = data;
        this.stations = data.stations();
        this.layout = data.sections();
        this.travelMs = Math.max(1000L, travelMs);
        setOpaque(true);
        setBackground(Theme.BG);
        Timer glide = new Timer(40, event -> {
            for (TrainInfo train : trains) {
                if (train.status() == TrainStatus.MOVING) {
                    repaint();
                    return;
                }
            }
        });
        glide.setCoalesce(true);
        glide.start();
        MouseAdapter hover = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                updateHover(event.getX(), event.getY());
            }

            @Override
            public void mouseExited(MouseEvent event) {
                updateHover(-1, -1);
            }
        };
        addMouseMotionListener(hover);
        addMouseListener(hover);
    }

    public void show(List<TrackInfo> tracks, List<TrainInfo> trains) {
        List<TrackInfo> nextTracks = tracks == null ? List.of() : List.copyOf(tracks);
        List<TrainInfo> nextTrains = trains == null ? List.of() : List.copyOf(trains);
        if (nextTracks.equals(liveTracks) && nextTrains.equals(this.trains)) {
            return;
        }
        this.liveTracks = nextTracks;
        this.trains = nextTrains;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        hits.clear();
        trackHits.clear();
        Map<String, Point2D.Float> stationAt = stationPoints();
        Map<String, Integer> lane = lanes();
        g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (TrackInfo section : layout) {
            Bend bend = bendFor(section, stationAt, lane);
            if (bend == null) {
                continue;
            }
            g.setColor(color(stateOf(section.sectionId())));
            g.draw(new QuadCurve2D.Float(bend.start.x, bend.start.y, bend.control.x, bend.control.y,
                    bend.end.x, bend.end.y));
            String direction = section.fromStationId() + "→" + section.toStationId();
            for (float t = 0.12f; t <= 0.9f; t += 0.08f) {
                Point2D.Float at = curveAt(bend, t);
                trackHits.add(new TrackHit(direction, at.x, at.y));
            }
        }
        g.setFont(Theme.FONT_SMALL_BOLD);
        for (StationInfo station : stations) {
            Point2D.Float at = stationAt.get(station.stationId());
            g.setColor(Theme.SURFACE);
            g.fillOval(Math.round(at.x) - 16, Math.round(at.y) - 16, 32, 32);
            g.setColor(Theme.PRIMARY);
            g.drawOval(Math.round(at.x) - 16, Math.round(at.y) - 16, 32, 32);
            g.setColor(Theme.TEXT);
            drawCentered(g, station.stationId(), Math.round(at.x), Math.round(at.y) + 4);
            g.setFont(Theme.FONT_SMALL);
            g.setColor(Theme.TEXT_MUTED);
            drawCentered(g, station.name(), Math.round(at.x), Math.round(at.y) + 32);
            g.setFont(Theme.FONT_SMALL_BOLD);
        }
        if (hoveredTrainId == null && hoveredSection != null && pointerX >= 0) {
            g.setFont(Theme.FONT_SMALL_BOLD);
            drawChip(g, hoveredSection, pointerX, pointerY - 16);
        }
        Map<String, Integer> seen = new HashMap<>();
        Map<String, Integer> count = new HashMap<>();
        for (TrainInfo train : trains) {
            if (train.location() != null) {
                count.merge(train.location(), 1, Integer::sum);
            }
        }
        for (TrainInfo train : trains) {
            if (train.location() == null) {
                continue;
            }
            int index = seen.merge(train.location(), 1, Integer::sum) - 1;
            Placement place = placeTrain(train, index, count.getOrDefault(train.location(), 1), stationAt, lane);
            if (place == null) {
                continue;
            }
            boolean showName = train.trainId().equals(hoveredTrainId);
            drawTrainIcon(g, train, place.x, place.y, place.angle, showName);
            String tip = train.trainId() + " " + train.name() + " · " + train.status() + " · " + train.location();
            hits.add(new TrainHit(train.trainId(), tip, place.x, place.y, 18f));
        }
        g.dispose();
    }

    private SectionState stateOf(String sectionId) {
        for (TrackInfo track : liveTracks) {
            if (track.sectionId().equals(sectionId)) {
                return track.state();
            }
        }
        return SectionState.FREE;
    }

    /**
     * Stations follow the corridor order: the first station sits toward the lower left and the
     * line climbs inland toward the upper right, the way this route leaves the coast.
     */
    private Map<String, Point2D.Float> stationPoints() {
        Map<String, Point2D.Float> points = new HashMap<>();
        int count = Math.max(stations.size(), 1);
        float left = 96f;
        float right = Math.max(getWidth() - 110f, left + 1f);
        float top = 72f;
        float bottom = Math.max(getHeight() - 52f, top + 48f);
        for (int i = 0; i < stations.size(); i++) {
            float t = count == 1 ? 0.5f : i / (float) (count - 1);
            float x = left + (float) Math.pow(t, 0.9) * (right - left);
            float y = bottom - (float) Math.sin(t * Math.PI / 2.0) * (bottom - top);
            points.put(stations.get(i).stationId(), new Point2D.Float(x, y));
        }
        return points;
    }

    /** Parallel sections between the same stations are drawn on separate lanes. */
    private Map<String, Integer> lanes() {
        Map<String, List<String>> groups = new HashMap<>();
        for (TrackInfo section : layout) {
            String key = section.fromStationId().compareTo(section.toStationId()) <= 0
                    ? section.fromStationId() + "|" + section.toStationId()
                    : section.toStationId() + "|" + section.fromStationId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(section.sectionId());
        }
        Map<String, Integer> lane = new HashMap<>();
        for (List<String> group : groups.values()) {
            for (int i = 0; i < group.size(); i++) {
                int side = group.size() == 1 ? 0 : (i % 2 == 0 ? -1 : 1);
                lane.put(group.get(i), side);
            }
        }
        return lane;
    }

    /** One block drawn as a bend. {@code nx, ny} points to the upper side of the corridor. */
    private record Bend(Point2D.Float start, Point2D.Float control, Point2D.Float end, float nx, float ny) {
    }

    private record Placement(float x, float y, double angle) {
    }

    private record TrainHit(String trainId, String tip, float x, float y, float radius) {
    }

    private record TrackHit(String direction, float x, float y) {
    }

    private static Bend bend(Point2D.Float from, Point2D.Float to, int lane) {
        Point2D.Float west = from.x <= to.x ? from : to;
        Point2D.Float east = from.x <= to.x ? to : from;
        float dx = east.x - west.x;
        float dy = east.y - west.y;
        float length = (float) Math.hypot(dx, dy);
        float nx = 0f;
        float ny = -1f;
        if (length > 0.1f) {
            nx = -dy / length;
            ny = dx / length;
            if (ny > 0f) {
                nx = -nx;
                ny = -ny;
            }
        }
        float rail = lane * 14f;
        float bow = 26f;
        Point2D.Float start = new Point2D.Float(from.x + nx * rail, from.y + ny * rail);
        Point2D.Float end = new Point2D.Float(to.x + nx * rail, to.y + ny * rail);
        Point2D.Float control = new Point2D.Float((from.x + to.x) / 2f + nx * (rail + bow),
                (from.y + to.y) / 2f + ny * (rail + bow));
        return new Bend(start, control, end, nx, ny);
    }

    /** Point at fraction {@code t} along the bend. {@code 0} is the start station, {@code 1} the end. */
    private static Point2D.Float curveAt(Bend bend, float t) {
        float u = 1f - t;
        return new Point2D.Float(
                u * u * bend.start.x + 2f * u * t * bend.control.x + t * t * bend.end.x,
                u * u * bend.start.y + 2f * u * t * bend.control.y + t * t * bend.end.y);
    }

    private Bend bendFor(TrackInfo section, Map<String, Point2D.Float> stationAt, Map<String, Integer> lane) {
        Point2D.Float from = stationAt.get(section.fromStationId());
        Point2D.Float to = stationAt.get(section.toStationId());
        if (from == null || to == null) {
            return null;
        }
        return bend(from, to, lane.getOrDefault(section.sectionId(), 0));
    }

    /**
     * Sits the train on the block it last reported. Several trains at one place are spread
     * along that block so their names do not cover each other.
     */
    private Placement placeTrain(TrainInfo train, int index, int total,
                                 Map<String, Point2D.Float> stationAt, Map<String, Integer> lane) {
        if (train.status() == TrainStatus.ARRIVED) {
            Point2D.Float station = stationAt.get(train.location());
            if (station != null) {
                float y = station.y - 30f - index * 18f;
                return new Placement(station.x, y, 0);
            }
        }
        TrackInfo section = sectionFor(train);
        if (section == null) {
            return null;
        }
        Bend bend = bendFor(section, stationAt, lane);
        if (bend == null) {
            return null;
        }
        boolean atStation = stationAt.containsKey(train.location());
        boolean leaving = section.fromStationId().equals(train.location());
        float t = along(train, atStation, leaving, index);
        Point2D.Float at = curveAt(bend, t);
        Point2D.Float ahead = curveAt(bend, Math.min(0.98f, t + 0.05f));
        double angle = Math.atan2(ahead.y - at.y, ahead.x - at.x);
        return new Placement(at.x, at.y, angle);
    }

    /** Fraction along the block. A moving train glides from the station it left toward the next one. */
    private float along(TrainInfo train, boolean atStation, boolean leaving, int index) {
        String where = train.location();
        if (!where.equals(glideWhere.get(train.trainId()))) {
            glideWhere.put(train.trainId(), where);
            glideSince.put(train.trainId(), System.currentTimeMillis());
        }
        if (atStation) {
            float step = leaving ? 0.1f : -0.1f;
            return clamp(leaving ? 0.16f + index * step : 0.84f + index * step);
        }
        if (train.status() == TrainStatus.ARRIVED) {
            return 0.9f;
        }
        if (train.status() != TrainStatus.MOVING) {
            return clamp(0.4f + index * 0.08f);
        }
        long started = glideSince.getOrDefault(train.trainId(), System.currentTimeMillis());
        float portion = Math.min(1f, (System.currentTimeMillis() - started) / (float) travelMs);
        return clamp(0.12f + 0.76f * portion + index * 0.05f);
    }

    private static float clamp(float t) {
        return Math.max(0.1f, Math.min(0.9f, t));
    }

    private void updateHover(int x, int y) {
        String id = null;
        String tip = null;
        for (int i = hits.size() - 1; i >= 0; i--) {
            TrainHit hit = hits.get(i);
            float dx = x - hit.x;
            float dy = y - hit.y;
            if (dx * dx + dy * dy <= hit.radius * hit.radius) {
                id = hit.trainId;
                tip = hit.tip;
                break;
            }
        }
        String section = null;
        if (id == null) {
            float best = 16f * 16f;
            for (TrackHit hit : trackHits) {
                float dx = x - hit.x;
                float dy = y - hit.y;
                float distance = dx * dx + dy * dy;
                if (distance <= best) {
                    best = distance;
                    section = hit.direction;
                }
            }
            tip = section;
        }
        if (Objects.equals(id, hoveredTrainId) && Objects.equals(section, hoveredSection)
                && x == pointerX && y == pointerY) {
            return;
        }
        hoveredTrainId = id;
        hoveredSection = section;
        pointerX = x;
        pointerY = y;
        setToolTipText(tip);
        repaint();
    }

    /** The block the train is on, or the block it is about to enter when it is still at a station. */
    private TrackInfo sectionFor(TrainInfo train) {
        for (TrackInfo section : layout) {
            if (section.sectionId().equals(train.location())) {
                return section;
            }
        }
        var route = data.route(train.routeId());
        if (route.isPresent()) {
            for (String sectionId : route.get().sectionIds()) {
                for (TrackInfo section : layout) {
                    if (!section.sectionId().equals(sectionId)) {
                        continue;
                    }
                    if (section.fromStationId().equals(train.location())
                            || section.toStationId().equals(train.location())) {
                        return section;
                    }
                }
            }
        }
        for (TrackInfo section : layout) {
            if (section.fromStationId().equals(train.location()) || section.toStationId().equals(train.location())) {
                return section;
            }
        }
        return null;
    }

    /**
     * Top-down carriage centered on the rail and turned along it. The train id is drawn only
     * while the pointer is over this carriage.
     */
    private void drawTrainIcon(Graphics2D g, TrainInfo train, float x, float y, double angle, boolean showName) {
        AffineTransform saved = g.getTransform();
        g.translate(x, y);
        g.rotate(angle);
        g.setColor(Theme.WARN);
        g.fillRoundRect(-16, -7, 32, 14, 12, 12);
        g.setColor(Theme.TEXT);
        g.fillOval(-15, -6, 10, 12);
        g.fillOval(5, -6, 10, 12);
        g.setColor(Theme.SURFACE);
        g.fillRoundRect(-5, -2, 10, 4, 3, 3);
        g.setColor(trainColor(train.status()));
        g.fillRoundRect(-1, -5, 3, 10, 2, 2);
        g.setColor(Theme.INACTIVE);
        g.fillRect(-8, -6, 3, 3);
        g.fillRect(-8, 3, 3, 3);
        g.fillRect(2, -6, 3, 3);
        g.fillRect(2, 3, 3, 3);
        g.setTransform(saved);
        if (!showName) {
            return;
        }
        g.setFont(Theme.FONT_SMALL_BOLD);
        FontMetrics metrics = g.getFontMetrics();
        String text = train.trainId();
        int labelW = metrics.stringWidth(text) + 8;
        int labelH = metrics.getHeight();
        int labelX = Math.max(4, Math.min(getWidth() - labelW - 4, Math.round(x) - labelW / 2));
        int labelTop = Math.max(2, Math.round(y) - 7 - labelH - 4);
        g.setColor(Theme.SURFACE);
        g.fillRoundRect(labelX, labelTop, labelW, labelH, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
        g.setColor(Theme.BORDER);
        g.drawRoundRect(labelX, labelTop, labelW, labelH, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
        g.setColor(Theme.TEXT);
        g.drawString(text, labelX + 4, labelTop + metrics.getAscent());
    }

    private static java.awt.Color trainColor(TrainStatus status) {
        if (status == null) {
            return Theme.INACTIVE;
        }
        return switch (status) {
            case MOVING, REGISTERED -> Theme.PRIMARY;
            case HELD, WAITING -> Theme.WARN;
            case EMERGENCY_STOPPED -> Theme.DANGER;
            case ARRIVED -> Theme.OK;
            case INACTIVE -> Theme.INACTIVE;
        };
    }

    /** Section id centered on its own stretch, so the return track is labelled on that line. */
    private void drawChip(Graphics2D g, String text, int x, int y) {
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(text) + 10;
        int height = metrics.getHeight();
        int left = Math.max(4, Math.min(getWidth() - width - 4, x - width / 2));
        int top = Math.max(2, Math.min(getHeight() - height - 2, y - height / 2));
        g.setColor(Theme.SURFACE);
        g.fillRoundRect(left, top, width, height, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
        g.setColor(Theme.BORDER);
        g.drawRoundRect(left, top, width, height, Theme.CORNER_RADIUS, Theme.CORNER_RADIUS);
        g.setColor(Theme.TEXT);
        g.drawString(text, left + 5, top + metrics.getAscent());
    }

    private static java.awt.Color color(SectionState state) {
        return switch (state) {
            case RESERVED -> Theme.WARN;
            case OCCUPIED -> Theme.OK;
            case FREE -> Theme.INACTIVE;
        };
    }

    private static void drawCentered(Graphics2D g, String text, int x, int y) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, x - metrics.stringWidth(text) / 2, y);
    }
}
