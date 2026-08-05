package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import java.time.Instant;

public record CallSummaryResponse(
        Long callId,
        Long leadId,
        String leadName,
        String companyName,
        CallStatus status,
        CallOutcome outcome,
        Boolean interestedInCrm,
        Integer salesTeamSize,
        String leadManagementMethod,
        Boolean wantsDemo,
        String summary,
        Instant startedAt,
        Instant completedAt
) {
}
