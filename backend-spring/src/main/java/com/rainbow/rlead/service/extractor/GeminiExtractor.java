package com.rainbow.rlead.service.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rainbow.rlead.domain.model.ExtractedData;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

/**
 * Google Gemini 추출기 (Spring AI 사용).
 *
 * Gemini의 OpenAI 호환 엔드포인트를 Spring AI OpenAI 모델로 호출한다.
 * AI Studio API 키(GEMINI_API_KEY)만 있으면 되며, Vertex AI(GCP 프로젝트)는 필요 없다.
 * 진짜 OpenAI용 자동 구성 빈(OpenAiChatModel)과 충돌하지 않도록 빈으로 등록하지 않고 내부에서만 생성한다.
 */
@Component("geminiExtractor")
public class GeminiExtractor implements CardExtractor {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com/v1beta/openai";
    private static final String COMPLETIONS_PATH = "/chat/completions";

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile ChatClient chatClient;

    public GeminiExtractor(
            @Value("${GEMINI_API_KEY:}") String apiKey,
            @Value("${app.ai.gemini.model:gemini-flash-latest}") String model
    ) {
        this.apiKey = apiKey;
        this.model = model;
    }

    /** API 키가 없어도 앱이 기동되도록 최초 사용 시점에 생성한다. */
    private ChatClient chatClient() {
        if (chatClient == null) {
            synchronized (this) {
                if (chatClient == null) {
                    if (apiKey == null || apiKey.trim().isEmpty()) {
                        throw new IllegalStateException("GEMINI_API_KEY is not set");
                    }
                    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                    requestFactory.setConnectTimeout(30_000);
                    requestFactory.setReadTimeout(120_000);

                    OpenAiApi api = new OpenAiApi(
                            BASE_URL,
                            apiKey,
                            COMPLETIONS_PATH,
                            "/embeddings",
                            RestClient.builder().requestFactory(requestFactory),
                            WebClient.builder(),
                            RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);

                    // Spring AI 기본 재시도(10회, 최대 3분)는 작업 큐 전체를 막을 수 있어 짧게 제한한다.
                    // 작업 단위 재시도는 ExtractionService/JobProcessorService 가 담당한다.
                    org.springframework.retry.support.RetryTemplate retry = org.springframework.retry.support.RetryTemplate.builder()
                            .maxAttempts(2)
                            .exponentialBackoff(java.time.Duration.ofSeconds(2), 2, java.time.Duration.ofSeconds(5))
                            .retryOn(org.springframework.ai.retry.TransientAiException.class)
                            .build();

                    OpenAiChatModel chatModel = new OpenAiChatModel(api, OpenAiChatOptions.builder()
                            .withModel(model)
                            .withTemperature(0.1)
                            .build(), null, retry);
                    chatClient = ChatClient.builder(chatModel).build();
                }
            }
        }
        return chatClient;
    }

    @Override
    public List<ExtractedData> extractAll(byte[] imageData) throws Exception {
        String promptText = ExtractorSupport.PROMPT;

        Media imageMedia = new Media(MimeTypeUtils.IMAGE_JPEG, new ByteArrayResource(imageData));
        UserMessage userMessage = new UserMessage(promptText, List.of(imageMedia));

        String response;
        try {
            response = chatClient().prompt()
                    .messages(userMessage)
                    .call()
                    .content();
        } catch (org.springframework.web.client.RestClientException e) {
            // Gemini 오류 응답(예: 503 과부하)은 본문이 JSON 배열이라 Spring AI가 역직렬화에 실패하며
            // "Error while extracting response" 로 나타난다. 원인을 알아보기 쉽게 안내한다.
            throw new RuntimeException("Gemini extraction failed: Gemini API 응답 오류(일시 과부하 503 등 가능). "
                    + "잠시 후 재시도하거나 GEMINI_MODEL 을 바꿔 보세요. 원인: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("Gemini extraction failed: "
                    + (e.getMessage() != null ? e.getMessage() : e.toString()), e);
        }

        if (response == null || response.trim().isEmpty()) {
            throw new RuntimeException("Gemini extraction failed: empty response");
        }
        return ExtractorSupport.parseAll(response);
    }

}
