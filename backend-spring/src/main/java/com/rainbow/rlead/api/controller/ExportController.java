package com.rainbow.rlead.api.controller;

import com.rainbow.rlead.domain.model.Lead;
import com.rainbow.rlead.domain.model.LeadStatus;
import com.rainbow.rlead.domain.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV 내보내기 및 대시보드 통계 API (FastAPI export.py 와 동일한 응답 형식).
 */
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LeadRepository leadRepository;

    @GetMapping("/csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) LeadStatus status) {
        List<Lead> leads = status != null
                ? leadRepository.findByStatusOrderByCreatedAtDesc(status)
                : leadRepository.findAllByOrderByCreatedAtDesc();

        StringBuilder sb = new StringBuilder();
        sb.append(row("ID", "이름", "회사", "부서", "직함", "이메일", "전화번호", "주소", "웹사이트",
                "관심 제품", "행사명", "부스", "수집자", "수집 일시", "마케팅 동의", "상태", "생성 일시"));
        for (Lead l : leads) {
            sb.append(row(
                    String.valueOf(l.getId()), l.getName(), l.getCompany(), l.getDepartment(), l.getJobTitle(),
                    l.getEmail(), l.getPhone(), l.getAddress(), l.getWebsite(),
                    l.getInterestedProducts() == null ? "" : String.join(", ", l.getInterestedProducts()),
                    l.getEventName(), l.getBoothNumber(), l.getCollectedBy(),
                    l.getCollectedAt() == null ? "" : l.getCollectedAt().format(DT),
                    Boolean.TRUE.equals(l.getMarketingConsent()) ? "예" : "아니오",
                    l.getStatus().toValue(),
                    l.getCreatedAt() == null ? "" : l.getCreatedAt().format(DT)));
        }

        // UTF-8 BOM: Excel 한글 호환
        byte[] body = ("﻿" + sb).getBytes(StandardCharsets.UTF_8);
        String filename = "leads_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<Lead> all = leadRepository.findAll();

        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        for (LeadStatus s : LeadStatus.values()) {
            statusCounts.put(s.toValue(), 0);
        }
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.with(DayOfWeek.MONDAY);
        int todayLeads = 0;
        int weekLeads = 0;
        for (Lead l : all) {
            statusCounts.merge(l.getStatus().toValue(), 1, Integer::sum);
            if (l.getCollectedAt() != null) {
                LocalDate d = l.getCollectedAt().toLocalDate();
                if (d.equals(today)) todayLeads++;
                if (!d.isBefore(weekStart)) weekLeads++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total_leads", all.size());
        result.put("status_counts", statusCounts);
        result.put("today_leads", todayLeads);
        result.put("this_week_leads", weekLeads);
        result.put("pending_review", statusCounts.get(LeadStatus.EXTRACTED.toValue()));
        return result;
    }

    private static String row(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells[i]));
        }
        return sb.append("\r\n").toString();
    }

    /** CSV 이스케이프 + 수식 주입(=,+,-,@) 방지. */
    private static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0 && !s.matches("^[+-]?[0-9][0-9\\-\\s()]*$")) {
            s = "'" + s;
        }
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
