package com.rainbow.rlead.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 명함 업로드 요청 DTO.
 */
@Data
public class CardUploadRequest {

    @JsonProperty("client_uuid")
    private String clientUuid;

    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("booth_number")
    private String boothNumber;

    @JsonProperty("interested_products")
    private List<String> interestedProducts;

    @JsonProperty("collected_by")
    private String collectedBy;

    @JsonProperty("marketing_consent")
    private Boolean marketingConsent;
}
