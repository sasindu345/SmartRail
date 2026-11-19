package com.smartrail.ui.shell;

import com.smartrail.common.service.NetworkService;
import com.smartrail.common.service.ServerContext;
import com.smartrail.ui.panels.NioServerPanel;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.WindowConstants;
import java.util.List;

/**
 * OWNER: Member 2 (application shell). Central server window: hosts Member 1's NioServerPanel and a
 * "Services & ports" table (evidence for IP addressing / ports).
 * <ul>
 *   <li>TODO(M2) add server IP addresses (ServerAddressResolver.localInterfaceAddresses) to the ports tab</li>
 *   <li>TODO(M2) show which services failed to start (NOT AVAILABLE) in the table</li>
 * </ul>
 */
public final class ServerWindow extends JFrame {

    public ServerWindow(ServerContext ctx, List<NetworkService> services) {
        super("SmartRail - Central Railway Control Server (simulation)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        String[][] rows = services.stream().map(s -> new String[]{s.name(), s.endpoint()}).toArray(String[][]::new);
        JTable portTable = new JTable(rows, new String[]{"Service", "Endpoint"});
        portTable.setEnabled(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Trains & tracks (NIO)", new NioServerPanel(ctx));
        tabs.addTab("Services & ports", new JScrollPane(portTable));
        add(tabs);
        setSize(900, 600);
        setLocationByPlatform(true);
    }
}
