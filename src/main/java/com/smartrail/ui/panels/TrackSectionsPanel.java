package com.smartrail.ui.panels;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.model.TrackInfo;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.components.TableStyles;
import com.smartrail.ui.shell.SimulationController;
import com.smartrail.ui.shell.UiThreads;
import com.smartrail.ui.theme.Theme;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OWNER: Member 1 - track section table and the conflict demo.
 *
 * <p>{@code Run conflict demo} opens two real TCP clients, TR01 and TR03, and asks both for
 * {@code SEC_A}. Their routes both contain that section, so one reply is {@code SECTION_DENIED}.
 */
public final class TrackSectionsPanel extends JPanel {

    private final SimulationController controller;
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Direction", "From", "To", "State", "Holder"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final JLabel notice = new JLabel(" ");
    private final JButton conflict = new JButton("Run conflict demo");
    private volatile String deniedSection = "";
    private Socket grantedSocket;

    public TrackSectionsPanel(SimulationController controller) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.controller = controller;
        setOpaque(false);
        notice.setFont(Theme.FONT_BODY);
        notice.setForeground(Theme.TEXT_MUTED);
        conflict.setFont(Theme.FONT_BODY_BOLD);
        conflict.addActionListener(e -> UiThreads.runInBackground(this::runConflict));

        TableStyles.apply(table);
        table.getColumnModel().getColumn(3).setCellRenderer(TableStyles.statusDotRenderer());
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable source, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                Component component = super.getTableCellRendererComponent(source, value, isSelected, hasFocus, row, column);
                component.setForeground(!isSelected && value != null && value.toString().equals(deniedSection)
                        ? Theme.DANGER : Theme.TEXT);
                return component;
            }
        });

        JPanel actions = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        actions.setOpaque(false);
        actions.add(conflict, BorderLayout.WEST);
        actions.add(notice, BorderLayout.CENTER);

        Card card = new Card("Track sections");
        JPanel body = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        body.setOpaque(false);
        body.add(new JScrollPane(table), BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);
        card.setBody(body);
        add(card, BorderLayout.CENTER);

        Timer timer = new Timer(1000, e -> refresh());
        timer.setInitialDelay(0);
        timer.start();
    }

    private void refresh() {
        if (!controller.isServerRunning() || controller.serverContext() == null) {
            if (model.getRowCount() > 0) {
                model.setRowCount(0);
            }
            return;
        }
        List<TrackInfo> sections = controller.serverContext().railwayService().getTracks();
        if (model.getRowCount() != sections.size()) {
            model.setRowCount(0);
            for (TrackInfo section : sections) {
                model.addRow(row(section));
            }
            return;
        }
        for (int i = 0; i < sections.size(); i++) {
            Object[] next = row(sections.get(i));
            for (int column = 0; column < next.length; column++) {
                if (!next[column].equals(model.getValueAt(i, column))) {
                    model.setValueAt(next[column], i, column);
                }
            }
        }
    }

    private static Object[] row(TrackInfo section) {
        return new Object[]{
                section.fromStationId() + "→" + section.toStationId(),
                section.fromStationId(),
                section.toStationId(),
                section.state().name(),
                section.holderTrainId() == null ? "" : section.holderTrainId()
        };
    }

    private void runConflict() {
        if (!controller.isServerRunning()) {
            UiThreads.onEdt(() -> notice.setText("NIO server is not started."));
            return;
        }
        closeGranted();
        NetworkConfig config = controller.config();
        String host = config.get(ConfigKeys.CLIENT_SERVER_HOST);
        int port = config.getInt(ConfigKeys.NIO_TCP_PORT);
        UiThreads.onEdt(() -> {
            conflict.setEnabled(false);
            notice.setText("Connecting TR01 and TR03 to " + host + ":" + port + " for ST01→ST02");
        });
        try {
            Socket tr01 = open(host, port);
            Socket tr03 = open(host, port);
            String reply1 = request(tr01, "TR01");
            String reply3 = request(tr03, "TR03");
            String deniedBy = reply1.contains("SECTION_DENIED") ? "TR01" : reply3.contains("SECTION_DENIED") ? "TR03" : "";
            Socket loser = reply1.contains("SECTION_DENIED") ? tr01 : tr03;
            grantedSocket = reply1.contains("SECTION_GRANTED") ? tr01 : tr03;
            loser.close();
            String text = deniedBy.isEmpty()
                    ? "Neither reply was SECTION_DENIED. " + reply1 + " | " + reply3
                    : deniedBy + " received SECTION_DENIED for ST01→ST02. The other train still holds it.";
            UiThreads.onEdt(() -> {
                deniedSection = "ST01→ST02";
                notice.setText(text);
                notice.setForeground(deniedBy.isEmpty() ? Theme.DANGER : Theme.TEXT);
            });
        } catch (Exception e) {
            UiThreads.onEdt(() -> {
                notice.setForeground(Theme.DANGER);
                notice.setText("Conflict demo failed: " + e.getMessage());
            });
        } finally {
            UiThreads.onEdt(() -> conflict.setEnabled(true));
        }
    }

    private static Socket open(String host, int port) throws Exception {
        Socket socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), 3000);
        socket.setSoTimeout(3000);
        return socket;
    }

    private static String request(Socket socket, String trainId) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        Writer writer = new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8);
        writer.write("REGISTER|" + trainId + "|ST01\n");
        writer.flush();
        reader.readLine();
        reader.readLine();
        writer.write("REQUEST_SECTION|" + trainId + "|SEC_A\n");
        writer.flush();
        String reply = reader.readLine();
        return reply == null ? "" : reply;
    }

    private void closeGranted() {
        Socket previous = grantedSocket;
        grantedSocket = null;
        if (previous != null) {
            try {
                previous.close();
            } catch (Exception ignored) {
                // The next demo opens new sockets.
            }
        }
    }
}
