package com.sumicare.room.domain;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "beds")
public class Bed {

    @Id
    @GeneratedValue
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "room_id", nullable = false, columnDefinition = "uuid")
    private UUID roomId;

    @Column(name = "bed_label", nullable = false)
    private String bedLabel;

    @Column(name = "row_index")
    private Integer rowIndex;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getRoomId() { return roomId; }
    public void setRoomId(UUID roomId) { this.roomId = roomId; }
    public String getBedLabel() { return bedLabel; }
    public void setBedLabel(String bedLabel) { this.bedLabel = bedLabel; }
    public Integer getRowIndex() { return rowIndex; }
    public void setRowIndex(Integer rowIndex) { this.rowIndex = rowIndex; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
