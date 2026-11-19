package com.rainbow.rlead.service.extractor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 추출기 팩토리.
 */
@Component
public class ExtractorFactory {

    @Value("${app.ai.provider}")
    private String aiProvider;

    @Autowired
    private ApplicationContext context;

    /**
     * 설정에 따라 적절한 추출기를 반환합니다.
     */
    public CardExtractor getExtractor() {
        return switch (aiProvider.toLowerCase()) {
            case "mock" -> context.getBean("mockExtractor", CardExtractor.class);
            case "gemini" -> context.getBean("geminiExtractor", CardExtractor.class);
            case "openai" -> context.getBean("openaiExtractor", CardExtractor.class);
            case "claude" -> context.getBean("claudeExtractor", CardExtractor.class);
            default -> throw new IllegalArgumentException("Unknown AI provider: " + aiProvider);
        };
    }
}
