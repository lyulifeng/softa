package io.softa.starter.ai.service;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.ai.dto.AiUserMessage;
import io.softa.starter.ai.dto.TokenUsage;
import io.softa.starter.ai.entity.AiMessage;

/**
 * AiMessage Model Service Interface
 */
public interface AiMessageService extends EntityService<AiMessage, Long> {

    /**
     * Save user request message.
     *
     * @param aiUserMessage User request message
     * @return User request message
     */
    AiMessage saveUserMessage(AiUserMessage aiUserMessage);

    /**
     * Save AI response message for stream response.
     *
     * @param userMessage User message
     * @return AI message with initial status
     */
    AiMessage saveAiMessageForStream(AiMessage userMessage);

    /**
     * Update AI message after stream completion.
     *
     * @param aiMessageId   AI message ID
     * @param content       Final AI response content
     * @param usage          Token usage
     * @param conversationId Conversation ID (for the conversation-level token rollup)
     */
    void updateAiMessageAfterStream(Long aiMessageId, String content, TokenUsage usage, Long conversationId);

    /**
     * Save AI response message for non-streaming response.
     *
     * @param userMessage User message
     * @param answer      AI response content
     * @param usage       Token usage
     * @return AI response message
     */
    AiMessage saveAiMessageForNonStreaming(AiMessage userMessage, String answer, TokenUsage usage);

}
