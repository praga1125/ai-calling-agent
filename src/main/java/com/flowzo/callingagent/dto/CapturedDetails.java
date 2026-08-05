package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.entity.CallRecord;

/**
 * What the agent has learned so far in the call. Returned with every turn so the conversation view
 * can show the qualification filling up, instead of only revealing it in the closing summary. A
 * null field is one the customer has not answered yet.
 */
public record CapturedDetails(
        Boolean interestedInCrm,
        Integer salesTeamSize,
        String leadManagementMethod,
        Boolean wantsDemo
) {

    public static CapturedDetails of(CallRecord call) {
        return new CapturedDetails(
                call.getInterestedInCrm(),
                call.getSalesTeamSize(),
                call.getLeadManagementMethod(),
                call.getWantsDemo());
    }
}
