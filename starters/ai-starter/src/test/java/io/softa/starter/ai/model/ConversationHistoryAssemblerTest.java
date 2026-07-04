package io.softa.starter.ai.model;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.ai.config.AiProperties;
import io.softa.starter.ai.entity.AiMessage;
import io.softa.starter.ai.enums.AiMessageRole;
import io.softa.starter.ai.enums.AiMessageStatus;
import io.softa.starter.ai.service.AiMessageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ConversationHistoryAssembler} — the multi-turn history replay.
 */
@ExtendWith(MockitoExtension.class)
class ConversationHistoryAssemblerTest {

    @Mock
    private AiMessageService aiMessageService;

    private AiProperties properties(int window) {
        AiProperties p = new AiProperties();
        p.setHistoryWindowSize(window);
        return p;
    }

    private AiMessage row(long id, AiMessageRole role, String content, AiMessageStatus status, int second) {
        AiMessage m = new AiMessage();
        m.setId(id);
        m.setConversationId(100L);
        m.setRole(role);
        m.setContent(content);
        m.setStatus(status);
        m.setCreatedTime(LocalDateTime.of(2026, 1, 1, 0, 0, second));
        return m;
    }

    @Test
    void nullConversation_returnsEmpty() {
        ConversationHistoryAssembler assembler = new ConversationHistoryAssembler(aiMessageService, properties(20));
        assertThat(assembler.load(null, null)).isEmpty();
    }

    @Test
    void mapsRoles_ordersByCreatedTime_excludesNonCompletedAndCurrent() {
        List<AiMessage> rows = List.of(
                row(3L, AiMessageRole.ASSISTANT, "answer-1", AiMessageStatus.COMPLETED, 3),
                row(1L, AiMessageRole.SYSTEM, "sys", AiMessageStatus.COMPLETED, 1),
                row(2L, AiMessageRole.USER, "question-1", AiMessageStatus.COMPLETED, 2),
                row(4L, AiMessageRole.USER, "current", AiMessageStatus.COMPLETED, 4),     // excluded (current)
                row(5L, AiMessageRole.ASSISTANT, "pending", AiMessageStatus.PENDING, 5),   // excluded (status)
                row(6L, AiMessageRole.ASSISTANT, "failed", AiMessageStatus.FAILED, 6));    // excluded (status)
        when(aiMessageService.searchList(any(Filters.class))).thenReturn(rows);

        ConversationHistoryAssembler assembler = new ConversationHistoryAssembler(aiMessageService, properties(20));
        List<Message> history = assembler.load(100L, 4L);

        assertThat(history).hasSize(3);
        assertThat(history.get(0)).isInstanceOf(SystemMessage.class);
        assertThat(history.get(0).getText()).isEqualTo("sys");
        assertThat(history.get(1)).isInstanceOf(UserMessage.class);
        assertThat(history.get(1).getText()).isEqualTo("question-1");
        assertThat(history.get(2)).isInstanceOf(AssistantMessage.class);
        assertThat(history.get(2).getText()).isEqualTo("answer-1");
    }

    @Test
    void appliesWindow_keepingMostRecent() {
        List<AiMessage> rows = List.of(
                row(1L, AiMessageRole.USER, "u1", AiMessageStatus.COMPLETED, 1),
                row(2L, AiMessageRole.ASSISTANT, "a1", AiMessageStatus.COMPLETED, 2),
                row(3L, AiMessageRole.USER, "u2", AiMessageStatus.COMPLETED, 3),
                row(4L, AiMessageRole.ASSISTANT, "a2", AiMessageStatus.COMPLETED, 4));
        when(aiMessageService.searchList(any(Filters.class))).thenReturn(rows);

        ConversationHistoryAssembler assembler = new ConversationHistoryAssembler(aiMessageService, properties(2));
        List<Message> history = assembler.load(100L, null);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).getText()).isEqualTo("u2");
        assertThat(history.get(1).getText()).isEqualTo("a2");
    }
}
