package com.smartrail.app;

import com.smartrail.common.config.NetworkConfig;
import com.smartrail.ui.shell.ControlCenterWindow;
import com.smartrail.ui.shell.SimulationController;
import com.smartrail.ui.shell.TrainProcessSupervisor;
import com.smartrail.ui.theme.Theme;

import javax.swing.SwingUtilities;

/**
 * SmartRail Control Center launcher.
 * Owner: Member 3 (control-center-spec.md §2, §6).
 *
 * <p>Installs the FlatLaf theme, wires the in-process server services and train process supervisor,
 * and displays the single demo window.
 *
 * <p>Run: {@code scripts/run.sh controlcenter}
 */
public final class ControlCenterApp {

    private ControlCenterApp() {
    }

    public static void main(String[] args) {
        // 1. Install FlatLaf look-and-feel (degrades gracefully if it fails)
        Theme.install();

        // 2. Load configuration and initialize in-process controller & supervisor
        NetworkConfig config = NetworkConfig.load();
        SimulationController controller = new SimulationController(config);
        TrainProcessSupervisor supervisor = new TrainProcessSupervisor();

        // 3. Open the single Control Center window on the Swing EDT
        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            SwingUtilities.invokeLater(() -> {
                ControlCenterWindow window = new ControlCenterWindow(controller, supervisor);
                window.setVisible(true);
            });
        }
    }
}
