package com.rainbow.rlead.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.rainbow.rlead.domain.model.ExtractedData;
import com.rainbow.rlead.domain.model.ValidationIssue;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 데이터 검증 서비스.
 */
@Service
public class ValidationService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    private final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();

    /**
     * 이메일 주소를 검증합니다.
     */
    public ValidationIssue validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return ValidationIssue.builder()
                    .field("email")
                    .issueType("empty")
                    .message("이메일 주소가 비어 있습니다.")
                    .build();
        }

        String trimmedEmail = email.trim();
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            return ValidationIssue.builder()
                    .field("email")
                    .issueType("invalid_format")
                    .message("이메일 형식이 올바르지 않습니다: " + email)
                    .build();
        }

        return null;
    }

    /**
     * 전화번호를 검증합니다.
     */
    public ValidationIssue validatePhone(String phone, String region) {
        if (phone == null || phone.trim().isEmpty()) {
            return ValidationIssue.builder()
                    .field("phone")
                    .issueType("empty")
                    .message("전화번호가 비어 있습니다.")
                    .build();
        }

        try {
            Phonenumber.PhoneNumber parsed = phoneNumberUtil.parse(phone, region);

            if (!phoneNumberUtil.isValidNumber(parsed)) {
                return ValidationIssue.builder()
                        .field("phone")
                        .issueType("invalid_format")
                        .message("전화번호 형식이 올바르지 않습니다: " + phone)
                        .build();
            }

            if (!phoneNumberUtil.isPossibleNumber(parsed)) {
                return ValidationIssue.builder()
                        .field("phone")
                        .issueType("invalid_format")
                        .message("전화번호가 불가능한 형식입니다: " + phone)
                        .build();
            }

            return null;

        } catch (NumberParseException e) {
            return ValidationIssue.builder()
                    .field("phone")
                    .issueType("invalid_format")
                    .message("전화번호를 해석할 수 없습니다: " + phone)
                    .build();
        }
    }

    /**
     * 필수 필드를 검증합니다.
     */
    public ValidationIssue validateRequiredField(String fieldName, String value, int minLength) {
        if (value == null || value.trim().length() < minLength) {
            return ValidationIssue.builder()
                    .field(fieldName)
                    .issueType("empty")
                    .message(fieldName + " 필드가 비어 있습니다.")
                    .build();
        }
        return null;
    }

    /**
     * 필드의 신뢰도를 계산합니다 (규칙 기반).
     *
     * 신뢰도 점수 기준:
     * - 0: 빈 값
     * - 30: 형식 오류
     * - 70: 형식 검증 통과 (필수 아닌 필드)
     * - 90: 명확한 추출 (필수 필드이며 형식 검증 통과)
     */
    public int calculateFieldConfidence(String fieldName, String value, ValidationIssue validationIssue) {
        // 빈 값
        if (value == null || value.trim().isEmpty()) {
            return 0;
        }

        // 검증 문제가 있으면
        if (validationIssue != null) {
            if ("empty".equals(validationIssue.getIssueType())) {
                return 0;
            } else if ("invalid_format".equals(validationIssue.getIssueType()) ||
                       "missing_in_original".equals(validationIssue.getIssueType())) {
                return 30;
            }
        }

        // 필수 필드 (name, company, email, phone)는 90점
        Set<String> essentialFields = Set.of("name", "company", "email", "phone");
        if (essentialFields.contains(fieldName)) {
            return 90;
        }

        // 선택 필드는 70점
        return 70;
    }

    /**
     * 추출된 데이터를 검증합니다.
     */
    public List<ValidationIssue> validateExtractedData(Map<String, String> extracted, List<String> requiredFields) {
        if (requiredFields == null) {
            requiredFields = List.of("name", "company");
        }

        List<ValidationIssue> issues = new ArrayList<>();

        // 필수 필드 검증
        for (String field : requiredFields) {
            String value = extracted.get(field);
            ValidationIssue issue = validateRequiredField(field, value, 1);
            if (issue != null) {
                issues.add(issue);
            }
        }

        // 이메일 검증
        String email = extracted.get("email");
        if (email != null && !email.trim().isEmpty()) {
            ValidationIssue issue = validateEmail(email);
            if (issue != null) {
                issues.add(issue);
            }
        }

        // 전화번호 검증
        String phone = extracted.get("phone");
        if (phone != null && !phone.trim().isEmpty()) {
            ValidationIssue issue = validatePhone(phone, "KR");
            if (issue != null) {
                issues.add(issue);
            }
        }

        return issues;
    }

    /**
     * 모든 필드의 신뢰도를 계산합니다.
     */
    public Map<String, Integer> calculateAllConfidences(Map<String, String> extracted, List<ValidationIssue> issues) {
        // 필드별로 issues를 매핑
        Map<String, ValidationIssue> fieldIssues = new HashMap<>();
        for (ValidationIssue issue : issues) {
            fieldIssues.put(issue.getField(), issue);
        }

        Map<String, Integer> confidences = new HashMap<>();
        List<String> fields = List.of("name", "company", "department", "job_title", "email", "phone", "address", "website");

        for (String field : fields) {
            String value = extracted.get(field);
            ValidationIssue issue = fieldIssues.get(field);
            int confidence = calculateFieldConfidence(field, value, issue);
            confidences.put(field + "_confidence", confidence);
        }

        return confidences;
    }

    /**
     * ExtractedData 객체에 신뢰도를 설정합니다.
     */
    public void setConfidences(ExtractedData data, Map<String, Integer> confidences) {
        data.setNameConfidence(confidences.getOrDefault("name_confidence", 0));
        data.setCompanyConfidence(confidences.getOrDefault("company_confidence", 0));
        data.setDepartmentConfidence(confidences.getOrDefault("department_confidence", 0));
        data.setJobTitleConfidence(confidences.getOrDefault("job_title_confidence", 0));
        data.setEmailConfidence(confidences.getOrDefault("email_confidence", 0));
        data.setPhoneConfidence(confidences.getOrDefault("phone_confidence", 0));
        data.setAddressConfidence(confidences.getOrDefault("address_confidence", 0));
        data.setWebsiteConfidence(confidences.getOrDefault("website_confidence", 0));
    }
}
