package com.rainbow.rlead.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 리드 모델 - CRM 리드 정보.
 */
@Entity
@Table(name = "leads", indexes = {
    @Index(name = "idx_lead_name", columnList = "name"),
    @Index(name = "idx_lead_company", columnList = "company"),
    @Index(name = "idx_lead_email", columnList = "email"),
    @Index(name = "idx_lead_phone", columnList = "phone"),
    @Index(name = "idx_lead_status", columnList = "status"),
    @Index(name = "idx_lead_client_uuid", columnList = "client_uuid")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 명함 정보
    @Column(length = 255)
    private String name;

    @Column(length = 255)
    private String company;

    @Column(length = 255)
    private String department;

    @Column(name = "job_title", length = 255)
    @JsonProperty("job_title")
    private String jobTitle;

    @Column(length = 255)
    private String email;

    @Column(length = 50)
    private String phone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 500)
    private String website;

    // 관심 제품 (JSON array)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "interested_products", columnDefinition = "TEXT")
    @JsonProperty("interested_products")
    private List<String> interestedProducts;

    // 수집 출처
    @Column(name = "event_name", length = 255)
    @JsonProperty("event_name")
    private String eventName;

    @Column(name = "booth_number", length = 100)
    @JsonProperty("booth_number")
    private String boothNumber;

    @Column(name = "collected_by", length = 255)
    @JsonProperty("collected_by")
    private String collectedBy;

    @Column(name = "collected_at")
    @JsonProperty("collected_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime collectedAt;

    // 마케팅 수신 동의
    @Column(name = "marketing_consent", nullable = false)
    @JsonProperty("marketing_consent")
    @Builder.Default
    private Boolean marketingConsent = false;

    @Column(name = "marketing_consent_at")
    @JsonProperty("marketing_consent_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime marketingConsentAt;

    // 처리 상태
    @Enumerated(EnumType.STRING)
    @Column(length = 50, nullable = false)
    @Builder.Default
    private LeadStatus status = LeadStatus.UPLOADED;

    // AI 추출 결과 (JSONB - 원본)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_extracted_data", columnDefinition = "TEXT")
    @JsonProperty("ai_extracted_data")
    private Map<String, Object> aiExtractedData;

    // 검증 문제 목록 (ValidationIssue 배열)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_issues", columnDefinition = "TEXT")
    @JsonProperty("validation_issues")
    private List<Map<String, Object>> validationIssues;

    // 필드별 신뢰도 (0~99)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "field_confidences", columnDefinition = "TEXT")
    @JsonProperty("field_confidences")
    private Map<String, Integer> fieldConfidences;

    // 담당자가 수정한 필드 목록
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "modified_fields", columnDefinition = "TEXT")
    @JsonProperty("modified_fields")
    private List<String> modifiedFields;

    // 명함 사진
    @Column(name = "card_image_url", length = 1000)
    @JsonProperty("card_image_url")
    private String cardImageUrl;

    @Column(name = "card_image_key", length = 500)
    @JsonProperty("card_image_key")
    private String cardImageKey;

    @Column(name = "image_delete_at")
    @JsonProperty("image_delete_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime imageDeleteAt;

    // 한 사진에 명함이 여러 장일 때 몇 번째 명함인지 (0부터, 단일 명함이면 null)
    @Column(name = "card_index")
    @JsonProperty("card_index")
    private Integer cardIndex;

    // 중복 리드 참조
    @Column(name = "duplicate_of_lead_id")
    @JsonProperty("duplicate_of_lead_id")
    private Long duplicateOfLeadId;

    // CRM 정보
    @Column(name = "crm_id", length = 255)
    @JsonProperty("crm_id")
    private String crmId;

    @Column(name = "crm_registered_at")
    @JsonProperty("crm_registered_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime crmRegisteredAt;

    @Column(name = "crm_error_message", columnDefinition = "TEXT")
    @JsonProperty("crm_error_message")
    private String crmErrorMessage;

    // 클라이언트가 생성한 UUID (중복 방지)
    @Column(name = "client_uuid", length = 36, unique = true)
    @JsonProperty("client_uuid")
    private String clientUuid;

    // 타임스탬프
    @Column(name = "created_at", nullable = false, updatable = false)
    @JsonProperty("created_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
