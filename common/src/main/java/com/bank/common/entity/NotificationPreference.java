package com.bank.common.entity;

import jakarta.persistence.*;
import java.time.LocalTime;

@Entity
@Table(name = "notification_preferences",
       uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "event_type"}))
public class NotificationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT")
    private String channels; // JSON array, e.g. ["SMS","EMAIL"]

    @Column(name = "quiet_start")
    private LocalTime quietStart;

    @Column(name = "quiet_end")
    private LocalTime quietEnd;

    private boolean enabled = true;

    public NotificationPreference() {}

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCustomerId() { return customerId; }
    public void setCustomerId(Long customerId) { this.customerId = customerId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getChannels() { return channels; }
    public void setChannels(String channels) { this.channels = channels; }
    public LocalTime getQuietStart() { return quietStart; }
    public void setQuietStart(LocalTime quietStart) { this.quietStart = quietStart; }
    public LocalTime getQuietEnd() { return quietEnd; }
    public void setQuietEnd(LocalTime quietEnd) { this.quietEnd = quietEnd; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}