package com.flowzo.callingagent.service;

import com.flowzo.callingagent.dto.FollowUpRequest;
import com.flowzo.callingagent.dto.FollowUpResponse;
import com.flowzo.callingagent.entity.FollowUp;
import com.flowzo.callingagent.entity.Lead;
import com.flowzo.callingagent.enums.FollowUpStatus;
import com.flowzo.callingagent.exception.BadRequestException;
import com.flowzo.callingagent.exception.ResourceNotFoundException;
import com.flowzo.callingagent.repository.FollowUpRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FollowUpService {

    private final FollowUpRepository followUpRepository;
    private final LeadService leadService;

    public FollowUpService(FollowUpRepository followUpRepository, LeadService leadService) {
        this.followUpRepository = followUpRepository;
        this.leadService = leadService;
    }

    @Transactional
    public FollowUpResponse create(FollowUpRequest request) {
        Lead lead = leadService.getLeadEntity(request.getLeadId());
        if (request.getScheduledAt() == null) {
            throw new BadRequestException("scheduledAt is required");
        }

        FollowUp followUp = new FollowUp();
        followUp.setLeadId(lead.getId());
        followUp.setCustomerName(firstNonBlank(request.getCustomerName(), lead.getName()));
        followUp.setPhoneNumber(firstNonBlank(request.getPhoneNumber(), lead.getPhoneNumber()));
        followUp.setScheduledAt(request.getScheduledAt());
        followUp.setNotes(trimToNull(request.getNotes()));
        followUp.setStatus(FollowUpStatus.PENDING);
        followUp.setCreatedAt(LocalDateTime.now());

        return toResponse(followUpRepository.save(followUp));
    }

    @Transactional(readOnly = true)
    public List<FollowUpResponse> getAll() {
        return followUpRepository.findAll(Sort.by(Sort.Direction.DESC, "scheduledAt")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FollowUpResponse> getByLeadId(Long leadId) {
        leadService.getLeadEntity(leadId);
        return followUpRepository.findByLeadIdOrderByScheduledAtDesc(leadId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public FollowUpResponse getById(Long id) {
        return toResponse(requireFollowUp(id));
    }

    @Transactional
    public FollowUpResponse update(Long id, FollowUpRequest request) {
        FollowUp followUp = requireFollowUp(id);
        if (followUp.getStatus() != FollowUpStatus.PENDING) {
            throw new BadRequestException("Only pending follow-ups can be updated");
        }

        Lead lead = leadService.getLeadEntity(request.getLeadId());
        if (request.getScheduledAt() == null) {
            throw new BadRequestException("scheduledAt is required");
        }

        followUp.setLeadId(lead.getId());
        followUp.setCustomerName(firstNonBlank(request.getCustomerName(), lead.getName()));
        followUp.setPhoneNumber(firstNonBlank(request.getPhoneNumber(), lead.getPhoneNumber()));
        followUp.setScheduledAt(request.getScheduledAt());
        followUp.setNotes(trimToNull(request.getNotes()));

        return toResponse(followUpRepository.save(followUp));
    }

    @Transactional
    public void delete(Long id) {
        FollowUp followUp = requireFollowUp(id);
        followUpRepository.delete(followUp);
    }

    @Transactional
    public FollowUpResponse complete(Long id) {
        FollowUp followUp = requireFollowUp(id);
        if (followUp.getStatus() != FollowUpStatus.PENDING) {
            throw new BadRequestException("Only pending follow-ups can be completed");
        }
        followUp.setStatus(FollowUpStatus.COMPLETED);
        followUp.setCompletedAt(LocalDateTime.now());
        return toResponse(followUpRepository.save(followUp));
    }

    @Transactional
    public FollowUpResponse cancel(Long id) {
        FollowUp followUp = requireFollowUp(id);
        if (followUp.getStatus() != FollowUpStatus.PENDING) {
            throw new BadRequestException("Only pending follow-ups can be cancelled");
        }
        followUp.setStatus(FollowUpStatus.CANCELLED);
        followUp.setCompletedAt(LocalDateTime.now());
        return toResponse(followUpRepository.save(followUp));
    }

    private FollowUp requireFollowUp(Long id) {
        return followUpRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Follow-up not found: " + id));
    }

    private String firstNonBlank(String preferred, String fallback) {
        String trimmed = trimToNull(preferred);
        return trimmed != null ? trimmed : fallback;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private FollowUpResponse toResponse(FollowUp followUp) {
        return new FollowUpResponse(
                followUp.getId(),
                followUp.getLeadId(),
                followUp.getCustomerName(),
                followUp.getPhoneNumber(),
                followUp.getScheduledAt(),
                followUp.getStatus(),
                followUp.getNotes(),
                followUp.getCreatedAt(),
                followUp.getCompletedAt()
        );
    }
}
