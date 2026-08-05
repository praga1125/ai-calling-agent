package com.flowzo.callingagent.dto;

import com.flowzo.callingagent.enums.LeadStatus;

/** Partial update: only non-null fields are applied. */
public class UpdateLeadRequest {

    private String name;
    private String phoneNumber;
    private String companyName;
    private LeadStatus status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public void setStatus(LeadStatus status) {
        this.status = status;
    }
}
