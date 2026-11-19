package com.rainbow.rlead.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * API 오류 응답 통일. 스택트레이스를 응답에 노출하지 않는다.
 * 응답 형식: {"message": "..."} (프론트엔드가 message 를 그대로 표시한다)
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(LeadNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(LeadNotFoundException e) {
        return body(HttpStatus.NOT_FOUND, "리드를 찾을 수 없습니다.");
    }

    @ExceptionHandler({IllegalArgumentException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> badRequest(Exception e) {
        return body(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> tooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "이미지 파일이 너무 큽니다. (최대 10MB)");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception e) {
        // 개인정보가 섞일 수 있어 상세 내용은 로그에 남기지 않고 종류만 남긴다.
        System.err.println("Unhandled error: " + e.getClass().getSimpleName());
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");
    }

    private ResponseEntity<Map<String, String>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("message", message == null ? "" : message));
    }
}
