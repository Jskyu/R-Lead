package com.rainbow.rlead.service.extractor;

import com.rainbow.rlead.domain.model.ExtractedData;
import org.springframework.stereotype.Component;

/**
 * Mock 추출기 (테스트용).
 */
@Component("mockExtractor")
public class MockExtractor implements CardExtractor {

    @Override
    public ExtractedData extract(byte[] imageData) {
        System.out.println("🧪 Using Mock extractor (for testing)");

        return ExtractedData.builder()
                .name("홍길동")
                .company("테스트 주식회사")
                .department("기술영업팀")
                .jobTitle("부장")
                .email("hong@test.co.kr")
                .phone("010-1234-5678")
                .address("서울시 강남구 테헤란로 123")
                .website("https://test.co.kr")
                .nameConfidence(90)
                .companyConfidence(90)
                .departmentConfidence(70)
                .jobTitleConfidence(70)
                .emailConfidence(90)
                .phoneConfidence(90)
                .addressConfidence(70)
                .websiteConfidence(70)
                .build();
    }
}
