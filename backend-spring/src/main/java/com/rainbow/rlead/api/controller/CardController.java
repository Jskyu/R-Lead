package com.rainbow.rlead.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainbow.rlead.api.dto.CardUploadRequest;
import com.rainbow.rlead.api.dto.CardUploadResponse;
import com.rainbow.rlead.service.CardUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 명함 업로드 API 컨트롤러.
 */
@RestController
@RequestMapping("/api/cards")
public class CardController {

    private final CardUploadService cardUploadService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CardController(CardUploadService cardUploadService) {
        this.cardUploadService = cardUploadService;
    }

    /**
     * 명함 사진을 업로드하고 처리 작업을 등록합니다.
     */
    @PostMapping("/upload")
    public ResponseEntity<CardUploadResponse> uploadCard(
            @RequestParam("image") MultipartFile image,
            @RequestParam("metadata") String metadata
    ) {
        try {
            CardUploadRequest request = objectMapper.readValue(metadata, CardUploadRequest.class);
            CardUploadResponse response = cardUploadService.uploadCard(image, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload card: " + e.getMessage(), e);
        }
    }

    /**
     * 여러 명함 사진을 한 번에 업로드하고 처리 작업을 등록합니다.
     */
    @PostMapping("/upload-batch")
    public ResponseEntity<Map<String, Object>> uploadCardsBatch(
            @RequestParam("images") MultipartFile[] images,
            @RequestParam("metadata") String metadata
    ) {
        try {
            Map<String, Object> baseMetadata = objectMapper.readValue(metadata, Map.class);
            Map<String, Object> response = cardUploadService.uploadCardsBatch(images, baseMetadata);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload cards: " + e.getMessage(), e);
        }
    }

    /**
     * API 헬스 체크.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "service", "cards"
        ));
    }
}
