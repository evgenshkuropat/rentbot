package com.yourapp.rentbot.domain;

import com.yourapp.rentbot.flow.FlowStep;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_filter")
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

    @Column(name = "updated_at")
    private Instant updatedAt;

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

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}