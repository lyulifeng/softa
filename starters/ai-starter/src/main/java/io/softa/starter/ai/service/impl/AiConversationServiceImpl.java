package io.softa.starter.ai.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.ai.dto.AiUserMessage;
import io.softa.starter.ai.dto.TokenUsage;
import io.softa.starter.ai.entity.AiConversation;
import io.softa.starter.ai.service.AiConversationService;

/**
 * AiConversation Model Service Implementation
 */
@Service
public class AiConversationServiceImpl extends EntityServiceImpl<AiConversation, Long> implements AiConversationService {

    /**
     * New conversation
     *
     * @param aiUserMessage AI User Message
     * @return Conversation ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long newConversation(AiUserMessage aiUserMessage) {
        AiConversation conversation = new AiConversation();
        String content = aiUserMessage.getContent();
        conversation.setTitle(content.length() > 10 ? content.substring(0, 10) : content);
        conversation.setRobotId(aiUserMessage.getRobotId());
        return this.createOne(conversation);
    }

    /**
     * Increment the conversation-level token rollup by one turn's usage.
     * <p>
     * Read-modify-write is sound here because turns within a single conversation are
     * strictly sequential (the user awaits each response before sending the next), so
     * there is no concurrent writer on the same conversation row; {@code updateOne}
     * ignores null fields, touching only the two counters.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addTokenUsage(Long conversationId, TokenUsage usage) {
        if (conversationId == null || usage == null) {
            return;
        }
        AiConversation conversation = this.getById(conversationId).orElse(null);
        if (conversation == null) {
            return;
        }
        int currentInput = conversation.getInputTokens() == null ? 0 : conversation.getInputTokens();
        int currentOutput = conversation.getOutputTokens() == null ? 0 : conversation.getOutputTokens();
        AiConversation update = new AiConversation();
        update.setId(conversationId);
        update.setInputTokens(currentInput + usage.promptTokens());
        update.setOutputTokens(currentOutput + usage.completionTokens());
        this.updateOne(update);
    }

}