package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.LeadStatus;
import java.util.Map;

/**
 * Pipeline totals for the dashboard. Both maps always carry every enum constant, so the UI can
 * render a fixed set of tiles without null checks.
 *
 * @param leadsByStatus  how many leads sit in each stage, including QUALIFIED and CONVERTED
 * @param callsByOutcome how the calls ended, counting only the ones that reached an outcome
 */
public record DashboardResponse(
        long totalLeads,
        Map<LeadStatus, Long> leadsByStatus,
        long totalCalls,
        long activeCalls,
        long completedCalls,
        Map<CallOutcome, Long> callsByOutcome
) {
}
