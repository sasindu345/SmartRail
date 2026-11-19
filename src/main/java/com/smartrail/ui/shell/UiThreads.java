package com.smartrail.ui.shell;

import javax.swing.SwingUtilities;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SHARED helper: keep network work OFF the Swing Event Dispatch Thread and UI updates ON it.
 * Every panel uses these two methods instead of creating threads ad hoc.
 */
public final class UiThreads {

    private static final ExecutorService NETWORK = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ui-network-worker");
        t.setDaemon(true);
        return t;
    });

    private UiThreads() {
    }

    /** Run blocking network code (connect, discover, URLConnection, RMI call) in the background. */
    public static void runInBackground(Runnable task) {
        NETWORK.execute(task);
    }

    /** Update Swing components safely from any thread. */
    public static void onEdt(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
