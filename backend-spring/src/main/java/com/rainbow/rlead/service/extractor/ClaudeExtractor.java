package com.rainbow.rlead.service.extractor;

import com.rainbow.rlead.domain.model.ExtractedData;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.model.Media;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;

import java.util.List;

/**
 * Anthropic Claude 추출기 (Spring AI 사용).
 */
@Component("claudeExtractor")
@ConditionalOnProperty(name = "spring.ai.anthropic.api-key")
public class ClaudeExtractor implements CardExtractor {

    private final ChatClient chatClient;

    public ClaudeExtractor(AnthropicChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @Override
    public List<ExtractedData> extractAll(byte[] imageData) throws Exception {
        String promptText = ExtractorSupport.PROMPT;

        // Create image media from byte array
        Media imageMedia = new Media(
                MimeTypeUtils.IMAGE_JPEG,
                new ByteArrayResource(imageData)
        );

        // Create user message with text and image
        UserMessage userMessage = new UserMessage(promptText, List.of(imageMedia));

        // Call Claude API using Spring AI
        String response = chatClient.prompt()
                .messages(userMessage)
                .call()
                .content();

        // Parse JSON response to ExtractedData
        return ExtractorSupport.parseAll(response);
    }

}
