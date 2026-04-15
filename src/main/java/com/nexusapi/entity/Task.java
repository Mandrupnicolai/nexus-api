ackage com.nexusapi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core task entity — the primary unit of work in NexusAPI.
 *
 * <p>Tasks belong to a {@link Project} and optionally to an assignee ({@link User}).
 * State transitions are validated in the service layer to enforce the allowed
 * workflow: TODO → IN_PROGRESS → IN_REVIEW → DONE.
 *
 * <p>The {@code position} field supports drag-and-drop reordering on the
 * Kanban board without requiring a full table scan.
 */
@Entity
@Table(name = "tasks")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * The project this task belongs to.
     * Loaded lazily — only fetched when the project is explicitly accessed.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    /**
     * Optional assignee. {@code SET NULL} on user delete — orphaned tasks
     * remain visible but unassigned.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    /** The user who created this task. Never null, never changes. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    @Builder.Default
    private Priority priority = Priority.MEDIUM;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Zero-based position for Kanban column ordering. */
    @Column(name = "position", nullable = false)
    @Builder.Default
    private int position = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    // ---------------------------------------------------------------------------
    // Relationships
    // ---------------------------------------------------------------------------

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "task", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt DESC")
    @Builder.Default
    private List<TaskActivity> activities = new ArrayList<>();

    // ---------------------------------------------------------------------------
    // Domain behaviour
    // ---------------------------------------------------------------------------

    /**
     * Transitions the task to a new status, recording the change in the
     * activity log. Enforces the allowed state machine.
     *
     * @param newStatus the target status
     * @param actor     the user performing the transition
     * @throws IllegalStateException if the transition is not permitted
     */
    public void transitionTo(Status newStatus, User actor) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Cannot transition task from %s to %s", this.status, newStatus)
            );
        }
        String oldValue = this.status.name();
        this.status = newStatus;

        if (newStatus == Status.DONE) {
            this.completedAt = Instant.now();
        } else {
            this.completedAt = null;
        }

        activities.add(TaskActivity.builder()
            .task(this)
            .actor(actor)
            .action("STATUS_CHANGED")
            .oldValue(oldValue)
            .newValue(newStatus.name())
            .build());
    }

    /**
     * Assigns the task to a user, recording the change in the activity log.
     *
     * @param newAssignee the user to assign (may be null to unassign)
     * @param actor       the user performing the assignment
     */
    public void assignTo(User newAssignee, User actor) {
        String oldValue = this.assignee != null ? this.assignee.getUsername() : "unassigned";
        this.assignee = newAssignee;
        String newValue = newAssignee != null ? newAssignee.getUsername() : "unassigned";

        activities.add(TaskActivity.builder()
            .task(this)
            .actor(actor)
            .action("ASSIGNED")
            .oldValue(oldValue)
            .newValue(newValue)
            .build());
    }

    /** Soft-deletes the task. */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    // ---------------------------------------------------------------------------
    // Status state machine
    // ---------------------------------------------------------------------------

    public enum Status {
        TODO {
            @Override
            public boolean canTransitionTo(Status next) {
                return next == IN_PROGRESS;
            }
        },
        IN_PROGRESS {
            @Override
            public boolean canTransitionTo(Status next) {
                return next == IN_REVIEW || next == TODO;
            }
        },
        IN_REVIEW {
            @Override
            public boolean canTransitionTo(Status next) {
                return next == DONE || next == IN_PROGRESS;
            }
        },
        DONE {
            @Override
            public boolean canTransitionTo(Status next) {
                return next == TODO; // Allow reopening
            }
        };

        /** Returns true if transitioning to {@code next} is permitted. */
        public abstract boolean canTransitionTo(Status next);
    }

    // ---------------------------------------------------------------------------
    // Priority enum
    // ---------------------------------------------------------------------------

    public enum Priority {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
