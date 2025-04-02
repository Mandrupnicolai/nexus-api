package com.nexusapi.service;

import com.nexusapi.entity.Task;
import com.nexusapi.entity.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for the {@link Task} domain entity.
 *
 * <p>Focuses on the status state machine — the most critical business logic
 * in the entity layer. Each invalid transition must throw to prevent
 * data integrity violations.
 */
@DisplayName("Task entity")
class TaskEntityTest {

    private User actor;
    private Task task;

    @BeforeEach
    void setUp() {
        actor = User.builder()
            .id(UUID.randomUUID())
            .username("testuser")
            .build();

        task = Task.builder()
            .id(UUID.randomUUID())
            .title("Test Task")
            .status(Task.Status.TODO)
            .build();
    }

    // ---------------------------------------------------------------------------
    // Valid transitions
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("TODO → IN_PROGRESS is a valid transition")
    void transitionTo_todoToInProgress_succeeds() {
        task.transitionTo(Task.Status.IN_PROGRESS, actor);
        assertThat(task.getStatus()).isEqualTo(Task.Status.IN_PROGRESS);
    }

    @Test
    @DisplayName("IN_PROGRESS → IN_REVIEW is a valid transition")
    void transitionTo_inProgressToInReview_succeeds() {
        task.setStatus(Task.Status.IN_PROGRESS);
        task.transitionTo(Task.Status.IN_REVIEW, actor);
        assertThat(task.getStatus()).isEqualTo(Task.Status.IN_REVIEW);
    }

    @Test
    @DisplayName("IN_REVIEW → DONE is a valid transition and sets completedAt")
    void transitionTo_inReviewToDone_setsCompletedAt() {
        task.setStatus(Task.Status.IN_REVIEW);
        task.transitionTo(Task.Status.DONE, actor);
        assertThat(task.getStatus()).isEqualTo(Task.Status.DONE);
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("DONE → TODO reopens the task and clears completedAt")
    void transitionTo_doneToTodo_clearsCompletedAt() {
        task.setStatus(Task.Status.IN_REVIEW);
        task.transitionTo(Task.Status.DONE, actor);
        task.transitionTo(Task.Status.TODO, actor);
        assertThat(task.getStatus()).isEqualTo(Task.Status.TODO);
        assertThat(task.getCompletedAt()).isNull();
    }

    // ---------------------------------------------------------------------------
    // Invalid transitions
    // ---------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} → {1} should be rejected")
    @CsvSource({
        "TODO, IN_REVIEW",
        "TODO, DONE",
        "IN_PROGRESS, DONE",
        "IN_REVIEW, TODO",
        "DONE, IN_PROGRESS",
        "DONE, IN_REVIEW"
    })
    @DisplayName("invalid transitions throw IllegalStateException")
    void transitionTo_invalidTransition_throwsIllegalStateException(
        Task.Status from, Task.Status to
    ) {
        task.setStatus(from);
        assertThatThrownBy(() -> task.transitionTo(to, actor))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Cannot transition");
    }

    // ---------------------------------------------------------------------------
    // Activity logging
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("valid transition records a STATUS_CHANGED activity entry")
    void transitionTo_valid_recordsActivity() {
        task.transitionTo(Task.Status.IN_PROGRESS, actor);
        assertThat(task.getActivities()).hasSize(1);
        assertThat(task.getActivities().get(0).getAction()).isEqualTo("STATUS_CHANGED");
        assertThat(task.getActivities().get(0).getOldValue()).isEqualTo("TODO");
        assertThat(task.getActivities().get(0).getNewValue()).isEqualTo("IN_PROGRESS");
    }

    // ---------------------------------------------------------------------------
    // Assignment
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("assignTo records an ASSIGNED activity with old and new values")
    void assignTo_newUser_recordsActivity() {
        User newAssignee = User.builder()
            .id(UUID.randomUUID())
            .username("newassignee")
            .build();

        task.assignTo(newAssignee, actor);

        assertThat(task.getAssignee()).isEqualTo(newAssignee);
        assertThat(task.getActivities()).hasSize(1);
        assertThat(task.getActivities().get(0).getAction()).isEqualTo("ASSIGNED");
        assertThat(task.getActivities().get(0).getNewValue()).isEqualTo("newassignee");
    }

    @Test
    @DisplayName("assignTo null unassigns the task")
    void assignTo_null_unassignsTask() {
        task.assignTo(null, actor);
        assertThat(task.getAssignee()).isNull();
        assertThat(task.getActivities().get(0).getNewValue()).isEqualTo("unassigned");
    }

    // ---------------------------------------------------------------------------
    // Soft delete
    // ---------------------------------------------------------------------------

    @Test
    @DisplayName("softDelete sets deletedAt and isDeleted returns true")
    void softDelete_setsDeletedAt() {
        assertThat(task.isDeleted()).isFalse();
        task.softDelete();
        assertThat(task.isDeleted()).isTrue();
        assertThat(task.getDeletedAt()).isNotNull();
    }
}
