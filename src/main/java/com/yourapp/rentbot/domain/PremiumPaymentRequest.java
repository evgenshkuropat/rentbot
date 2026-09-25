package com.yourapp.rentbot.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "premium_payment_requests")
public class PremiumPaymentRequest {
    public enum Status { PENDING, APPROVED, REJECTED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "telegram_user_id", nullable = false) private Long telegramUserId;
    @Column(name = "payment_method", nullable = false) private String paymentMethod;
    @Column(name = "amount_czk", nullable = false) private int amountCzk = 99;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status = Status.PENDING;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();
    @Column(name = "processed_at") private Instant processedAt;
    public Long getId() { return id; }
    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long value) { telegramUserId = value; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String value) { paymentMethod = value; }
    public Status getStatus() { return status; }
    public void setStatus(Status value) { status = value; }
    public void setProcessedAt(Instant value) { processedAt = value; }
}
