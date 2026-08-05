package com.flowzo.callingagent.repository;

import com.flowzo.callingagent.entity.CallRecord;
import com.flowzo.callingagent.enums.CallOutcome;
import com.flowzo.callingagent.enums.CallStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CallRecordRepository extends JpaRepository<CallRecord, Long> {

    List<CallRecord> findByLeadIdOrderByStartedAtDesc(Long leadId);

    List<CallRecord> findByLeadIdAndStatus(Long leadId, CallStatus status);

    void deleteByLeadId(Long leadId);

    @Query("select c.status as value, count(c) as total from CallRecord c group by c.status")
    List<EnumCount<CallStatus>> countByStatus();

    @Query("select c.outcome as value, count(c) as total from CallRecord c group by c.outcome")
    List<EnumCount<CallOutcome>> countByOutcome();
}
