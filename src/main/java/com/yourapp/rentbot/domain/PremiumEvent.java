package com.yourapp.rentbot.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "premium_events", indexes = {
        @Index(name = "idx_premium_event_type_time_user", columnList = "type, created_at, telegram_user_id")
})
public class PremiumEvent {

    public enum Type { OPENED, PAYMENT_METHOD_SELECTED, REQUEST_SUBMITTED, APPROVED, REJECTED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32)
    private Type type;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; }
    public void setType(Type type) { this.type = type; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
