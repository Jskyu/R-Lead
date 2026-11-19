package com.rainbow.rlead.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI가 추출한 명함 데이터.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractedData {

    private String name;
    private String company;
    private String department;

    @JsonProperty("job_title")
    private String jobTitle;

    private String email;
    private String phone;
    private String address;
    private String website;

    // 신뢰도 정보 (0~99)
    @JsonProperty("name_confidence")
    @Builder.Default
    private Integer nameConfidence = 0;

    @JsonProperty("company_confidence")
    @Builder.Default
    private Integer companyConfidence = 0;

    @JsonProperty("department_confidence")
    @Builder.Default
    private Integer departmentConfidence = 0;

    @JsonProperty("job_title_confidence")
    @Builder.Default
    private Integer jobTitleConfidence = 0;

    @JsonProperty("email_confidence")
    @Builder.Default
    private Integer emailConfidence = 0;

    @JsonProperty("phone_confidence")
    @Builder.Default
    private Integer phoneConfidence = 0;

    @JsonProperty("address_confidence")
    @Builder.Default
    private Integer addressConfidence = 0;

    @JsonProperty("website_confidence")
    @Builder.Default
    private Integer websiteConfidence = 0;
}
