package com.rainbow.rlead.service;

import com.rainbow.rlead.api.dto.CardUploadRequest;
import com.rainbow.rlead.api.dto.CardUploadResponse;
import com.rainbow.rlead.domain.model.*;
import com.rainbow.rlead.domain.repository.LeadRepository;
import com.rainbow.rlead.domain.repository.ProcessingJobRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 명함 업로드 서비스.
 */
@Service
@RequiredArgsConstructor
public class CardUploadService {

    private final LeadRepository leadRepository;
    private final ProcessingJobRepository jobRepository;
    private final StorageService storageService;

    @Value("${app.image.retention-days}")
    private int retentionDays;

    /**
     * 단일 명함 업로드.
     */
    @Transactional
    public CardUploadResponse uploadCard(MultipartFile image, CardUploadRequest request) {
        // 이미지 파일만 허용 (HEIC 등 image/* 포함)
        String contentType = image.getContentType();
        if (image.isEmpty()) {
            throw new IllegalArgumentException("빈 이미지 파일입니다.");
        }
        if (contentType == null || !contentType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        try {
            // 중복 체크 (client_uuid 가 있을 때만)
            Optional<Lead> existingLead = request.getClientUuid() == null
                    ? Optional.empty()
                    : leadRepository.findByClientUuid(request.getClientUuid());
            if (existingLead.isPresent()) {
                Lead lead = existingLead.get();
                return CardUploadResponse.builder()
                        .leadId(lead.getId())
                        .message("이미 업로드된 명함입니다.")
                        .duplicate(true)
                        .duplicateOfLeadId(lead.getId())
                        .build();
            }

            // 이미지 읽기
            byte[] imageData = image.getBytes();
            if (imageData.length == 0) {
                throw new RuntimeException("Empty image file");
            }

            // S3에 업로드
            String s3Key = "cards/" + UUID.randomUUID() + ".jpg";
            storageService.uploadImage(s3Key, imageData, image.getContentType());
            String s3Url = storageService.generatePresignedUrl(s3Key, 3600);

            // 삭제 예정일 계산
            LocalDateTime deleteDate = LocalDateTime.now().plusDays(retentionDays);

            // Lead 생성
            Lead lead = Lead.builder()
                    .status(LeadStatus.UPLOADED)
                    .eventName(request.getEventName())
                    .boothNumber(request.getBoothNumber())
                    .collectedBy(request.getCollectedBy())
                    .interestedProducts(request.getInterestedProducts())
                    .marketingConsent(request.getMarketingConsent() != null ? request.getMarketingConsent() : false)
                    .marketingConsentAt(Boolean.TRUE.equals(request.getMarketingConsent()) ? LocalDateTime.now() : null)
                    .cardImageUrl(s3Url)
                    .cardImageKey(s3Key)
                    .imageDeleteAt(deleteDate)
                    .clientUuid(request.getClientUuid())
                    .collectedAt(LocalDateTime.now())
                    .build();

            lead = leadRepository.save(lead);

            // 백그라운드 작업 등록 (AI 추출)
            ProcessingJob job = ProcessingJob.builder()
                    .jobType(JobType.EXTRACT_CARD)
                    .leadId(lead.getId())
                    .build();
            jobRepository.save(job);

            return CardUploadResponse.builder()
                    .leadId(lead.getId())
                    .message("명함이 업로드되었습니다. AI 추출을 시작합니다.")
                    .duplicate(false)
                    .build();

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload card: " + e.getMessage(), e);
        }
    }

    /**
     * 다중 명함 업로드.
     */
    @Transactional
    public Map<String, Object> uploadCardsBatch(MultipartFile[] images, Map<String, Object> baseMetadata) {
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;

        for (int index = 0; index < images.length; index++) {
            MultipartFile image = images[index];
            Map<String, Object> result = new HashMap<>();
            result.put("index", index);

            try {
                // 각 이미지마다 고유한 UUID 생성
                String clientUuid = UUID.randomUUID().toString();

                CardUploadRequest request = new CardUploadRequest();
                request.setClientUuid(clientUuid);
                request.setEventName((String) baseMetadata.get("event_name"));
                request.setBoothNumber((String) baseMetadata.get("booth_number"));
                request.setCollectedBy((String) baseMetadata.get("collected_by"));
                request.setInterestedProducts((List<String>) baseMetadata.get("interested_products"));
                request.setMarketingConsent((Boolean) baseMetadata.get("marketing_consent"));

                // 이미지 업로드
                CardUploadResponse uploadResponse = uploadCard(image, request);

                result.put("success", true);
                result.put("lead_id", uploadResponse.getLeadId());
                result.put("message", "업로드 성공");
                result.put("duplicate", false);
                successCount++;

            } catch (Exception e) {
                result.put("success", false);
                result.put("message", "업로드 실패: " + e.getMessage());
            }

            results.add(result);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("total", images.length);
        response.put("success_count", successCount);
        response.put("results", results);

        return response;
    }
}
