package com.smartrail.ui.panels;

import com.smartrail.common.config.ConfigKeys;
import com.smartrail.common.config.NetworkConfig;
import com.smartrail.common.log.EventLog;
import com.smartrail.common.model.RouteInfo;
import com.smartrail.common.model.TrainInfo;
import com.smartrail.common.protocol.Message;
import com.smartrail.multicast.MulticastAlertSubscriber;
import com.smartrail.ui.components.Card;
import com.smartrail.ui.shell.SimulationController;
import com.smartrail.ui.shell.UiThreads;
import com.smartrail.ui.theme.Theme;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * OWNER: Member 3 — the Alerts screen.
 *
 * <p>Subscribe joins the multicast group and lists each {@code EMERGENCY_ALERT} that arrives.
 * Trigger obstruction asks the server to publish one alert for the section the chosen train
 * is on. The path line says whether that send used multicast, the unicast fallback, or both.
 */
public final class AlertsPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final String ALERT_REASON = "track blocked";

    private final SimulationController controller;
    private final NetworkConfig config;
    private final JButton subscribeButton = new JButton("Subscribe");
    private final JButton triggerButton = new JButton("Trigger obstruction");
    private final JComboBox<String> trainCombo = new JComboBox<>();
    private final JLabel sectionLabel = new JLabel("Section —");
    private final JLabel pathLabel = new JLabel(" ");
    private final DefaultListModel<String> alerts = new DefaultListModel<>();

    private volatile MulticastAlertSubscriber subscriber;

    public AlertsPanel(SimulationController controller) {
        super(new BorderLayout(Theme.GAP, Theme.GAP));
        this.controller = controller;
        this.config = controller.config();
        setOpaque(false);

        for (var train : controller.railwayData().trains()) {
            trainCombo.addItem(train.trainId());
        }
        trainCombo.setFont(Theme.FONT_BODY);
        subscribeButton.setFont(Theme.FONT_BODY);
        triggerButton.setFont(Theme.FONT_BODY);
        sectionLabel.setFont(Theme.FONT_BODY);
        sectionLabel.setForeground(Theme.TEXT);
        pathLabel.setFont(Theme.FONT_BODY);
        pathLabel.setForeground(Theme.TEXT);
        pathLabel.setText(configuredPath());

        subscribeButton.addActionListener(e -> toggleSubscription());
        triggerButton.addActionListener(e -> triggerObstruction());
        trainCombo.addActionListener(e -> refreshSection());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, Theme.GAP, 0));
        controls.setOpaque(false);
        controls.add(subscribeButton);
        controls.add(trainCombo);
        controls.add(sectionLabel);
        controls.add(triggerButton);

        JPanel header = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        header.setOpaque(false);
        header.add(controls, BorderLayout.NORTH);
        header.add(pathLabel, BorderLayout.CENTER);

        JList<String> list = new JList<>(alerts);
        list.setFont(Theme.FONT_MONO);
        list.setBackground(Theme.SURFACE);
        list.setForeground(Theme.TEXT);

        Card card = new Card("Alerts");
        JPanel body = new JPanel(new BorderLayout(Theme.GAP, Theme.GAP));
        body.setOpaque(false);
        body.add(header, BorderLayout.NORTH);
        body.add(new JScrollPane(list), BorderLayout.CENTER);
        card.setBody(body);
        add(card, BorderLayout.CENTER);

        Timer timer = new Timer(400, e -> refreshSection());
        timer.setInitialDelay(0);
        timer.start();
    }

    private void toggleSubscription() {
        if (subscriber == null) {
            MulticastAlertSubscriber created = new MulticastAlertSubscriber(config, this::onAlert);
            subscriber = created;
            subscribeButton.setText("Unsubscribe");
            subscribeButton.setEnabled(false);
            UiThreads.runInBackground(() -> {
                try {
                    created.start();
                    if (subscriber != created) {
                        created.stop();
                        return;
                    }
                    UiThreads.onEdt(() -> {
                        subscribeButton.setEnabled(true);
                        pathLabel.setForeground(Theme.TEXT);
                        pathLabel.setText("Subscribed. " + configuredPath());
                    });
                } catch (RuntimeException e) {
                    if (subscriber == created) {
                        subscriber = null;
                    }
                    String detail = e.getCause() == null || e.getCause().getMessage() == null
                            ? e.getMessage() : e.getCause().getMessage();
                    UiThreads.onEdt(() -> {
                        subscribeButton.setText("Subscribe");
                        subscribeButton.setEnabled(true);
                        pathLabel.setForeground(Theme.DANGER);
                        pathLabel.setText("Multicast join failed: " + detail + ". " + fallbackClause());
                    });
                }
            });
        } else {
            stopSubscription();
        }
    }

    /**
     * Leaves the multicast group if this screen subscribed. Safe to call more than once.
     */
    public void stopSubscription() {
        MulticastAlertSubscriber current = subscriber;
        subscriber = null;
        UiThreads.onEdt(() -> {
            subscribeButton.setText("Subscribe");
            subscribeButton.setEnabled(true);
            pathLabel.setForeground(Theme.TEXT);
            pathLabel.setText("Not subscribed. " + configuredPath());
        });
        if (current != null) {
            UiThreads.runInBackground(current::stop);
        }
    }

    private void onAlert(Message alert) {
        String line = LocalTime.now().format(TIME_FORMAT) + "  " + direction(alert.field(0))
                + "  " + alert.field(1);
        UiThreads.onEdt(() -> alerts.addElement(line));
    }

    private void triggerObstruction() {
        if (!controller.isServerRunning() || controller.serverContext() == null) {
            pathLabel.setForeground(Theme.DANGER);
            pathLabel.setText("Start the server before sending an alert.");
            return;
        }
        Object selected = trainCombo.getSelectedItem();
        if (selected == null) {
            return;
        }
        String trainId = selected.toString();
        String sectionId = sectionFor(trainId);
        if (sectionId == null) {
            pathLabel.setForeground(Theme.DANGER);
            pathLabel.setText("No section for " + trainId + ".");
            return;
        }
        triggerButton.setEnabled(false);
        UiThreads.runInBackground(() -> {
            controller.serverContext().railwayService().reportObstruction(sectionId, ALERT_REASON);
            String path = pathAfterSend();
            UiThreads.onEdt(() -> {
                triggerButton.setEnabled(true);
                pathLabel.setForeground(Theme.TEXT);
                pathLabel.setText(path);
            });
        });
    }

    private void refreshSection() {
        Object selected = trainCombo.getSelectedItem();
        if (selected == null) {
            sectionLabel.setText("Section —");
            return;
        }
        String sectionId = sectionFor(selected.toString());
        sectionLabel.setText(sectionId == null ? "Section —" : "Section " + direction(sectionId));
    }

    private String sectionFor(String trainId) {
        if (!controller.isServerRunning() || controller.serverContext() == null) {
            return null;
        }
        TrainInfo train = controller.serverContext().railwayService().getTrain(trainId).orElse(null);
        if (train == null) {
            return null;
        }
        if (controller.railwayData().section(train.location()).isPresent()) {
            return train.location();
        }
        for (RouteInfo route : controller.railwayData().routes()) {
            if (!route.routeId().equals(train.routeId())) {
                continue;
            }
            for (String sectionId : route.sectionIds()) {
                var section = controller.railwayData().section(sectionId);
                if (section.isPresent() && section.get().fromStationId().equals(train.location())) {
                    return sectionId;
                }
            }
            return route.sectionIds().isEmpty() ? null : route.sectionIds().get(0);
        }
        return null;
    }

    private String direction(String sectionId) {
        return controller.railwayData().section(sectionId)
                .map(section -> section.fromStationId() + "→" + section.toStationId())
                .orElse(sectionId);
    }

    private String configuredPath() {
        String group = config.get(ConfigKeys.MULTICAST_GROUP);
        int port = config.getInt(ConfigKeys.MULTICAST_PORT);
        return "Multicast " + group + ":" + port + ". " + fallbackClause();
    }

    private String fallbackClause() {
        boolean fallback = config.getBoolean(ConfigKeys.MULTICAST_UNICAST_FALLBACK_ENABLED);
        return fallback ? "Unicast fallback is enabled." : "Unicast fallback is off.";
    }

    /** Reads the publisher's own log lines from the send that just happened. */
    private String pathAfterSend() {
        boolean published = false;
        boolean failed = false;
        boolean fallback = false;
        List<EventLog.Entry> entries = controller.eventLog().snapshot();
        for (int i = entries.size() - 1; i >= 0; i--) {
            EventLog.Entry entry = entries.get(i);
            if (!"MULTICAST".equals(entry.source())) {
                continue;
            }
            String message = entry.message();
            if (message.startsWith("Unicast fallback alert sent")) {
                fallback = true;
                continue;
            }
            if (message.startsWith("Published")) {
                published = true;
                break;
            }
            if (message.startsWith("Failed to publish") || message.startsWith("Cannot publish")) {
                failed = true;
                break;
            }
        }
        String where = config.get(ConfigKeys.MULTICAST_GROUP) + ":" + config.getInt(ConfigKeys.MULTICAST_PORT);
        if (published && fallback) {
            return "Sent on multicast " + where + ", and by unicast to each connected train.";
        }
        if (published) {
            return "Sent on multicast " + where + ".";
        }
        if (failed && fallback) {
            return "Multicast publish failed. Sent by unicast fallback.";
        }
        if (failed) {
            return "Multicast publish failed. Unicast fallback is off.";
        }
        return configuredPath();
    }
}
