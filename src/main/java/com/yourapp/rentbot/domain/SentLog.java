package com.yourapp.rentbot.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(
        name = "sent_log",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_sent_user_listing",
                columnNames = {"telegram_user_id", "listing_key"}
        )
)
public class SentLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="telegram_user_id", nullable = false)
    private Long telegramUserId;

    // уникальный ключ объявления (пока строка; позже будет URL/ID)
    @Column(name="listing_key", nullable = false, length = 512)
    private String listingKey;

    @Column(name="sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    public Long getId() { return id; }

    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; }

    public String getListingKey() { return listingKey; }
    public void setListingKey(String listingKey) { this.listingKey = listingKey; }

    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant sentAt) { this.sentAt = sentAt; }
}