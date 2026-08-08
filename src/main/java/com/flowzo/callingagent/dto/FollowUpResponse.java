package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.FollowUpStatus;
import java.time.LocalDateTime;

public class FollowUpResponse {

    private Long id;
    private Long leadId;
    private String customerName;
    private String phoneNumber;
    private LocalDateTime scheduledAt;
    private FollowUpStatus status;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    public FollowUpResponse() {
    }

    public FollowUpResponse(
            Long id,
            Long leadId,
            String customerName,
            String phoneNumber,
            LocalDateTime scheduledAt,
            FollowUpStatus status,
            String notes,
            LocalDateTime createdAt,
            LocalDateTime completedAt
    ) {
        this.id = id;
        this.leadId = leadId;
        this.customerName = customerName;
        this.phoneNumber = phoneNumber;
        this.scheduledAt = scheduledAt;
        this.status = status;
        this.notes = notes;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLeadId() {
        return leadId;
    }

    public void setLeadId(Long leadId) {
        this.leadId = leadId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public FollowUpStatus getStatus() {
        return status;
    }

    public void setStatus(FollowUpStatus status) {
        this.status = status;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }
}
