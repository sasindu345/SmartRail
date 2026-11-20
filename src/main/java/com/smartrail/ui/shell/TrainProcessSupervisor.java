package com.smartrail.ui.shell;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Supervises child train client JVM processes.
 * Owner: Member 3 (control-center-spec.md §2-§3).
 *
 * <p>Spawns each train as an independent JVM process using {@link ProcessBuilder} with
 * {@code -Dui.train.window.enabled=false}, pipes each child's stdout/stderr to listeners,
 * and ensures all child processes are killed cleanly on exit.
 *
 * <p><strong>Rule:</strong> This is the ONLY class in the entire project permitted to use
 * {@link ProcessBuilder} or {@link Runtime#exec}.
 */
public final class TrainProcessSupervisor {

    public interface TrainOutputListener {
        void onOutput(String trainId, String line);
    }

    public interface TrainLifecycleListener {
        void onStarted(String trainId);
        void onStopped(String trainId, int exitCode);
    }

    private final Map<String, Process> processes = new ConcurrentHashMap<>();
    private final List<TrainOutputListener> outputListeners = new CopyOnWriteArrayList<>();
    private final List<TrainLifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    public TrainProcessSupervisor() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stopAllTrains, "train-process-cleaner"));
    }

    public void addOutputListener(TrainOutputListener listener) {
        if (listener != null) {
            outputListeners.add(listener);
        }
    }

    public void addLifecycleListener(TrainLifecycleListener listener) {
        if (listener != null) {
            lifecycleListeners.add(listener);
        }
    }

    /**
     * Start a train child JVM process.
     *
     * @param trainId ID of the train (e.g. "TR01", "TR02", "TR03")
     * @param tls     whether to use TLS transport (-Dclient.transport=TLS)
     * @return the started {@link Process}
     * @throws IOException if the process could not be started
     */
    public synchronized Process startTrain(String trainId, boolean tls) throws IOException {
        Process existing = processes.get(trainId);
        if (existing != null && existing.isAlive()) {
            notifyOutput(trainId, "[SUPERVISOR] Train " + trainId + " is already running.");
            return existing;
        }

        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classPath = System.getProperty("java.class.path");

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classPath);
        command.add("-Dui.train.window.enabled=false");
        if (tls) {
            command.add("-Dclient.transport=TLS");
        }
        command.add("com.smartrail.app.TrainClientApp");
        command.add(trainId);

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(System.getProperty("user.dir", ".")));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        processes.put(trainId, process);

        notifyStarted(trainId);
        notifyOutput(trainId, "[SUPERVISOR] Launched train " + trainId + (tls ? " (TLS)" : " (PLAIN)")
                + " [PID: " + process.pid() + "]");

        Thread stdoutReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    notifyOutput(trainId, line);
                }
            } catch (IOException ignored) {
            }

            int exitCode = -1;
            try {
                exitCode = process.waitFor();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            processes.remove(trainId);
            notifyOutput(trainId, "[SUPERVISOR] Train " + trainId + " process exited (code " + exitCode + ").");
            notifyStopped(trainId, exitCode);
        }, "train-reader-" + trainId);

        stdoutReader.setDaemon(true);
        stdoutReader.start();

        return process;
    }

    /**
     * Stop a specific running train.
     */
    public synchronized void stopTrain(String trainId) {
        Process p = processes.remove(trainId);
        if (p != null && p.isAlive()) {
            notifyOutput(trainId, "[SUPERVISOR] Stopping train " + trainId + "...");
            p.destroy();
            try {
                if (!p.waitFor(1, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException ignored) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Stop all running train processes. Leaves no orphan processes (Checkpoint C10).
     */
    public synchronized void stopAllTrains() {
        for (String id : new ArrayList<>(processes.keySet())) {
            stopTrain(id);
        }
    }

    public boolean isTrainRunning(String trainId) {
        Process p = processes.get(trainId);
        return p != null && p.isAlive();
    }

    public List<String> runningTrainIds() {
        List<String> list = new ArrayList<>();
        for (Map.Entry<String, Process> entry : processes.entrySet()) {
            if (entry.getValue().isAlive()) {
                list.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(list);
    }

    private void notifyOutput(String trainId, String line) {
        for (TrainOutputListener l : outputListeners) {
            try {
                l.onOutput(trainId, line);
            } catch (Throwable ignored) {
            }
        }
    }

    private void notifyStarted(String trainId) {
        for (TrainLifecycleListener l : lifecycleListeners) {
            try {
                l.onStarted(trainId);
            } catch (Throwable ignored) {
            }
        }
    }

    private void notifyStopped(String trainId, int exitCode) {
        for (TrainLifecycleListener l : lifecycleListeners) {
            try {
                l.onStopped(trainId, exitCode);
            } catch (Throwable ignored) {
            }
        }
    }
}
