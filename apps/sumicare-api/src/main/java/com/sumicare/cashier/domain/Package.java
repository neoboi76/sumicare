package com.sumicare.cashier.domain;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "packages")
public class Package {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "benefits", columnDefinition = "TEXT")
    private String benefits;

    @Column(name = "max_stay_hours")
    private Integer maxStayHours;

    @Column(name = "default_pax", nullable = false)
    private int defaultPax = 1;

    @Column(name = "is_couple", nullable = false)
    private boolean couple = false;

    @Column(name = "includes_massage", nullable = false)
    private boolean includesMassage = true;

    @Column(name = "bundles_private_room", nullable = false)
    private boolean bundlesPrivateRoom = false;

    @Column(name = "requires_vip_room", nullable = false)
    private boolean requiresVipRoom = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getBenefits() { return benefits; }
    public void setBenefits(String benefits) { this.benefits = benefits; }
    public Integer getMaxStayHours() { return maxStayHours; }
    public void setMaxStayHours(Integer maxStayHours) { this.maxStayHours = maxStayHours; }
    public int getDefaultPax() { return defaultPax; }
    public void setDefaultPax(int defaultPax) { this.defaultPax = defaultPax; }
    public boolean isCouple() { return couple; }
    public void setCouple(boolean couple) { this.couple = couple; }
    public boolean isIncludesMassage() { return includesMassage; }
    public void setIncludesMassage(boolean includesMassage) { this.includesMassage = includesMassage; }
    public boolean isBundlesPrivateRoom() { return bundlesPrivateRoom; }
    public void setBundlesPrivateRoom(boolean bundlesPrivateRoom) { this.bundlesPrivateRoom = bundlesPrivateRoom; }
    public boolean isRequiresVipRoom() { return requiresVipRoom; }
    public void setRequiresVipRoom(boolean requiresVipRoom) { this.requiresVipRoom = requiresVipRoom; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
