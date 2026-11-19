package com.rainbow.rlead.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rainbow.rlead.domain.model.Lead;
import com.rainbow.rlead.domain.model.LeadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 리드 응답 DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeadResponse {

    private Long id;

    // 명함 정보
    private String name;
    private String company;
    private String department;

    @JsonProperty("job_title")
    private String jobTitle;

    private String email;
    private String phone;
    private String address;
    private String website;

    // 관심 제품
    @JsonProperty("interested_products")
    private List<String> interestedProducts;

    // 수집 출처
    @JsonProperty("event_name")
    private String eventName;

    @JsonProperty("booth_number")
    private String boothNumber;

    @JsonProperty("collected_by")
    private String collectedBy;

    @JsonProperty("collected_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime collectedAt;

    // 마케팅 수신 동의
    @JsonProperty("marketing_consent")
    private Boolean marketingConsent;

    @JsonProperty("marketing_consent_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime marketingConsentAt;

    // 처리 상태
    private LeadStatus status;

    // AI 추출 결과
    @JsonProperty("ai_extracted_data")
    private Map<String, Object> aiExtractedData;

    // 검증 문제
    @JsonProperty("validation_issues")
    private List<Map<String, Object>> validationIssues;

    // 필드별 신뢰도
    @JsonProperty("field_confidences")
    private Map<String, Integer> fieldConfidences;

    // 수정된 필드
    @JsonProperty("modified_fields")
    private List<String> modifiedFields;

    // 명함 사진
    @JsonProperty("card_image_url")
    private String cardImageUrl;

    @JsonProperty("card_image_key")
    private String cardImageKey;

    @JsonProperty("image_delete_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime imageDeleteAt;

    // 한 사진 속 명함 순서 (여러 장일 때만)
    @JsonProperty("card_index")
    private Integer cardIndex;

    // 중복 리드
    @JsonProperty("duplicate_of_lead_id")
    private Long duplicateOfLeadId;

    // CRM 정보
    @JsonProperty("crm_id")
    private String crmId;

    @JsonProperty("crm_registered_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime crmRegisteredAt;

    @JsonProperty("crm_error_message")
    private String crmErrorMessage;

    // UUID
    @JsonProperty("client_uuid")
    private String clientUuid;

    // 타임스탬프
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime updatedAt;

    public static LeadResponse from(Lead lead) {
        return LeadResponse.builder()
                .id(lead.getId())
                .name(lead.getName())
                .company(lead.getCompany())
                .department(lead.getDepartment())
                .jobTitle(lead.getJobTitle())
                .email(lead.getEmail())
                .phone(lead.getPhone())
                .address(lead.getAddress())
                .website(lead.getWebsite())
                .interestedProducts(lead.getInterestedProducts())
                .eventName(lead.getEventName())
                .boothNumber(lead.getBoothNumber())
                .collectedBy(lead.getCollectedBy())
                .collectedAt(lead.getCollectedAt())
                .marketingConsent(lead.getMarketingConsent())
                .marketingConsentAt(lead.getMarketingConsentAt())
                .status(lead.getStatus())
                .aiExtractedData(lead.getAiExtractedData())
                .validationIssues(lead.getValidationIssues())
                .fieldConfidences(lead.getFieldConfidences())
                .modifiedFields(lead.getModifiedFields())
                .cardImageUrl(lead.getCardImageUrl())
                .cardImageKey(lead.getCardImageKey())
                .imageDeleteAt(lead.getImageDeleteAt())
                .cardIndex(lead.getCardIndex())
                .duplicateOfLeadId(lead.getDuplicateOfLeadId())
                .crmId(lead.getCrmId())
                .crmRegisteredAt(lead.getCrmRegisteredAt())
                .crmErrorMessage(lead.getCrmErrorMessage())
                .clientUuid(lead.getClientUuid())
                .createdAt(lead.getCreatedAt())
                .updatedAt(lead.getUpdatedAt())
                .build();
    }
}
