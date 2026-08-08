package com.flowzo.callingagent.scheduler;

import com.flowzo.callingagent.entity.FollowUp;
import com.flowzo.callingagent.enums.FollowUpStatus;
import com.flowzo.callingagent.repository.FollowUpRepository;
import com.flowzo.callingagent.service.CallService;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FollowUpScheduler {

    private static final Logger log = LoggerFactory.getLogger(FollowUpScheduler.class);

    private final FollowUpRepository followUpRepository;
    private final CallService callService;

    public FollowUpScheduler(FollowUpRepository followUpRepository, CallService callService) {
        this.followUpRepository = followUpRepository;
        this.callService = callService;
    }

    @Scheduled(fixedRate = 60_000)
    public void processDueFollowUps() {
        LocalDateTime now = LocalDateTime.now();
        List<FollowUp> due = followUpRepository.findByStatusAndScheduledAtLessThanEqual(
                FollowUpStatus.PENDING, now);

        if (due.isEmpty()) {
            return;
        }

        log.info("Processing {} due follow-up(s)", due.size());

        for (FollowUp followUp : due) {
            try {
                var started = callService.startCall(followUp.getLeadId());
                followUp.setStatus(FollowUpStatus.COMPLETED);
                followUp.setCompletedAt(LocalDateTime.now());
                followUpRepository.save(followUp);
                log.info(
                        "Follow-up {} triggered call {} for lead {} ({})",
                        followUp.getId(),
                        started.callId(),
                        followUp.getLeadId(),
                        followUp.getCustomerName());
            } catch (Exception e) {
                followUp.setStatus(FollowUpStatus.MISSED);
                followUp.setCompletedAt(LocalDateTime.now());
                followUpRepository.save(followUp);
                log.error(
                        "Follow-up {} failed for lead {} ({}): {}",
                        followUp.getId(),
                        followUp.getLeadId(),
                        followUp.getCustomerName(),
                        e.getMessage());
            }
        }
    }
}
