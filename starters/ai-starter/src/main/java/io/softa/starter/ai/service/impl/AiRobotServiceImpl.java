package io.softa.starter.ai.service.impl;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.ai.config.AiProperties;
import io.softa.starter.ai.dto.AiResponseMessage;
import io.softa.starter.ai.dto.AiStreamRequest;
import io.softa.starter.ai.dto.AiUserMessage;
import io.softa.starter.ai.entity.AiMessage;
import io.softa.starter.ai.entity.AiModel;
import io.softa.starter.ai.entity.AiRobot;
import io.softa.starter.ai.model.ChatExecutor;
import io.softa.starter.ai.model.ConversationHistoryAssembler;
import io.softa.starter.ai.service.AiConversationService;
import io.softa.starter.ai.service.AiMessageService;
import io.softa.starter.ai.service.AiModelService;
import io.softa.starter.ai.service.AiRobotService;

/**
 * AiRobot Model Service Implementation.
 * <p>
 * Owns conversation management and message persistence (the database). All model I/O
 * is delegated to {@link ChatExecutor}; this class never touches a Spring AI / SDK type.
 */
@Service
@Slf4j
public class AiRobotServiceImpl extends EntityServiceImpl<AiRobot, Long> implements AiRobotService {

    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private AiModelService aiModelService;

    @Autowired
    private AiConversationService conversationService;

    @Autowired
    private AiMessageService aiMessageService;

    @Autowired
    private ChatExecutor chatExecutor;

    @Autowired
    private ConversationHistoryAssembler historyAssembler;

    /**
     * Persist user message and AI message in advance for stream response.
     * Response: Conversation ID, User Message ID, AI Message ID
     *
     * @param aiUserMessage AI User message
     * @return AiResponseMessage
     */
    @Override
    public AiResponseMessage persistChatMessage(AiUserMessage aiUserMessage) {
        if (aiUserMessage.getConversationId() == null) {
            Long conversationId = conversationService.newConversation(aiUserMessage);
            aiUserMessage.setConversationId(conversationId);
        }
        // Save user message
        AiMessage userMessage = aiMessageService.saveUserMessage(aiUserMessage);
        // Save AI message in advance for stream response
        AiMessage aiMessage = aiMessageService.saveAiMessageForStream(userMessage);
        // Build response message
        AiResponseMessage aiResponseMessage = new AiResponseMessage();
        aiResponseMessage.setConversationId(userMessage.getConversationId());
        aiResponseMessage.setUserMessageId(userMessage.getId());
        aiResponseMessage.setAiMessageId(aiMessage.getId());
        return aiResponseMessage;
    }

    /**
     * Get robot object based on robot ID
     *
     * @param robotId Robot ID
     * @return Robot object
     */
    private AiRobot getAiRobotById(Long robotId) {
        Assert.notNull(robotId, "Robot ID cannot be empty!");
        return this.getById(robotId).orElseThrow(
                () -> new IllegalArgumentException("Robot ID not exists: " + robotId));
    }

    /**
     * Resolve the authoritative AI model (provider / endpoint / credentials / code) for a robot.
     *
     * @param aiRobot Robot object
     * @return AI model
     */
    private AiModel resolveAiModel(AiRobot aiRobot) {
        Assert.notNull(aiRobot.getAiModelId(), "Robot has no AI model configured: " + aiRobot.getCode());
        return aiModelService.getById(aiRobot.getAiModelId()).orElseThrow(
                () -> new IllegalArgumentException("AI model not found: " + aiRobot.getAiModelId()));
    }

    /**
     * Stream Chat
     *
     * @param aiRequest AI Stream Request Message
     * @return SseEmitter
     */
    @Override
    public SseEmitter streamChat(AiStreamRequest aiRequest) {
        Context context = ContextHolder.getContext();
        AiMessage userMessage = aiMessageService.getById(aiRequest.getUserMessageId()).orElseThrow(
                () -> new IllegalArgumentException("User Message ID not exists: " + aiRequest.getUserMessageId()));
        AiMessage aiMessage = aiMessageService.getById(aiRequest.getAiMessageId()).orElseThrow(
                () -> new IllegalArgumentException("AI Message ID not exists: " + aiRequest.getAiMessageId()));
        AiRobot aiRobot = this.getAiRobotById(userMessage.getRobotId());
        AiModel aiModel = this.resolveAiModel(aiRobot);
        List<Message> history = historyAssembler.load(userMessage.getConversationId(), userMessage.getId());

        // Use SseEmitter to send messages to the client
        SseEmitter sseEmitter = new SseEmitter(aiProperties.getResponseTimeout());
        sseEmitter.onTimeout(sseEmitter::complete);
        sseEmitter.onError(_ -> sseEmitter.complete());

        // Stream via Spring AI; persist the accumulated result on completion (in the request context)
        chatExecutor.stream(aiModel, aiRobot, history, userMessage.getContent(), sseEmitter, result ->
                ContextHolder.runWith(context, () -> aiMessageService.updateAiMessageAfterStream(
                        aiMessage.getId(), result.text(), result.usage(), userMessage.getConversationId())));
        return sseEmitter;
    }

    /**
     * Chat API
     *
     * @param aiUserMessage Chat message
     * @return AiMessage
     */
    @Override
    public AiMessage chat(AiUserMessage aiUserMessage) {
        // Get robot object
        AiRobot aiRobot = this.getAiRobotById(aiUserMessage.getRobotId());
        // New conversation if conversation ID is not set
        if (aiUserMessage.getConversationId() == null) {
            Long conversationId = conversationService.newConversation(aiUserMessage);
            aiUserMessage.setConversationId(conversationId);
        }
        // Complete chat
        return this.completeChat(aiRobot, aiUserMessage);
    }

    /**
     * Complete chat
     *
     * @param aiRobot       Robot object
     * @param aiUserMessage AI User message
     * @return AI message
     */
    private AiMessage completeChat(AiRobot aiRobot, AiUserMessage aiUserMessage) {
        // Save user message
        AiMessage userMessage = aiMessageService.saveUserMessage(aiUserMessage);
        AiModel aiModel = this.resolveAiModel(aiRobot);
        // Replay prior turns (excluding the just-saved current user message)
        List<Message> history = historyAssembler.load(userMessage.getConversationId(), userMessage.getId());

        // Send the chat message and persist the AI response
        ChatExecutor.ChatResult result = chatExecutor.callSync(aiModel, aiRobot, history, aiUserMessage.getContent());
        return aiMessageService.saveAiMessageForNonStreaming(userMessage, result.text(), result.usage());
    }

}
