package com.flowzo.callingagent.entity;

import com.flowzo.callingagent.enums.SpeakerType;
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
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
// History is always read, and the next sequence number always computed, per call.
@Table(name = "conversation_messages",
        indexes = @Index(name = "idx_messages_call_id", columnList = "call_id"))
public class ConversationMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "call_id", nullable = false)
    private CallRecord call;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SpeakerType speakerType;

    @Column(nullable = false, length = 4000)
    private String messageText;

    @Column(nullable = false)
    private Integer sequenceNumber;

    private String audioFilePath;

    @Column(nullable = false, updatable = false)
    private Instant timestamp;

    @PrePersist
    void onCreate() {
        timestamp = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CallRecord getCall() {
        return call;
    }

    public void setCall(CallRecord call) {
        this.call = call;
    }

    public SpeakerType getSpeakerType() {
        return speakerType;
    }

    public void setSpeakerType(SpeakerType speakerType) {
        this.speakerType = speakerType;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public Integer getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(Integer sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getAudioFilePath() {
        return audioFilePath;
    }

    public void setAudioFilePath(String audioFilePath) {
        this.audioFilePath = audioFilePath;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
