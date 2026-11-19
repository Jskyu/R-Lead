package com.rainbow.rlead.service;

import com.rainbow.rlead.domain.model.Lead;
import com.rainbow.rlead.domain.repository.LeadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 중복 리드 확인 서비스.
 */
@Service
@RequiredArgsConstructor
public class DuplicateCheckerService {

    private final LeadRepository leadRepository;

    /**
     * 문자열을 정규화합니다 (공백 제거, 소문자).
     */
    private String normalizeString(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.trim().toLowerCase();
    }

    /**
     * 두 문자열의 유사도를 계산합니다 (0.0 ~ 1.0).
     * Levenshtein Distance를 사용한 유사도 계산.
     */
    private double calculateSimilarity(String str1, String str2) {
        if (str1 == null || str1.isEmpty() || str2 == null || str2.isEmpty()) {
            return 0.0;
        }

        String s1 = normalizeString(str1);
        String s2 = normalizeString(str2);

        int maxLength = Math.max(s1.length(), s2.length());
        if (maxLength == 0) {
            return 1.0;
        }

        int distance = levenshteinDistance(s1, s2);
        return 1.0 - (double) distance / maxLength;
    }

    /**
     * Levenshtein Distance 계산.
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                            Math.min(dp[i - 1][j], dp[i][j - 1]),
                            dp[i - 1][j - 1]
                    ) + 1;
                }
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * 중복 리드를 찾습니다.
     *
     * @param email   이메일 주소
     * @param phone   전화번호
     * @param company 회사명
     * @return 중복 가능성이 있는 리드 목록
     */
    public List<Lead> findDuplicates(String email, String phone, String company) {
        List<Lead> duplicates = new ArrayList<>();

        // 1. 이메일 완전 일치 (가장 확실한 중복)
        if (email != null && !email.trim().isEmpty()) {
            List<Lead> emailMatches = leadRepository.findByEmail(email.trim());
            duplicates.addAll(emailMatches);
        }

        // 2. 전화번호 일치
        if (duplicates.isEmpty() && phone != null && !phone.trim().isEmpty()) {
            List<Lead> phoneMatches = leadRepository.findByPhone(phone.trim());
            duplicates.addAll(phoneMatches);
        }

        // 3. 회사명 유사도 검사 (이메일, 전화번호가 없을 때)
        if (duplicates.isEmpty() && company != null && !company.trim().isEmpty()) {
            List<Lead> companyLeads = leadRepository.findByCompany(company.trim());

            // 유사도 계산
            for (Lead lead : companyLeads) {
                if (lead.getCompany() != null) {
                    double similarity = calculateSimilarity(company, lead.getCompany());
                    // 유사도 80% 이상이면 중복 가능성
                    if (similarity >= 0.8) {
                        duplicates.add(lead);
                    }
                }
            }
        }

        return duplicates;
    }

    /**
     * 중복 여부를 확인합니다.
     *
     * @param email   이메일 주소
     * @param phone   전화번호
     * @param company 회사명
     * @return 중복 리드 (없으면 null)
     */
    public Lead checkDuplicate(String email, String phone, String company) {
        List<Lead> duplicates = findDuplicates(email, phone, company);

        if (!duplicates.isEmpty()) {
            // 가장 최근 리드 반환
            return duplicates.get(0);
        }

        return null;
    }
}
