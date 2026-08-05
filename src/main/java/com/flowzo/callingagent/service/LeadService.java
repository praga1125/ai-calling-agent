package com.flowzo.callingagent.service;

import com.flowzo.callingagent.dto.CreateLeadRequest;
import com.flowzo.callingagent.dto.LeadCallSummary;
import com.flowzo.callingagent.dto.LeadDetailResponse;
import com.flowzo.callingagent.dto.LeadResponse;
import com.flowzo.callingagent.dto.UpdateLeadRequest;
import com.flowzo.callingagent.entity.CallRecord;
import com.flowzo.callingagent.entity.ConversationMessage;
import com.flowzo.callingagent.entity.Lead;
import com.flowzo.callingagent.enums.LeadStatus;
import com.flowzo.callingagent.exception.BadRequestException;
import com.flowzo.callingagent.exception.ResourceNotFoundException;
import com.flowzo.callingagent.repository.CallRecordRepository;
import com.flowzo.callingagent.repository.ConversationMessageRepository;
import com.flowzo.callingagent.repository.LeadRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadService {

    private static final Logger log = LoggerFactory.getLogger(LeadService.class);

    private final LeadRepository leadRepository;
    private final CallRecordRepository callRecordRepository;
    private final ConversationMessageRepository messageRepository;
    private final AudioStorageService audioStorageService;

    public LeadService(
            LeadRepository leadRepository,
            CallRecordRepository callRecordRepository,
            ConversationMessageRepository messageRepository,
            AudioStorageService audioStorageService
    ) {
        this.leadRepository = leadRepository;
        this.callRecordRepository = callRecordRepository;
        this.messageRepository = messageRepository;
        this.audioStorageService = audioStorageService;
    }

    @Transactional
    public LeadResponse createLead(CreateLeadRequest request) {
        Lead lead = new Lead();
        lead.setName(request.getName().trim());
        lead.setPhoneNumber(request.getPhoneNumber().trim());
        lead.setCompanyName(trimToNull(request.getCompanyName()));
        lead.setStatus(request.getStatus() == null ? LeadStatus.NEW : request.getStatus());
        return toResponse(leadRepository.save(lead));
    }

    @Transactional(readOnly = true)
    public List<LeadResponse> listLeads() {
        return leadRepository.findAll(Sort.by(Sort.Direction.ASC, "id")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public LeadDetailResponse getLead(Long id) {
        Lead lead = getLeadEntity(id);
        List<LeadCallSummary> calls = callRecordRepository.findByLeadIdOrderByStartedAtDesc(id).stream()
                .map(call -> new LeadCallSummary(
                        call.getId(),
                        call.getStatus(),
                        call.getCurrentStep(),
                        call.getOutcome(),
                        call.getSummary(),
                        call.getStartedAt(),
                        call.getCompletedAt()))
                .toList();

        return new LeadDetailResponse(
                lead.getId(),
                lead.getName(),
                lead.getPhoneNumber(),
                lead.getCompanyName(),
                lead.getStatus(),
                lead.getCreatedAt(),
                calls.size(),
                calls);
    }

    @Transactional
    public LeadResponse updateLead(Long id, UpdateLeadRequest request) {
        Lead lead = getLeadEntity(id);

        if (request.getName() != null) {
            if (request.getName().isBlank()) {
                throw new BadRequestException("name must not be blank");
            }
            lead.setName(request.getName().trim());
        }
        if (request.getPhoneNumber() != null) {
            if (request.getPhoneNumber().isBlank()) {
                throw new BadRequestException("phoneNumber must not be blank");
            }
            lead.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getCompanyName() != null) {
            lead.setCompanyName(trimToNull(request.getCompanyName()));
        }
        if (request.getStatus() != null) {
            lead.setStatus(request.getStatus());
        }

        return toResponse(leadRepository.save(lead));
    }

    /** Removes the lead together with its calls, stored messages, and generated audio files. */
    @Transactional
    public void deleteLead(Long id) {
        Lead lead = getLeadEntity(id);

        List<Long> callIds = callRecordRepository.findByLeadIdOrderByStartedAtDesc(id).stream()
                .map(CallRecord::getId)
                .toList();

        if (!callIds.isEmpty()) {
            messageRepository.findAudioFilePaths(callIds)
                    .forEach(path -> audioStorageService.deleteQuietly(Path.of(path)));
            messageRepository.deleteByCallIdIn(callIds);
            callRecordRepository.deleteByLeadId(id);
        }

        leadRepository.delete(lead);
        log.info("Deleted lead {} together with {} call(s)", id, callIds.size());
    }

    @Transactional(readOnly = true)
    public Lead getLeadEntity(Long id) {
        return leadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Lead not found: " + id));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LeadResponse toResponse(Lead lead) {
        return new LeadResponse(
                lead.getId(),
                lead.getName(),
                lead.getPhoneNumber(),
                lead.getCompanyName(),
                lead.getStatus(),
                lead.getCreatedAt()
        );
    }
}
