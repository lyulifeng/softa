package io.softa.starter.ai.service.impl;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.softa.framework.base.utils.LambdaUtils;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.ai.dto.AiUserMessage;
import io.softa.starter.ai.dto.TokenUsage;
import io.softa.starter.ai.entity.AiMessage;
import io.softa.starter.ai.enums.AiMessageRole;
import io.softa.starter.ai.enums.AiMessageStatus;
import io.softa.starter.ai.service.AiConversationService;
import io.softa.starter.ai.service.AiMessageService;

/**
 * AiMessage Model Service Implementation
 */
@Service
public class AiMessageServiceImpl extends EntityServiceImpl<AiMessage, Long> implements AiMessageService {

    @Autowired
    private AiConversationService conversationService;

    /**
     * Save user request message.
     *
     * @param aiUserMessage User request message
     * @return User request message
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMessage saveUserMessage(AiUserMessage aiUserMessage) {
        AiMessage aiMessage = new AiMessage();
        aiMessage.setRole(AiMessageRole.USER);
        aiMessage.setRobotId(aiUserMessage.getRobotId());
        aiMessage.setConversationId(aiUserMessage.getConversationId());
        aiMessage.setParentId(aiUserMessage.getParentId());
        aiMessage.setContent(aiUserMessage.getContent());
        aiMessage.setStatus(AiMessageStatus.COMPLETED);
        aiMessage.setId(this.createOne(aiMessage));
        return aiMessage;
    }

    /**
     * Save AI response message for non-streaming response.
     *
     * @param userMessage User message
     * @param answer      AI response content
     * @param usage       Token usage
     * @return AI response message
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMessage saveAiMessageForNonStreaming(AiMessage userMessage, String answer, TokenUsage usage) {
        AiMessage aiMessage = new AiMessage();
        aiMessage.setRole(AiMessageRole.ASSISTANT);
        aiMessage.setRobotId(userMessage.getRobotId());
        aiMessage.setConversationId(userMessage.getConversationId());
        aiMessage.setParentId(userMessage.getId());
        aiMessage.setContent(answer);
        applyUsage(aiMessage, usage);
        aiMessage.setStream(false);
        aiMessage.setStatus(AiMessageStatus.COMPLETED);
        aiMessage.setId(this.createOne(aiMessage));
        // Roll the turn's usage up to the conversation
        conversationService.addTokenUsage(userMessage.getConversationId(), usage);
        return aiMessage;
    }

    /**
     * Set the turn's input/output token usage on the assistant message. The provider
     * reports the whole turn's usage on the response, so both counts live on this row
     * (user-role rows carry no usage). A null usage degrades to 0.
     *
     * @param aiMessage Assistant message
     * @param usage     Token usage
     */
    private void applyUsage(AiMessage aiMessage, TokenUsage usage) {
        aiMessage.setInputTokens(usage == null ? 0 : usage.promptTokens());
        aiMessage.setOutputTokens(usage == null ? 0 : usage.completionTokens());
    }

    /**
     * Save AI response message for stream response.
     *
     * @param userMessage User message
     * @return AI message with initial status
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiMessage saveAiMessageForStream(AiMessage userMessage) {
        AiMessage aiMessage = new AiMessage();
        aiMessage.setRole(AiMessageRole.ASSISTANT);
        aiMessage.setRobotId(userMessage.getRobotId());
        aiMessage.setConversationId(userMessage.getConversationId());
        aiMessage.setParentId(userMessage.getId());
        aiMessage.setStream(true);
        aiMessage.setStatus(AiMessageStatus.PENDING);
        aiMessage.setId(this.createOne(aiMessage));
        return aiMessage;
    }

    /**
     * Update AI message after stream completion.
     *
     * @param aiMessageId   AI message ID
     * @param content       Final AI response content
     * @param usage         Token usage
     * @param conversationId Conversation ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAiMessageAfterStream(Long aiMessageId, String content, TokenUsage usage, Long conversationId) {
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put(ModelConstant.ID, aiMessageId);
        updateMap.put(LambdaUtils.getAttributeName(AiMessage::getContent), content);
        updateMap.put(LambdaUtils.getAttributeName(AiMessage::getInputTokens), usage == null ? 0 : usage.promptTokens());
        updateMap.put(LambdaUtils.getAttributeName(AiMessage::getOutputTokens), usage == null ? 0 : usage.completionTokens());
        updateMap.put(LambdaUtils.getAttributeName(AiMessage::getStatus), AiMessageStatus.COMPLETED);

        this.modelService.updateOne(modelName, updateMap);

        // Roll the turn's usage up to the conversation
        conversationService.addTokenUsage(conversationId, usage);
    }
}
