package com.flowzo.callingagent.repository;

import com.flowzo.callingagent.entity.Lead;
import com.flowzo.callingagent.enums.LeadStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface LeadRepository extends JpaRepository<Lead, Long> {

    @Query("select l.status as value, count(l) as total from Lead l group by l.status")
    List<EnumCount<LeadStatus>> countByStatus();
}
