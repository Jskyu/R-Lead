package com.rainbow.rlead.domain.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 리드 처리 상태.
 * API 에서는 FastAPI 백엔드와 동일하게 소문자 값(uploaded, extracted ...)을 사용하고,
 * DB 에는 enum 이름(대문자)으로 저장한다.
 */
public enum LeadStatus {
    UPLOADED,       // 업로드됨
    EXTRACTED,      // AI 추출됨
    REVIEWED,       // 담당자 확인됨
    APPROVED,       // 승인됨
    REJECTED,       // 거부됨
    CRM_REGISTERED, // CRM 등록됨
    CRM_FAILED;     // CRM 등록 실패

    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    /** 대소문자 구분 없이 파싱한다. */
    @JsonCreator
    public static LeadStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LeadStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown status: " + value);
        }
    }
}
