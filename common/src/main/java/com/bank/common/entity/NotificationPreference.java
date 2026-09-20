package com.bank.common.entity;

import com.bank.common.converter.StringListConverter;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalTime;
import java.util.List;

@Data
@Entity
@Table(name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(columnNames = {"customer_id", "event_type"}))
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    @Convert(converter = StringListConverter.class)
    private List<String> channels;

    @Column(name = "quiet_start")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime quietStart;

    @Column(name = "quiet_end")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime quietEnd;

    private boolean enabled = true;
}