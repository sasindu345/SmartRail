package com.smartrail.common.log;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe in-memory event log (bounded). Every module logs network activity here so the
 * server window, RMI {@code getEventLog()} and the demo can show it. SHARED CONTRACT (frozen).
 *
 * <p>Listeners are called on the logging thread - Swing code must hop to the EDT
 * (see {@code ui.shell.UiThreads}).
 */
public final class EventLog {

    /** One log line. {@code source} is the module, e.g. "NIO", "UDP", "TLS", "RMI". */
    public record Entry(long timeMillis, String source, String message) implements Serializable {
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

        public String format() {
            LocalTime t = LocalTime.ofInstant(Instant.ofEpochMilli(timeMillis), ZoneId.systemDefault());
            return TIME.format(t) + " [" + source + "] " + message;
        }
    }

    private final int capacity;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final List<Consumer<Entry>> listeners = new CopyOnWriteArrayList<>();

    /** @param capacity maximum entries kept (from {@code log.capacity}) */
    public EventLog(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public void log(String source, String message) {
        Entry entry = new Entry(System.currentTimeMillis(), source, message);
        synchronized (entries) {
            if (entries.size() == capacity) {
                entries.removeFirst();
            }
            entries.addLast(entry);
        }
        System.out.println(entry.format());
        for (Consumer<Entry> listener : listeners) {
            listener.accept(entry);
        }
    }

    public List<Entry> snapshot() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }

    public void addListener(Consumer<Entry> listener) {
        listeners.add(listener);
    }
}
