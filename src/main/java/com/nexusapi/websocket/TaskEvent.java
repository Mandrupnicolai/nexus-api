package com.nexusapi.websocket;

import com.nexusapi.dto.response.TaskResponse;
import java.time.OffsetDateTime;

public class TaskEvent {
    public enum EventType {
        CREATED, UPDATED, DELETED
    }

    private EventType type;
    private TaskResponse task;
    private OffsetDateTime occurredAt;

    public TaskEvent() {
        this.occurredAt = OffsetDateTime.now();
    }

    public TaskEvent(EventType type, TaskResponse task) {
        this();
        this.type = type;
        this.task = task;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public TaskResponse getTask() {
        return task;
    }

    public void setTask(TaskResponse task) {
        this.task = task;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }
}