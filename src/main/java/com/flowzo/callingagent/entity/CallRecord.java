package com.flowzo.callingagent.entity;

import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import com.flowzo.callingagent.enums.ConversationStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
// Every call lookup, cascade delete and lead detail page filters on lead_id.
@Table(name = "calls", indexes = @Index(name = "idx_calls_lead_id", columnList = "lead_id"))
public class CallRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lead_id", nullable = false)
    private Lead lead;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallStatus status = CallStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConversationStep currentStep = ConversationStep.GREETING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CallOutcome outcome = CallOutcome.IN_PROGRESS;

    private Boolean interestedInCrm;

    private Integer salesTeamSize;

    private String leadManagementMethod;

    private Boolean wantsDemo;

    /**
     * Times the current question has been repeated because the reply did not answer it.
     * Nullable so that {@code ddl-auto=update} can add the column to a database that already
     * holds call rows; rows created before this column existed read back as zero.
     */
    private Integer repromptCount = 0;

    /** Comma-separated {@link ConversationStep} names the agent gave up on, so they are not re-asked. */
    @Column(length = 500)
    private String skippedSteps;

    @Column(length = 2000)
    private String summary;

    @Column(nullable = false, updatable = false)
    private Instant startedAt;

    private Instant completedAt;

    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        startedAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Lead getLead() {
        return lead;
    }

    public void setLead(Lead lead) {
        this.lead = lead;
    }

    public CallStatus getStatus() {
        return status;
    }

    public void setStatus(CallStatus status) {
        this.status = status;
    }

    public ConversationStep getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(ConversationStep currentStep) {
        this.currentStep = currentStep;
    }

    public CallOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(CallOutcome outcome) {
        this.outcome = outcome;
    }

    public Boolean getInterestedInCrm() {
        return interestedInCrm;
    }

    public void setInterestedInCrm(Boolean interestedInCrm) {
        this.interestedInCrm = interestedInCrm;
    }

    public Integer getSalesTeamSize() {
        return salesTeamSize;
    }

    public void setSalesTeamSize(Integer salesTeamSize) {
        this.salesTeamSize = salesTeamSize;
    }

    public String getLeadManagementMethod() {
        return leadManagementMethod;
    }

    public void setLeadManagementMethod(String leadManagementMethod) {
        this.leadManagementMethod = leadManagementMethod;
    }

    public Boolean getWantsDemo() {
        return wantsDemo;
    }

    public void setWantsDemo(Boolean wantsDemo) {
        this.wantsDemo = wantsDemo;
    }

    public int getRepromptCount() {
        return repromptCount == null ? 0 : repromptCount;
    }

    public void setRepromptCount(int repromptCount) {
        this.repromptCount = repromptCount;
    }

    public String getSkippedSteps() {
        return skippedSteps;
    }

    public void setSkippedSteps(String skippedSteps) {
        this.skippedSteps = skippedSteps;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
