package com.rainbow.rlead.api;

/** 리드를 찾을 수 없을 때 (HTTP 404). */
public class LeadNotFoundException extends RuntimeException {
    public LeadNotFoundException(Long leadId) {
        super("Lead not found: " + leadId);
    }
}
