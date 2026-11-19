package com.rainbow.rlead.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 리드 수정 요청 DTO.
 */
@Data
public class LeadUpdateRequest {

    private String name;
    private String company;
    private String department;

    @JsonProperty("job_title")
    private String jobTitle;

    private String email;
    private String phone;
    private String address;
    private String website;

    @JsonProperty("interested_products")
    private List<String> interestedProducts;

    @JsonProperty("marketing_consent")
    private Boolean marketingConsent;
}
