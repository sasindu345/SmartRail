package com.smartrail.common.service;

/**
 * Publishes emergency alerts to all subscribed train clients. Implemented by Member 3
 * ({@code MulticastAlertPublisher}); called by Member 1's {@code RailwayServiceImpl}.
 * SHARED CONTRACT (frozen).
 */
public interface AlertService {

    /** Sends {@code EMERGENCY_ALERT|sectionId|reason} to the multicast group (and unicast fallback if enabled). */
    void publishAlert(String sectionId, String reason);
}
