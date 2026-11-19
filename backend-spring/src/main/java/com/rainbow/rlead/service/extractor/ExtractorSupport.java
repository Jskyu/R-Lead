package com.rainbow.rlead.service.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainbow.rlead.domain.model.ExtractedData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

/**
 * AI 추출기 공통 로직: 프롬프트, 응답 파싱, 빈 이미지 검사.
 * (LLM 이 빈 이미지에도 그럴듯한 명함 정보를 지어내는 문제를 막기 위한 규칙 기반 방어선 포함)
 */
public final class ExtractorSupport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String PROMPT = """
            이 이미지에 있는 명함을 모두 찾아, 각 명함에 실제로 인쇄된 정보만 추출해주세요.
            이미지에는 명함이 한 장일 수도, 여러 장일 수도 있습니다.

            규칙:
            - 명함 한 장당 항목 하나입니다. 명함이 여러 장이면 위에서 아래로, 같은 줄이면 왼쪽에서 오른쪽 순서로 배열에 담으세요.
            - 같은 명함을 두 번 넣지 마세요. 다른 명함의 정보를 섞지 마세요.
            - 이미지에 명함이 없거나(빈 이미지, 풍경, 문서 등) 글자를 읽을 수 없으면 "cards"를 빈 배열 []로 반환하세요.
            - 이미지에 실제로 보이지 않는 값은 절대 추측하거나 만들어내지 말고 null로 반환하세요.
            - 예시나 가상의 데이터를 만들어내지 마세요.
            - 전화번호는 읽은 그대로 반환하되, 번호가 여러 개면 휴대폰 번호 하나만 "phone"에 넣으세요 (휴대폰이 없으면 대표 번호 하나). 번호를 쉼표로 이어 붙이지 마세요. 팩스(Fax/F) 번호는 phone에 넣지 마세요.

            JSON 형식으로만 응답하세요 (다른 텍스트 금지):
            {
                "cards": [
                    {
                        "name": "이름 (읽을 수 없으면 null)",
                        "company": "회사명 (읽을 수 없으면 null)",
                        "department": "부서 (읽을 수 없으면 null)",
                        "job_title": "직함 (읽을 수 없으면 null)",
                        "email": "이메일 (읽을 수 없으면 null)",
                        "phone": "전화번호 (읽을 수 없으면 null)",
                        "address": "주소 (읽을 수 없으면 null)",
                        "website": "웹사이트 (읽을 수 없으면 null)"
                    }
                ]
            }
            """;

    private ExtractorSupport() {
    }

    /**
     * LLM 응답에서 명함 여러 장을 읽는다. {"cards":[...]} 형식과 단일 객체 형식을 모두 받는다.
     * 읽을 수 있는 명함이 하나도 없으면 UnreadableCardException.
     */
    public static java.util.List<ExtractedData> parseAll(String response) throws Exception {
        JsonNode root = readJson(response);

        JsonNode cards = root.get("cards");
        if (cards == null && !root.isArray()) {
            // 이전 형식(명함 한 장짜리 객체)
            return java.util.List.of(parse(response));
        }

        java.util.List<ExtractedData> result = new java.util.ArrayList<>();
        for (JsonNode card : (cards != null ? cards : root)) {
            ExtractedData data = toData(card);
            if (data != null) {
                result.add(data);
            }
        }
        if (result.isEmpty()) {
            throw new UnreadableCardException("명함으로 인식되지 않는 이미지입니다. 다시 촬영해 주세요.");
        }
        return result;
    }

    private static JsonNode readJson(String response) throws Exception {
        if (response == null || response.isBlank()) {
            throw new RuntimeException("AI 응답이 비어 있습니다.");
        }
        String clean = response.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return MAPPER.readTree(clean.trim());
    }

    /** 모든 칸이 비어 있으면 null. */
    private static ExtractedData toData(JsonNode node) {
        ExtractedData data = ExtractedData.builder()
                .name(text(node, "name"))
                .company(text(node, "company"))
                .department(text(node, "department"))
                .jobTitle(text(node, "job_title"))
                .email(text(node, "email"))
                .phone(text(node, "phone"))
                .address(text(node, "address"))
                .website(text(node, "website"))
                .build();
        boolean empty = data.getName() == null && data.getCompany() == null && data.getEmail() == null
                && data.getPhone() == null && data.getDepartment() == null && data.getJobTitle() == null
                && data.getAddress() == null && data.getWebsite() == null;
        return empty ? null : data;
    }

    /** LLM 응답(JSON, 코드블록 허용)을 ExtractedData 로 변환한다. */
    public static ExtractedData parse(String response) throws Exception {
        if (response == null || response.isBlank()) {
            throw new RuntimeException("AI 응답이 비어 있습니다.");
        }
        String clean = response.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        } else if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }

        JsonNode root = MAPPER.readTree(clean.trim());

        JsonNode flag = root.get("is_business_card");
        if (flag != null && flag.isBoolean() && !flag.asBoolean()) {
            throw new UnreadableCardException("명함으로 인식되지 않는 이미지입니다. 다시 촬영해 주세요.");
        }

        ExtractedData data = ExtractedData.builder()
                .name(text(root, "name"))
                .company(text(root, "company"))
                .department(text(root, "department"))
                .jobTitle(text(root, "job_title"))
                .email(text(root, "email"))
                .phone(text(root, "phone"))
                .address(text(root, "address"))
                .website(text(root, "website"))
                .build();

        if (data.getName() == null && data.getCompany() == null && data.getEmail() == null
                && data.getPhone() == null && data.getDepartment() == null && data.getJobTitle() == null
                && data.getAddress() == null && data.getWebsite() == null) {
            throw new UnreadableCardException("명함에서 읽을 수 있는 정보가 없습니다. 다시 촬영해 주세요.");
        }
        return data;
    }

    private static String text(JsonNode node, String field) {
        JsonNode f = node.get(field);
        if (f == null || f.isNull()) {
            return null;
        }
        String v = f.asText();
        return (v == null || v.isBlank() || "null".equalsIgnoreCase(v.trim())) ? null : v.trim();
    }

    /**
     * 거의 단색(빈 종이, 렌즈 가림 등)인 이미지인지 검사한다. 밝기 표준편차가 매우 낮으면 true.
     */
    public static boolean isBlankImage(byte[] imageData) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageData));
            if (img == null) {
                return false;
            }
            int step = Math.max(1, Math.min(img.getWidth(), img.getHeight()) / 80);
            double sum = 0;
            double sumSq = 0;
            long n = 0;
            for (int y = 0; y < img.getHeight(); y += step) {
                for (int x = 0; x < img.getWidth(); x += step) {
                    int rgb = img.getRGB(x, y);
                    double lum = 0.299 * ((rgb >> 16) & 0xFF) + 0.587 * ((rgb >> 8) & 0xFF) + 0.114 * (rgb & 0xFF);
                    sum += lum;
                    sumSq += lum * lum;
                    n++;
                }
            }
            if (n == 0) {
                return false;
            }
            double mean = sum / n;
            double std = Math.sqrt(Math.max(0, sumSq / n - mean * mean));
            return std < 4.0;
        } catch (Exception e) {
            return false;
        }
    }
}
