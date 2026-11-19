package com.rainbow.rlead.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 검증 문제점.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue {

    /**
     * 문제가 있는 필드명.
     */
    private String field;

    /**
     * 문제 유형 (empty, invalid_format, missing_in_original).
     */
    private String issueType;

    /**
     * 사람이 읽을 수 있는 설명.
     */
    private String message;
}
