package com.sumicare.room.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "organization_id", nullable = false, columnDefinition = "uuid")
    private UUID organizationId;

    @Column(name = "room_number", nullable = false)
    private String roomNumber;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "room_type", nullable = false)
    private String roomType;

    @Column(name = "row_segmented", nullable = false)
    private boolean rowSegmented = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getOrganizationId() { return organizationId; }
    public void setOrganizationId(UUID organizationId) { this.organizationId = organizationId; }
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public Integer getFloor() { return floor; }
    public void setFloor(Integer floor) { this.floor = floor; }
    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }
    public boolean isRowSegmented() { return rowSegmented; }
    public void setRowSegmented(boolean rowSegmented) { this.rowSegmented = rowSegmented; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
