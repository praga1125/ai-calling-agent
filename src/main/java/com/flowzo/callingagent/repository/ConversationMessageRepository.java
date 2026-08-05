package com.flowzo.callingagent.repository;

import com.flowzo.callingagent.entity.ConversationMessage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findByCallIdOrderBySequenceNumberAsc(Long callId);

    @Query("select coalesce(max(m.sequenceNumber), 0) from ConversationMessage m where m.call.id = :callId")
    Integer findMaxSequenceNumber(Long callId);

    Optional<ConversationMessage> findFirstByCallIdAndSpeakerTypeOrderBySequenceNumberDesc(
            Long callId, com.flowzo.callingagent.enums.SpeakerType speakerType);

    /** Only the paths, so deleting a noisy lead does not load every 4 000-character transcript. */
    @Query("select m.audioFilePath from ConversationMessage m "
            + "where m.call.id in :callIds and m.audioFilePath is not null")
    List<String> findAudioFilePaths(List<Long> callIds);

    void deleteByCallIdIn(List<Long> callIds);
}
