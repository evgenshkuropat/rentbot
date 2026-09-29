package com.yourapp.rentbot.domain;

import com.yourapp.rentbot.flow.FlowStep;
import com.yourapp.rentbot.i18n.Language;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "user_filter",
        indexes = {
                @Index(name = "idx_user_filter_active_updated", columnList = "active, updated_at"),
                @Index(name = "idx_user_filter_active_status", columnList = "active, onboarded, search_status_sent_at"),
                @Index(name = "idx_user_filter_inactive_reactivation", columnList = "active, inactive_reactivation_sent_at")
        }
)
public class UserFilter {

    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id")
    private Region region;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_group_id")
    private RegionGroup regionGroup;

    @Column(name = "layout_value")
    private String layout;

    @Column(name = "max_price")
    private Integer maxPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlowStep step = FlowStep.CITY;

    @Column(nullable = false)
    private boolean active = false;

    @Column(nullable = false)
    private boolean onboarded = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Language language = Language.UA;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "reactivation_sent_at")
    private Instant reactivationSentAt;

    @Column(name = "inactive_reactivation_sent_at")
    private Instant inactiveReactivationSentAt;

    @Column(name = "search_status_sent_at")
    private Instant searchStatusSentAt;

    @Column(name = "milestone_1500_sent_at")
    private Instant milestone1500SentAt;

    @Column(name = "premium_until")
    private Instant premiumUntil;

    @Column(name = "premium_expiry_reminder_sent_at")
    private Instant premiumExpiryReminderSentAt;

    @Column(name = "premium_activated_at")
    private Instant premiumActivatedAt;

    @Column(name = "premium_second_search_reminder_sent_at")
    private Instant premiumSecondSearchReminderSentAt;

    public Long getTelegramUserId() {
        return telegramUserId;
    }

    public void setTelegramUserId(Long telegramUserId) {
        this.telegramUserId = telegramUserId;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public RegionGroup getRegionGroup() {
        return regionGroup;
    }

    public void setRegionGroup(RegionGroup regionGroup) {
        this.regionGroup = regionGroup;
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public Integer getMaxPrice() {
        return maxPrice;
    }

    public void setMaxPrice(Integer maxPrice) {
        this.maxPrice = maxPrice;
    }

    public FlowStep getStep() {
        return step;
    }

    public void setStep(FlowStep step) {
        this.step = step;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isOnboarded() {
        return onboarded;
    }

    public void setOnboarded(boolean onboarded) {
        this.onboarded = onboarded;
    }

    public Language getLanguage() {
        return language;
    }

    public void setLanguage(Language language) {
        this.language = language;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Instant getReactivationSentAt() {
        return reactivationSentAt;
    }

    public void setReactivationSentAt(Instant reactivationSentAt) {
        this.reactivationSentAt = reactivationSentAt;
    }

    public Instant getInactiveReactivationSentAt() {
        return inactiveReactivationSentAt;
    }

    public void setInactiveReactivationSentAt(Instant inactiveReactivationSentAt) {
        this.inactiveReactivationSentAt = inactiveReactivationSentAt;
    }

    public Instant getSearchStatusSentAt() {
        return searchStatusSentAt;
    }

    public void setSearchStatusSentAt(Instant searchStatusSentAt) {
        this.searchStatusSentAt = searchStatusSentAt;
    }

    public Instant getMilestone1500SentAt() {
        return milestone1500SentAt;
    }

    public void setMilestone1500SentAt(Instant milestone1500SentAt) {
        this.milestone1500SentAt = milestone1500SentAt;
    }

    public Instant getPremiumUntil() { return premiumUntil; }

    public void setPremiumUntil(Instant premiumUntil) { this.premiumUntil = premiumUntil; }

    public Instant getPremiumExpiryReminderSentAt() { return premiumExpiryReminderSentAt; }

    public void setPremiumExpiryReminderSentAt(Instant premiumExpiryReminderSentAt) {
        this.premiumExpiryReminderSentAt = premiumExpiryReminderSentAt;
    }

    public Instant getPremiumActivatedAt() { return premiumActivatedAt; }
    public void setPremiumActivatedAt(Instant premiumActivatedAt) { this.premiumActivatedAt = premiumActivatedAt; }
    public Instant getPremiumSecondSearchReminderSentAt() { return premiumSecondSearchReminderSentAt; }
    public void setPremiumSecondSearchReminderSentAt(Instant value) { premiumSecondSearchReminderSentAt = value; }
}
