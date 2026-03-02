package com.yourapp.rentbot.domain;

import com.yourapp.rentbot.flow.FlowStep;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "user_filters")
public class UserFilter {

    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    // ✅ FK на regions(id)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    // ✅ FK на region_groups(id) (nullable)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_group_id")
    private RegionGroup regionGroup;

    private String layout;

    @Column(name = "max_price")
    private Integer maxPrice;

    @Column(nullable = false)
    private boolean active = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlowStep step = FlowStep.CITY;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    // --- getters/setters ---

    public Long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(Long telegramUserId) { this.telegramUserId = telegramUserId; }

    public Region getRegion() { return region; }
    public void setRegion(Region region) { this.region = region; }

    public RegionGroup getRegionGroup() { return regionGroup; }
    public void setRegionGroup(RegionGroup regionGroup) { this.regionGroup = regionGroup; }

    public String getLayout() { return layout; }
    public void setLayout(String layout) { this.layout = layout; }

    public Integer getMaxPrice() { return maxPrice; }
    public void setMaxPrice(Integer maxPrice) { this.maxPrice = maxPrice; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public FlowStep getStep() { return step; }
    public void setStep(FlowStep step) { this.step = step; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}