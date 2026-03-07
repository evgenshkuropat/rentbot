package com.yourapp.rentbot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "regions", uniqueConstraints = @UniqueConstraint(name = "uk_region_code", columnNames = "code"))
public class Region {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String title;

    @Column(name = "has_districts", nullable = false)
    private boolean hasDistricts = false;

    @Column(name = "sreality_region_id")
    private Integer srealityRegionId;

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isHasDistricts() {
        return hasDistricts;
    }

    public void setHasDistricts(boolean hasDistricts) {
        this.hasDistricts = hasDistricts;
    }

    public Integer getSrealityRegionId() {
        return srealityRegionId;
    }

    public void setSrealityRegionId(Integer srealityRegionId) {
        this.srealityRegionId = srealityRegionId;
    }
}