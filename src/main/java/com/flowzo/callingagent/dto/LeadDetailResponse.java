package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.LeadStatus;
import java.time.Instant;
import java.util.List;

public record LeadDetailResponse(
        Long id,
        String name,
        String phoneNumber,
        String companyName,
        LeadStatus status,
        Instant createdAt,
        int totalCalls,
        List<LeadCallSummary> calls
) {
}
