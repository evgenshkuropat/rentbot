package com.yourapp.rentbot.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "region_groups",
        uniqueConstraints = @UniqueConstraint(name = "uk_region_group_code", columnNames = "code"))
public class RegionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "region_id", nullable = false)
    private Region region;

    @Column(nullable = false)
    private String code;   // PRAHA_1_3, PRAHA_ALL ...

    @Column(nullable = false)
    private String title;  // "Praha 1–3", "Всі райони"

    // --- getters/setters ---

    public Long getId() { return id; }

    public Region getRegion() { return region; }
    public void setRegion(Region region) { this.region = region; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}