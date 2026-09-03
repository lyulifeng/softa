package io.softa.starter.flow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Form contract usage within a flow.
 */
@Getter
@AllArgsConstructor
public enum FlowFormUsage {
    START_FORM("StartForm", "Start Form"),
    TASK_FORM("TaskForm", "Task Form"),
    READONLY_VIEW("ReadonlyView", "Readonly View"),
    ;

    @JsonValue
    private final String type;
    private final String name;
}

