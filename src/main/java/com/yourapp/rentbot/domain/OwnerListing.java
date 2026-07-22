package com.yourapp.rentbot.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "owner_listing")
public class OwnerListing {

    public enum Status {
        PENDING,
        APPROVED,
        ARCHIVED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_by_telegram_id", nullable = false)
    private Long createdByTelegramId;

    @Column(name = "created_by_username", length = 255)
    private String createdByUsername;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false, length = 500)
    private String locality;

    @Column(name = "layout_value", nullable = false, length = 50)
    private String layout;

    @Column(name = "price_czk", nullable = false)
    private Integer priceCzk;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false, length = 500)
    private String contact;

    @Column(name = "photo_file_id", length = 1000)
    private String photoFileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Status status = Status.APPROVED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "approved_at")
    private Instant approvedAt = Instant.now();

    public Long getId() {
        return id;
    }

    public Long getCreatedByTelegramId() {
        return createdByTelegramId;
    }

    public void setCreatedByTelegramId(Long createdByTelegramId) {
        this.createdByTelegramId = createdByTelegramId;
    }

    public String getCreatedByUsername() {
        return createdByUsername;
    }

    public void setCreatedByUsername(String createdByUsername) {
        this.createdByUsername = createdByUsername;
    }

    public Region getRegion() {
        return region;
    }

    public void setRegion(Region region) {
        this.region = region;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getLayout() {
        return layout;
    }

    public void setLayout(String layout) {
        this.layout = layout;
    }

    public Integer getPriceCzk() {
        return priceCzk;
    }

    public void setPriceCzk(Integer priceCzk) {
        this.priceCzk = priceCzk;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getPhotoFileId() {
        return photoFileId;
    }

    public void setPhotoFileId(String photoFileId) {
        this.photoFileId = photoFileId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Instant approvedAt) {
        this.approvedAt = approvedAt;
    }
}
