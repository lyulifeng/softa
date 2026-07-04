package io.softa.starter.ai.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.ai.config.AiProperties;
import io.softa.starter.ai.entity.AiMessage;
import io.softa.starter.ai.enums.AiMessageStatus;
import io.softa.starter.ai.service.AiMessageService;

/**
 * Assembles multi-turn conversation history for a chat call by replaying prior
 * {@code ai_message} rows as Spring AI {@link Message}s.
 * <p>
 * This is what makes the chat genuinely multi-turn (the previous design persisted
 * messages but never replayed them). We deliberately do NOT use Spring AI's
 * {@code ChatMemoryRepository}/{@code MessageChatMemoryAdvisor}: the {@code ai_message}
 * table is the single writer (with richer domain metadata — status, tokens, parent),
 * so an advisor would double-write and flatten that. History assembly here is read-only.
 */
@Component
@RequiredArgsConstructor
public class ConversationHistoryAssembler {

    private final AiMessageService aiMessageService;
    private final AiProperties aiProperties;

    /**
     * Load completed user/assistant turns for a conversation, oldest-first, excluding
     * {@code excludeMessageId} (the current turn's user message, already persisted),
     * windowed to the most recent {@code historyWindowSize} messages.
     */
    public List<Message> load(Long conversationId, Long excludeMessageId) {
        if (conversationId == null) {
            return List.of();
        }
        Filters filters = new Filters().eq(AiMessage::getConversationId, conversationId);
        List<AiMessage> rows = new ArrayList<>(aiMessageService.searchList(filters));
        rows.sort(Comparator
                .comparing(AiMessage::getCreatedTime, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(AiMessage::getId, Comparator.nullsFirst(Comparator.naturalOrder())));

        List<Message> history = new ArrayList<>();
        for (AiMessage row : rows) {
            if (excludeMessageId != null && excludeMessageId.equals(row.getId())) {
                continue;
            }
            if (row.getStatus() != AiMessageStatus.COMPLETED) {
                continue;
            }
            Message message = toMessage(row);
            if (message != null) {
                history.add(message);
            }
        }

        int window = aiProperties.getHistoryWindowSize();
        if (window > 0 && history.size() > window) {
            return new ArrayList<>(history.subList(history.size() - window, history.size()));
        }
        return history;
    }

    private Message toMessage(AiMessage row) {
        String content = row.getContent();
        if (content == null || content.isBlank() || row.getRole() == null) {
            return null;
        }
        return switch (row.getRole()) {
            case USER -> new UserMessage(content);
            case ASSISTANT -> new AssistantMessage(content);
            case SYSTEM -> new SystemMessage(content);
            // TOOL / FUNCTION turns are not replayed yet (no tool-calling in scope)
            default -> null;
        };
    }
}
