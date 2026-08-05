package com.flowzo.callingagent.service;

import com.flowzo.callingagent.dto.CallSummaryResponse;
import com.flowzo.callingagent.dto.CapturedDetails;
import com.flowzo.callingagent.dto.ConversationHistoryResponse;
import com.flowzo.callingagent.dto.ConversationMessageResponse;
import com.flowzo.callingagent.dto.CustomerResponseResult;
import com.flowzo.callingagent.dto.StartCallResponse;
import com.flowzo.callingagent.entity.CallRecord;
import com.flowzo.callingagent.entity.ConversationMessage;
import com.flowzo.callingagent.entity.Lead;
import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import com.flowzo.callingagent.enums.ConversationStep;
import com.flowzo.callingagent.enums.CustomerInputMode;
import com.flowzo.callingagent.enums.LeadStatus;
import com.flowzo.callingagent.enums.SpeakerType;
import com.flowzo.callingagent.exception.BadRequestException;
import com.flowzo.callingagent.exception.ResourceNotFoundException;
import com.flowzo.callingagent.repository.CallRecordRepository;
import com.flowzo.callingagent.repository.ConversationMessageRepository;
import com.flowzo.callingagent.service.audio.WavDiagnostics;
import com.flowzo.callingagent.service.conversation.RuleBasedConversationEngine;
import com.flowzo.callingagent.service.stt.SpeechToTextService;
import com.flowzo.callingagent.service.tts.TextToSpeechService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CallService {

    private static final Logger log = LoggerFactory.getLogger(CallService.class);

    private final CallRecordRepository callRecordRepository;
    private final ConversationMessageRepository messageRepository;
    private final LeadService leadService;
    private final RuleBasedConversationEngine conversationEngine;
    private final SpeechToTextService speechToTextService;
    private final TextToSpeechService textToSpeechService;
    private final AudioStorageService audioStorageService;

    public CallService(
            CallRecordRepository callRecordRepository,
            ConversationMessageRepository messageRepository,
            LeadService leadService,
            RuleBasedConversationEngine conversationEngine,
            SpeechToTextService speechToTextService,
            TextToSpeechService textToSpeechService,
            AudioStorageService audioStorageService
    ) {
        this.callRecordRepository = callRecordRepository;
        this.messageRepository = messageRepository;
        this.leadService = leadService;
        this.conversationEngine = conversationEngine;
        this.speechToTextService = speechToTextService;
        this.textToSpeechService = textToSpeechService;
        this.audioStorageService = audioStorageService;
    }

    @Transactional
    public StartCallResponse startCall(Long leadId) {
        Lead lead = leadService.getLeadEntity(leadId);
        closeUnfinishedCalls(lead);

        CallRecord call = new CallRecord();
        call.setLead(lead);
        call.setStatus(CallStatus.ACTIVE);
        call.setCurrentStep(ConversationStep.AWAIT_INTEREST);
        call.setOutcome(CallOutcome.IN_PROGRESS);
        call = callRecordRepository.save(call);

        if (lead.getStatus() == LeadStatus.NEW) {
            lead.setStatus(LeadStatus.CONTACTED);
        }

        String greeting = conversationEngine.buildGreeting(lead);
        Path audioPath = textToSpeechService.synthesize(greeting, "call-" + call.getId() + "-ai");
        saveMessage(call, SpeakerType.AI, greeting, audioPath);

        return new StartCallResponse(
                call.getId(),
                lead.getId(),
                lead.getName(),
                call.getStatus(),
                call.getCurrentStep(),
                call.getOutcome(),
                greeting,
                audioStorageService.toPublicUrl(audioPath),
                call.getStartedAt()
        );
    }

    /** The customer answers by voice: the recording is transcribed by OpenAI before the agent replies. */
    @Transactional
    public CustomerResponseResult submitCustomerResponse(Long callId, MultipartFile audio) {
        CallRecord call = requireActiveCall(callId);
        if (audio == null || audio.isEmpty()) {
            throw new BadRequestException("Audio file is required");
        }

        Path customerAudio = audioStorageService.saveUpload(audio, "call-" + callId + "-customer");

        String transcript;
        try {
            transcript = speechToTextService.transcribe(customerAudio, audio.getOriginalFilename()).trim();
        } catch (RuntimeException e) {
            audioStorageService.quarantine(customerAudio);
            throw e;
        }
        if (transcript.isEmpty()) {
            // Kept, not deleted: "could not be read" and a genuinely silent take are indistinguishable
            // from the outside, and the file is the only way to tell them apart after the fact.
            log.warn("Call {}: recording produced no transcript. {}", callId, WavDiagnostics.describe(customerAudio));
            audioStorageService.quarantine(customerAudio);
            throw new BadRequestException(
                    "That recording could not be read. Speak for at least a second with the right "
                            + "microphone selected and try again, or type the reply instead.");
        }

        return advanceConversation(call, transcript, customerAudio, CustomerInputMode.VOICE, true);
    }

    /**
     * The customer answers by typing, which keeps a demo moving when no microphone is available or
     * the OpenAI quota is exhausted. Speech-to-text is skipped by definition, so the stored customer
     * message has no audio file.
     *
     * @param speak whether the agent's reply is still synthesized; pass false to skip text-to-speech
     *              as well, for example while waiting out a rate limit
     */
    @Transactional
    public CustomerResponseResult submitCustomerText(Long callId, String text, boolean speak) {
        CallRecord call = requireActiveCall(callId);
        String transcript = text == null ? "" : text.trim();
        if (transcript.isEmpty()) {
            throw new BadRequestException("text must not be blank");
        }

        return advanceConversation(call, transcript, null, CustomerInputMode.TEXT, speak);
    }

    /**
     * Shared turn handling for both input modes: store what the customer said, ask the engine for the
     * next message, speak it, and close the call with a summary when the engine says it is finished.
     */
    private CustomerResponseResult advanceConversation(
            CallRecord call,
            String transcript,
            Path customerAudio,
            CustomerInputMode inputMode,
            boolean speak
    ) {
        saveMessage(call, SpeakerType.CUSTOMER, transcript, customerAudio);

        RuleBasedConversationEngine.EngineResult result = conversationEngine.processCustomerReply(call, transcript);
        call.setCurrentStep(result.nextStep());
        call.setOutcome(result.outcome());

        Path aiAudio = speak
                ? textToSpeechService.synthesize(result.aiMessage(), "call-" + call.getId() + "-ai")
                : null;
        saveMessage(call, SpeakerType.AI, result.aiMessage(), aiAudio);

        if (result.completed()) {
            call.setStatus(CallStatus.COMPLETED);
            call.setCompletedAt(Instant.now());
            call.setSummary(conversationEngine.buildSummary(call));
        }

        callRecordRepository.save(call);

        return new CustomerResponseResult(
                call.getId(),
                transcript,
                inputMode,
                result.aiMessage(),
                audioStorageService.toPublicUrl(aiAudio),
                call.getCurrentStep(),
                call.getStatus(),
                call.getOutcome(),
                result.completed(),
                CapturedDetails.of(call)
        );
    }

    @Transactional(readOnly = true)
    public ConversationHistoryResponse getConversation(Long callId) {
        CallRecord call = getCallEntity(callId);
        List<ConversationMessageResponse> messages = messageRepository
                .findByCallIdOrderBySequenceNumberAsc(callId)
                .stream()
                .map(this::toMessageResponse)
                .toList();

        return new ConversationHistoryResponse(
                call.getId(),
                call.getLead().getId(),
                call.getLead().getName(),
                call.getStatus(),
                call.getOutcome(),
                call.getStartedAt(),
                call.getCompletedAt(),
                messages
        );
    }

    @Transactional(readOnly = true)
    public CallSummaryResponse getSummary(Long callId) {
        CallRecord call = getCallEntity(callId);
        String summary = call.getSummary();
        if (summary == null || summary.isBlank()) {
            summary = conversationEngine.buildSummary(call);
        }
        return new CallSummaryResponse(
                call.getId(),
                call.getLead().getId(),
                call.getLead().getName(),
                call.getLead().getCompanyName(),
                call.getStatus(),
                call.getOutcome(),
                call.getInterestedInCrm(),
                call.getSalesTeamSize(),
                call.getLeadManagementMethod(),
                call.getWantsDemo(),
                summary,
                call.getStartedAt(),
                call.getCompletedAt()
        );
    }

    /**
     * A lead can only have one call in progress. Any earlier call left open is closed with the
     * information gathered so far, so its history and outcome are never left dangling.
     */
    private void closeUnfinishedCalls(Lead lead) {
        for (CallRecord stale : callRecordRepository.findByLeadIdAndStatus(lead.getId(), CallStatus.ACTIVE)) {
            stale.setStatus(CallStatus.COMPLETED);
            stale.setCurrentStep(ConversationStep.COMPLETED);
            stale.setOutcome(CallOutcome.COMPLETED);
            stale.setCompletedAt(Instant.now());
            stale.setSummary(conversationEngine.buildSummary(stale)
                    + " This call was closed automatically because a new call was started for the lead.");
            callRecordRepository.save(stale);
            log.info("Closed unfinished call {} for lead {}", stale.getId(), lead.getId());
        }
    }

    private CallRecord getCallEntity(Long callId) {
        return callRecordRepository.findById(callId)
                .orElseThrow(() -> new ResourceNotFoundException("Call not found: " + callId));
    }

    private CallRecord requireActiveCall(Long callId) {
        CallRecord call = getCallEntity(callId);
        if (call.getStatus() == CallStatus.COMPLETED) {
            throw new BadRequestException("Call already completed");
        }
        return call;
    }

    private void saveMessage(CallRecord call, SpeakerType speaker, String text, Path audioPath) {
        Integer max = messageRepository.findMaxSequenceNumber(call.getId());
        int next = (max == null ? 0 : max) + 1;

        ConversationMessage message = new ConversationMessage();
        message.setCall(call);
        message.setSpeakerType(speaker);
        message.setMessageText(text);
        message.setSequenceNumber(next);
        message.setAudioFilePath(audioPath == null ? null : audioPath.toAbsolutePath().toString());
        messageRepository.save(message);
    }

    private ConversationMessageResponse toMessageResponse(ConversationMessage message) {
        String audioUrl = null;
        if (message.getAudioFilePath() != null) {
            audioUrl = audioStorageService.toPublicUrl(Path.of(message.getAudioFilePath()));
        }
        return new ConversationMessageResponse(
                message.getId(),
                message.getSpeakerType(),
                message.getMessageText(),
                message.getSequenceNumber(),
                audioUrl,
                message.getTimestamp()
        );
    }
}
