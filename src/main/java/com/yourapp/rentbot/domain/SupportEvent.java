package com.yourapp.rentbot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "support_events")
public class SupportEvent {

    public enum Type { OPENED, RAIFFEISEN, PRIVATBANK, PAYPAL, REVOLUT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; }
    public void setType(Type type) { this.type = type; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
