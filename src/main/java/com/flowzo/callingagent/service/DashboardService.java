package com.flowzo.callingagent.service;

import com.flowzo.callingagent.dto.DashboardResponse;
import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import com.flowzo.callingagent.enums.LeadStatus;
import com.flowzo.callingagent.repository.CallRecordRepository;
import com.flowzo.callingagent.repository.EnumCount;
import com.flowzo.callingagent.repository.LeadRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pipeline totals for the dashboard. Every number comes from a {@code group by} query, so the cost
 * stays flat as the database grows instead of loading every lead and call into memory to count.
 */
@Service
public class DashboardService {

    private final LeadRepository leadRepository;
    private final CallRecordRepository callRecordRepository;

    public DashboardService(LeadRepository leadRepository, CallRecordRepository callRecordRepository) {
        this.leadRepository = leadRepository;
        this.callRecordRepository = callRecordRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse summary() {
        Map<LeadStatus, Long> leads = tally(LeadStatus.class, leadRepository.countByStatus());
        Map<CallStatus, Long> callStatuses = tally(CallStatus.class, callRecordRepository.countByStatus());
        Map<CallOutcome, Long> outcomes = tally(CallOutcome.class, callRecordRepository.countByOutcome());

        return new DashboardResponse(
                total(leads),
                leads,
                total(callStatuses),
                callStatuses.get(CallStatus.ACTIVE),
                callStatuses.get(CallStatus.COMPLETED),
                outcomes);
    }

    /** Turns grouped rows into a map that holds every constant, zero included. */
    private <E extends Enum<E>> Map<E, Long> tally(Class<E> type, List<EnumCount<E>> rows) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants()) {
            counts.put(value, 0L);
        }
        rows.forEach(row -> counts.put(row.getValue(), row.getTotal()));
        return counts;
    }

    private long total(Map<?, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}
