package com.rainbow.rlead.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.net.URI;
import java.time.Duration;

/**
 * S3 스토리지 서비스.
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    @Value("${app.s3.endpoint}")
    private String s3Endpoint;

    /** 브라우저가 접근하는 저장소 주소 (Docker 에서는 내부 주소 minio:9000 과 다르다). 비면 endpoint 사용. */
    @Value("${app.s3.public-endpoint:}")
    private String s3PublicEndpoint;

    @Value("${app.s3.access-key}")
    private String accessKey;

    @Value("${app.s3.secret-key}")
    private String secretKey;

    @Value("${app.s3.bucket-name}")
    private String bucketName;

    @Value("${app.s3.region}")
    private String region;

    private S3Client getS3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .endpointOverride(URI.create(s3Endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .forcePathStyle(true)
                .build();
    }

    private S3Presigner getS3Presigner() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        // 서명에는 호스트가 포함되므로, 브라우저가 접근할 공개 주소로 직접 서명한다.
        String presignEndpoint = (s3PublicEndpoint == null || s3PublicEndpoint.isBlank()) ? s3Endpoint : s3PublicEndpoint;
        return S3Presigner.builder()
                .endpointOverride(URI.create(presignEndpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                // MinIO 등 S3 호환 저장소는 path-style(http://host/bucket/key) 이어야 한다.
                .serviceConfiguration(software.amazon.awssdk.services.s3.S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /**
     * 앱 시작 시 버킷이 없으면 만든다 (새 MinIO 에서 Spring 만 단독 실행해도 업로드가 되도록).
     * 저장소가 아직 준비되지 않았더라도 앱 기동은 막지 않는다.
     */
    @jakarta.annotation.PostConstruct
    void ensureBucketExists() {
        try (S3Client s3 = getS3Client()) {
            try {
                s3.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            } catch (NoSuchBucketException e) {
                s3.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                System.out.println("🪣 Created bucket: " + bucketName);
            }
        } catch (Exception e) {
            System.err.println("⚠️ Could not verify storage bucket: " + e.getClass().getSimpleName());
        }
    }

    /**
     * 이미지를 S3에 업로드합니다.
     */
    public void uploadImage(String key, byte[] imageData, String contentType) {
        try (S3Client s3 = getS3Client()) {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3.putObject(putRequest, RequestBody.fromBytes(imageData));
        }
    }

    /**
     * S3에서 이미지를 다운로드합니다.
     */
    public byte[] downloadImage(String key) {
        try (S3Client s3 = getS3Client()) {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            return s3.getObjectAsBytes(getRequest).asByteArray();
        }
    }

    /**
     * S3에서 이미지를 삭제합니다.
     */
    public void deleteImage(String key) {
        try (S3Client s3 = getS3Client()) {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            s3.deleteObject(deleteRequest);
        }
    }

    /**
     * Presigned URL을 생성합니다.
     */
    public String generatePresignedUrl(String key, int expirationSeconds) {
        try (S3Presigner presigner = getS3Presigner()) {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expirationSeconds))
                    .getObjectRequest(getRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        }
    }
}
