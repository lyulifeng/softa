package io.softa.starter.ai.service;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.ai.dto.AiUserMessage;
import io.softa.starter.ai.dto.TokenUsage;
import io.softa.starter.ai.entity.AiConversation;

/**
 * AiConversation Model Service Interface
 */
public interface AiConversationService extends EntityService<AiConversation, Long> {

    /**
     * New conversation
     *
     * @param aiUserMessage AI User Message
     * @return Conversation ID
     */
    Long newConversation(AiUserMessage aiUserMessage);

    /**
     * Add a completed turn's token usage to the conversation-level rollup
     * ({@code inputTokens} / {@code outputTokens}). Called once per turn after the
     * assistant message is persisted, in the same transaction.
     *
     * @param conversationId Conversation ID
     * @param usage          The turn's token usage (null / empty is a no-op)
     */
    void addTokenUsage(Long conversationId, TokenUsage usage);

}