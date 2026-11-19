package com.smartrail.common.config;

import com.smartrail.common.model.RouteInfo;
import com.smartrail.common.model.ScheduleInfo;
import com.smartrail.common.model.SectionState;
import com.smartrail.common.model.StationInfo;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.common.model.TrainDefinition;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Static railway model (stations, sections, routes, trains, timetable) loaded from
 * {@code config/railway-data.properties}. Immutable; runtime state lives in Member 1's services.
 *
 * <p>SHARED CONTRACT (frozen). Never hard-code train/station/section IDs in Java code.
 */
public final class RailwayData {

    private final List<StationInfo> stations;
    private final List<TrackInfo> sections;
    private final List<RouteInfo> routes;
    private final List<TrainDefinition> trains;
    private final List<ScheduleInfo> schedules;

    private RailwayData(List<StationInfo> stations, List<TrackInfo> sections, List<RouteInfo> routes,
                        List<TrainDefinition> trains, List<ScheduleInfo> schedules) {
        this.stations = List.copyOf(stations);
        this.sections = List.copyOf(sections);
        this.routes = List.copyOf(routes);
        this.trains = List.copyOf(trains);
        this.schedules = List.copyOf(schedules);
    }

    /** Loads the file named by {@link ConfigKeys#RAILWAY_DATA_PATH}. */
    public static RailwayData load(NetworkConfig config) {
        return load(config.getPath(ConfigKeys.RAILWAY_DATA_PATH));
    }

    public static RailwayData load(Path path) {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            p.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read railway data " + path.toAbsolutePath(), e);
        }
        return parse(p);
    }

    static RailwayData parse(Properties p) {
        List<StationInfo> stations = new ArrayList<>();
        for (String id : list(p, "stations")) {
            stations.add(new StationInfo(id, required(p, "station." + id + ".name")));
        }
        List<TrackInfo> sections = new ArrayList<>();
        for (String id : list(p, "sections")) {
            sections.add(new TrackInfo(id, required(p, "section." + id + ".from"),
                    required(p, "section." + id + ".to"), SectionState.FREE, null));
        }
        List<RouteInfo> routes = new ArrayList<>();
        for (String id : list(p, "routes")) {
            routes.add(new RouteInfo(id, list(p, "route." + id + ".sections")));
        }
        List<TrainDefinition> trains = new ArrayList<>();
        List<ScheduleInfo> schedules = new ArrayList<>();
        for (String id : list(p, "trains")) {
            trains.add(new TrainDefinition(id, required(p, "train." + id + ".name"),
                    required(p, "train." + id + ".start"), required(p, "train." + id + ".route")));
            String timetable = p.getProperty("schedule." + id, "");
            for (String stop : split(timetable)) {
                String[] parts = stop.split("@");
                if (parts.length != 2) {
                    throw new IllegalStateException("Bad schedule entry '" + stop + "' for " + id + " (expected STATION@HH:mm)");
                }
                schedules.add(new ScheduleInfo(id, parts[0].trim(), parts[1].trim()));
            }
        }
        return new RailwayData(stations, sections, routes, trains, schedules);
    }

    public List<StationInfo> stations() {
        return stations;
    }

    /** Sections in their initial (FREE) state. */
    public List<TrackInfo> sections() {
        return sections;
    }

    public List<RouteInfo> routes() {
        return routes;
    }

    public List<TrainDefinition> trains() {
        return trains;
    }

    public List<ScheduleInfo> schedules() {
        return schedules;
    }

    public Optional<TrainDefinition> train(String trainId) {
        return trains.stream().filter(t -> t.trainId().equals(trainId)).findFirst();
    }

    public Optional<RouteInfo> route(String routeId) {
        return routes.stream().filter(r -> r.routeId().equals(routeId)).findFirst();
    }

    public Optional<TrackInfo> section(String sectionId) {
        return sections.stream().filter(s -> s.sectionId().equals(sectionId)).findFirst();
    }

    public Optional<StationInfo> station(String stationId) {
        return stations.stream().filter(s -> s.stationId().equals(stationId)).findFirst();
    }

    private static String required(Properties p, String key) {
        String value = p.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing railway data key: " + key);
        }
        return value.trim();
    }

    private static List<String> list(Properties p, String key) {
        return split(required(p, key));
    }

    private static List<String> split(String csv) {
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
