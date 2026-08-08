package com.flowzo.callingagent.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.flowzo.callingagent.entity.FollowUp;
import com.flowzo.callingagent.enums.FollowUpStatus;

@Repository
public interface FollowUpRepository extends JpaRepository<FollowUp, Long> {

	List<FollowUp> findByStatusAndScheduledAtLessThanEqual(FollowUpStatus status, LocalDateTime scheduledAt);

	List<FollowUp> findByLeadIdOrderByScheduledAtDesc(Long leadId);
}
