package com.nexusapi.service.impl;

import com.nexusapi.dto.request.CreateTaskRequest;
import com.nexusapi.dto.request.UpdateTaskRequest;
import com.nexusapi.dto.response.TaskResponse;
import com.nexusapi.entity.Task;
import com.nexusapi.entity.User;
import com.nexusapi.exception.ForbiddenException;
import com.nexusapi.exception.ResourceNotFoundException;
import com.nexusapi.mapper.TaskMapper;
import com.nexusapi.repository.ProjectRepository;
import com.nexusapi.repository.TaskRepository;
import com.nexusapi.repository.UserRepository;
import com.nexusapi.service.TaskService;
import com.nexusapi.websocket.TaskNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Business logic for task management.
 *
 * <p>This service is the authoritative entry-point for all task operations.
 * It enforces:
 * <ul>
 *   <li>Ownership and team membership checks</li>
 *   <li>Task status machine transitions</li>
 *   <li>Activity logging on every state change</li>
 *   <li>Real-time WebSocket notifications to connected clients</li>
 *   <li>Redis cache invalidation on mutations</li>
 * </ul>
 *
 * <p>All write methods are {@code @Transactional} — if a WebSocket notification
 * fails, the database change is still committed (notifications are best-effort).
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)  // Default: read-only; overridden per write method
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskMapper taskMapper;
    private final TaskNotificationService notificationService;

    // ---------------------------------------------------------------------------
    // Queries
    // ---------------------------------------------------------------------------

    /**
     * Returns a paginated list of tasks for a project.
     *
     * <p>Results are cached in Redis keyed by project ID + page parameters.
     * The cache is evicted on any mutation to tasks in this project.
     *
     * @param projectId the project to query
     * @param pageable  pagination and sorting parameters
     * @param currentUser the requesting user (used for access control)
     * @return page of task response DTOs
     */
    @Cacheable(value = "tasks", key = "#projectId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public Page<TaskResponse> getTasksByProject(UUID projectId, Pageable pageable, User currentUser) {
        validateProjectAccess(projectId, currentUser);
        return taskRepository.findByProjectIdAndDeletedAtIsNull(projectId, pageable)
            .map(taskMapper::toResponse);
    }

    /**
     * Returns a single task by ID.
     *
     * @param taskId      the task to retrieve
     * @param currentUser the requesting user
     * @return the task response DTO
     * @throws ResourceNotFoundException if the task does not exist
     * @throws ForbiddenException        if the user cannot access this task
     */
    @Cacheable(value = "task", key = "#taskId")
    public TaskResponse getTaskById(UUID taskId, User currentUser) {
        Task task = findActiveTask(taskId);
        validateTaskAccess(task, currentUser);
        return taskMapper.toResponse(task);
    }

    // ---------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------

    /**
     * Creates a new task in the specified project.
     *
     * <p>The creating user is recorded as {@code createdBy}. The task starts
     * in {@code TODO} status with the provided priority (default MEDIUM).
     *
     * @param request     the task creation DTO
     * @param currentUser the authenticated user creating the task
     * @return the created task response DTO
     */
    @Transactional
    @CacheEvict(value = "tasks", allEntries = true)
    public TaskResponse createTask(CreateTaskRequest request, User currentUser) {
        var project = projectRepository.findByIdAndDeletedAtIsNull(request.projectId())
            .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId()));

        validateProjectAccess(project.getId(), currentUser);

        User assignee = null;
        if (request.assigneeId() != null) {
            assignee = userRepository.findByIdAndDeletedAtIsNull(request.assigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.assigneeId()));
        }

        Task task = Task.builder()
            .title(request.title())
            .description(request.description())
            .project(project)
            .assignee(assignee)
            .createdBy(currentUser)
            .priority(request.priority() != null ? request.priority() : Task.Priority.MEDIUM)
            .dueDate(request.dueDate())
            .build();

        Task saved = taskRepository.save(task);
        log.info("Task created: {} in project {}", saved.getId(), project.getId());

        TaskResponse response = taskMapper.toResponse(saved);
        // Notify connected clients about the new task
        notificationService.notifyTaskCreated(project.getId(), response);

        return response;
    }

    /**
     * Updates an existing task's mutable fields.
     *
     * <p>Only the task's project members can update tasks. Status transitions
     * are validated by the {@link Task#transitionTo} state machine.
     *
     * @param taskId      the task to update
     * @param request     the update DTO
     * @param currentUser the authenticated user performing the update
     * @return the updated task response DTO
     * @throws IllegalStateException if the status transition is not permitted
     */
    @Transactional
    @CacheEvict(value = {"tasks", "task"}, allEntries = true)
    public TaskResponse updateTask(UUID taskId, UpdateTaskRequest request, User currentUser) {
        Task task = findActiveTask(taskId);
        validateTaskAccess(task, currentUser);

        if (request.title() != null) {
            task.setTitle(request.title());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.dueDate() != null) {
            task.setDueDate(request.dueDate());
        }
        if (request.status() != null && request.status() != task.getStatus()) {
            // Delegate to the domain entity — it validates the transition
            task.transitionTo(request.status(), currentUser);
        }
        if (request.assigneeId() != null) {
            User newAssignee = userRepository.findByIdAndDeletedAtIsNull(request.assigneeId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.assigneeId()));
            task.assignTo(newAssignee, currentUser);
        }

        Task updated = taskRepository.save(task);
        log.info("Task updated: {}", updated.getId());

        TaskResponse response = taskMapper.toResponse(updated);
        notificationService.notifyTaskUpdated(updated.getProject().getId(), response);

        return response;
    }

    /**
     * Soft-deletes a task.
     *
     * <p>Only the task creator or a team admin can delete tasks.
     *
     * @param taskId      the task to delete
     * @param currentUser the authenticated user requesting deletion
     */
    @Transactional
    @CacheEvict(value = {"tasks", "task"}, allEntries = true)
    @PreAuthorize("hasRole('ADMIN') or @taskSecurityService.isTaskCreator(#taskId, #currentUser)")
    public void deleteTask(UUID taskId, User currentUser) {
        Task task = findActiveTask(taskId);
        task.softDelete();
        taskRepository.save(task);
        log.info("Task soft-deleted: {} by user {}", taskId, currentUser.getId());
        notificationService.notifyTaskDeleted(task.getProject().getId(), taskId);
    }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private Task findActiveTask(UUID taskId) {
        return taskRepository.findByIdAndDeletedAtIsNull(taskId)
            .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }

    private void validateProjectAccess(UUID projectId, User user) {
        // Admins bypass team membership checks
        if (user.getRole() == User.Role.ADMIN) return;

        boolean isMember = projectRepository.isUserMemberOfProjectTeam(projectId, user.getId());
        if (!isMember) {
            throw new ForbiddenException("You are not a member of this project's team");
        }
    }

    private void validateTaskAccess(Task task, User user) {
        validateProjectAccess(task.getProject().getId(), user);
    }
}
