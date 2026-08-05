package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.LeadStatus;
import java.time.Instant;

public record LeadResponse(
        Long id,
        String name,
        String phoneNumber,
        String companyName,
        LeadStatus status,
        Instant createdAt
) {
}
