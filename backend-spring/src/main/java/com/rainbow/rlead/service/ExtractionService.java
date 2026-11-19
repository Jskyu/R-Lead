package com.rainbow.rlead.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainbow.rlead.domain.model.*;
import com.rainbow.rlead.domain.repository.LeadRepository;
import com.rainbow.rlead.domain.repository.ProcessingJobRepository;
import com.rainbow.rlead.service.extractor.CardExtractor;
import com.rainbow.rlead.service.extractor.ExtractorSupport;
import com.rainbow.rlead.service.extractor.UnreadableCardException;
import com.rainbow.rlead.service.extractor.ExtractorFactory;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;

/**
 * 명함 추출 서비스.
 */
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final ExtractorFactory extractorFactory;
    private final ValidationService validationService;
    private final DuplicateCheckerService duplicateChecker;
    private final StorageService storageService;
    private final LeadRepository leadRepository;
    private final ProcessingJobRepository jobRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 추출 작업을 처리합니다.
     */
    @Transactional
    public void processExtractionJob(ProcessingJob job) {
        try {
            // 작업 상태 업데이트
            job.setStatus(JobStatus.PROCESSING);
            job.setStartedAt(LocalDateTime.now());
            jobRepository.save(job);

            // 리드 조회
            Lead lead = leadRepository.findById(job.getLeadId())
                    .orElseThrow(() -> new RuntimeException("Lead not found: " + job.getLeadId()));

            if (lead.getCardImageKey() == null) {
                throw new RuntimeException("Card image key is missing");
            }

            // S3에서 원본 이미지 다운로드
            System.out.println("📥 Downloading image from S3: " + lead.getCardImageKey());
            byte[] originalImageData = storageService.downloadImage(lead.getCardImageKey());

            // AI 분석용으로 이미지 압축
            System.out.println("🔄 Compressing image for AI analysis...");
            byte[] compressedImageData = resizeImageForAI(originalImageData, 2048, 2048, 0.88f);

            double originalSize = originalImageData.length / 1024.0;
            double compressedSize = compressedImageData.length / 1024.0;
            System.out.printf("📊 Image size: %.1fKB → %.1fKB (%.1f%%)%n",
                    originalSize, compressedSize, (compressedSize / originalSize * 100));

            // 빈 이미지에 LLM 이 정보를 지어내는 것을 막는다 (AI 호출 전 규칙 검사)
            if (ExtractorSupport.isBlankImage(compressedImageData)) {
                throw new UnreadableCardException("빈 이미지입니다. 명함이 보이도록 다시 촬영해 주세요.");
            }

            // AI 추출 실행 (한 사진에 명함이 여러 장일 수 있다)
            System.out.println("🤖 Running AI extraction...");
            CardExtractor extractor = extractorFactory.getExtractor();
            List<ExtractedData> cards = extractor.extractAll(compressedImageData);
            System.out.println("🃏 Cards found in image: " + cards.size());

            if (lead.getCardIndex() != null) {
                // 이미 여러 장으로 나뉜 사진의 재추출: 이 리드가 맡은 순서의 명함만 사용한다 (리드가 불어나지 않도록)
                int index = lead.getCardIndex();
                if (index >= cards.size()) {
                    throw new UnreadableCardException("사진에서 " + (index + 1) + "번째 명함을 찾지 못했습니다.");
                }
                applyExtraction(lead, cards.get(index));
            } else {
                applyExtraction(lead, cards.get(0));

                // 명함이 여러 장이면 나머지는 같은 사진·수집 정보를 가진 새 리드로 만든다.
                if (cards.size() > 1) {
                    lead.setCardIndex(0);
                    leadRepository.save(lead);
                    for (int i = 1; i < cards.size(); i++) {
                        Lead extra = Lead.builder()
                                .status(LeadStatus.UPLOADED)
                                .cardIndex(i)
                                .eventName(lead.getEventName())
                                .boothNumber(lead.getBoothNumber())
                                .collectedBy(lead.getCollectedBy())
                                .collectedAt(lead.getCollectedAt())
                                .interestedProducts(lead.getInterestedProducts())
                                .marketingConsent(lead.getMarketingConsent())
                                .marketingConsentAt(lead.getMarketingConsentAt())
                                .cardImageUrl(lead.getCardImageUrl())
                                .cardImageKey(lead.getCardImageKey())
                                .imageDeleteAt(lead.getImageDeleteAt())
                                .build();
                        applyExtraction(extra, cards.get(i));
                        System.out.println("➕ Created lead " + extra.getId() + " for card #" + (i + 1) + " in the same photo");
                    }
                }
            }

            // 작업 완료
            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            System.out.println("✅ Extraction completed for lead " + lead.getId());

        } catch (Exception e) {
            // 작업 실패
            // 재시도 횟수가 남아 있으면 PENDING 으로 되돌려 다시 시도하고, 소진되면 FAILED 로 확정한다.
            // (Gemini 503 과부하 같은 일시 오류 대응. 재시도 간격은 JobProcessorService 가 조절)
            int attempts = job.getRetryCount() + 1;
            job.setRetryCount(attempts);
            job.setErrorMessage(e.getMessage());
            // 같은 이미지는 다시 보내도 결과가 같으므로(명함 아님/빈 이미지) 재시도하지 않는다.
            boolean retryable = !(e instanceof UnreadableCardException);
            job.setStatus(retryable && attempts < job.getMaxRetries() ? JobStatus.PENDING : JobStatus.FAILED);
            jobRepository.save(job);

            System.err.println("❌ Extraction failed: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * 추출된 명함 한 장을 검증하고 리드에 반영해 저장합니다.
     */
    private void applyExtraction(Lead lead, ExtractedData extractedData) {
        Map<String, String> extractedMap = extractedDataToMap(extractedData);
        List<ValidationIssue> validationIssues = validationService.validateExtractedData(
                extractedMap, List.of("name", "company"));

        if (!validationIssues.isEmpty()) {
            System.out.println("⚠️  Found " + validationIssues.size() + " validation issues");
            for (ValidationIssue issue : validationIssues) {
                System.out.println("  - " + issue.getField() + ": " + issue.getIssueType()); // 값(개인정보)은 로그에 남기지 않는다
            }
        }

        Map<String, Integer> confidences = validationService.calculateAllConfidences(extractedMap, validationIssues);
        validationService.setConfidences(extractedData, confidences);

        // 중복 리드 체크 (같은 사진의 형제 리드는 제외)
        Lead duplicateLead = duplicateChecker.checkDuplicate(
                extractedData.getEmail(),
                extractedData.getPhone(),
                extractedData.getCompany()
        );
        if (duplicateLead != null && !duplicateLead.getId().equals(lead.getId())
                && !(duplicateLead.getCardImageKey() != null
                && duplicateLead.getCardImageKey().equals(lead.getCardImageKey()))) {
            System.out.println("⚠️  Duplicate lead found: " + duplicateLead.getId());
            lead.setDuplicateOfLeadId(duplicateLead.getId());
        }

        lead.setName(extractedData.getName());
        lead.setCompany(extractedData.getCompany());
        lead.setDepartment(extractedData.getDepartment());
        lead.setJobTitle(extractedData.getJobTitle());
        lead.setEmail(extractedData.getEmail());
        lead.setPhone(extractedData.getPhone());
        lead.setAddress(extractedData.getAddress());
        lead.setWebsite(extractedData.getWebsite());

        lead.setAiExtractedData(extractedDataToJsonMap(extractedData));
        lead.setValidationIssues(validationIssuesToJsonList(validationIssues));
        lead.setFieldConfidences(confidences);
        lead.setStatus(LeadStatus.EXTRACTED);

        leadRepository.save(lead);
    }

    /**
     * AI 분석용으로 이미지 크기를 조정합니다.
     */
    private byte[] resizeImageForAI(byte[] imageData, int maxWidth, int maxHeight, float quality) throws Exception {
        ByteArrayInputStream bais = new ByteArrayInputStream(imageData);
        BufferedImage originalImage = ImageIO.read(bais);

        int width = originalImage.getWidth();
        int height = originalImage.getHeight();

        // 비율 계산
        double widthRatio = (double) maxWidth / width;
        double heightRatio = (double) maxHeight / height;
        double ratio = Math.min(widthRatio, heightRatio);

        // 이미 작은 이미지는 그대로 반환
        if (ratio >= 1.0) {
            return imageData;
        }

        int newWidth = (int) (width * ratio);
        int newHeight = (int) (height * ratio);

        // 이미지 리사이즈
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resizedImage.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g.dispose();

        // JPEG로 압축
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        javax.imageio.ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        javax.imageio.stream.ImageOutputStream ios = ImageIO.createImageOutputStream(baos);
        writer.setOutput(ios);

        javax.imageio.ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
        }

        writer.write(null, new javax.imageio.IIOImage(resizedImage, null, null), param);
        writer.dispose();
        ios.close();

        return baos.toByteArray();
    }

    private Map<String, String> extractedDataToMap(ExtractedData data) {
        Map<String, String> map = new HashMap<>();
        map.put("name", data.getName());
        map.put("company", data.getCompany());
        map.put("department", data.getDepartment());
        map.put("job_title", data.getJobTitle());
        map.put("email", data.getEmail());
        map.put("phone", data.getPhone());
        map.put("address", data.getAddress());
        map.put("website", data.getWebsite());
        return map;
    }

    private Map<String, Object> extractedDataToJsonMap(ExtractedData data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private List<Map<String, Object>> validationIssuesToJsonList(List<ValidationIssue> issues) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            Map<String, Object> map = new HashMap<>();
            map.put("field", issue.getField());
            map.put("issue_type", issue.getIssueType());
            map.put("message", issue.getMessage());
            list.add(map);
        }
        return list;
    }
}
