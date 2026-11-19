package com.smartrail.app;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.contenthandler.SmartRailContentHandlerFactory;
import com.smartrail.ui.shell.ControllerDashboard;

import javax.swing.SwingUtilities;
import java.net.URLConnection;

/**
 * SHARED launcher: controller / administrator dashboard (separate process from the server).
 * Installs Member 5's content handler factory once for the JVM; the smartrail:// protocol handler is
 * found automatically through META-INF/services.
 *
 * <p>Run: {@code scripts/run.sh controller}
 */
public final class ControllerApp {

    private ControllerApp() {
    }

    public static void main(String[] args) {
        NetworkConfig config = NetworkConfig.load();
        URLConnection.setContentHandlerFactory(new SmartRailContentHandlerFactory());     // Member 5
        SwingUtilities.invokeLater(() -> new ControllerDashboard(config).setVisible(true));
    }
}
