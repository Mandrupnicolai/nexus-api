package com.nexusapi.controller;

import com.nexusapi.dto.request.CreateTaskRequest;
import com.nexusapi.dto.request.UpdateTaskRequest;
import com.nexusapi.dto.response.PagedResponse;
import com.nexusapi.dto.response.TaskResponse;
import com.nexusapi.entity.User;
import com.nexusapi.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for task CRUD operations.
 *
 * <p>
 * All endpoints require a valid JWT Bearer token.
 * Base path: {@code /api/v1/tasks}
 *
 * <p>
 * Follows REST conventions:
 * <ul>
 * <li>GET → read, idempotent, cacheable</li>
 * <li>POST → create, returns 201 with Location header</li>
 * <li>PATCH → partial update (preferred over PUT for partial updates)</li>
 * <li>DELETE → soft delete, returns 204 No Content</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "Tasks", description = "Task management endpoints")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;

    // ---------------------------------------------------------------------------
    // GET /api/v1/tasks?projectId=...&page=0&size=20&sort=position,asc
    // ---------------------------------------------------------------------------

    /**
     * Returns a paginated list of tasks for a project.
     *
     * @param projectId   the project UUID to query
     * @param page        zero-based page number (default 0)
     * @param size        page size, max 100 (default 20)
     * @param sort        sort field and direction (default "position,asc")
     * @param currentUser the authenticated user injected by Spring Security
     * @return paginated task list wrapped in a {@link PagedResponse}
     */
    @GetMapping
    @Operation(summary = "List tasks for a project")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tasks retrieved"),
            @ApiResponse(responseCode = "403", description = "Not a team member"),
            @ApiResponse(responseCode = "404", description = "Project not found")
    })
    public ResponseEntity<PagedResponse<TaskResponse>> getTasks(
            @RequestParam UUID projectId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "position,asc") String sort,
            @AuthenticationPrincipal User currentUser) {
        // Clamp page size to prevent abuse
        int clampedSize = Math.min(size, 100);

        String[] sortParts = sort.split(",");
        Sort.Direction direction = sortParts.length > 1 && sortParts[1].equalsIgnoreCase("desc")
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Page<TaskResponse> tasks = taskService.getTasksByProject(
                projectId,
                PageRequest.of(page, clampedSize, Sort.by(direction, sortParts[0])),
                currentUser);

        return ResponseEntity.ok(PagedResponse.from(tasks));
    }

    // ---------------------------------------------------------------------------
    // GET /api/v1/tasks/{id}
    // ---------------------------------------------------------------------------

    /**
     * Returns a single task by ID.
     *
     * @param taskId      the task UUID
     * @param currentUser the authenticated user
     * @return the task DTO
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task found"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    public ResponseEntity<TaskResponse> getTaskById(
            @PathVariable("id") @Parameter(description = "Task UUID") UUID taskId,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(taskService.getTaskById(taskId, currentUser));
    }

    // ---------------------------------------------------------------------------
    // POST /api/v1/tasks
    // ---------------------------------------------------------------------------

    /**
     * Creates a new task.
     *
     * <p>
     * {@code @Valid} triggers bean validation on the request body.
     * Validation errors are caught by {@link GlobalExceptionHandler} and
     * returned as a structured 400 response.
     *
     * @param request     the task creation payload
     * @param currentUser the authenticated user (becomes task creator)
     * @return 201 Created with the new task DTO
     */
    @PostMapping
    @Operation(summary = "Create a new task")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Task created"),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "403", description = "Not a team member"),
            @ApiResponse(responseCode = "404", description = "Project or assignee not found")
    })
    public ResponseEntity<TaskResponse> createTask(
            @Valid @RequestBody CreateTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        TaskResponse created = taskService.createTask(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ---------------------------------------------------------------------------
    // PATCH /api/v1/tasks/{id}
    // ---------------------------------------------------------------------------

    /**
     * Partially updates a task.
     *
     * <p>
     * Uses PATCH (not PUT) because clients typically update one field at a time
     * (e.g. drag to change status, click to change assignee). All fields in the
     * request body are optional — only non-null fields are applied.
     *
     * @param taskId      the task to update
     * @param request     the partial update payload
     * @param currentUser the authenticated user performing the update
     * @return the updated task DTO
     */
    @PatchMapping("/{id}")
    @Operation(summary = "Update a task (partial)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Task updated"),
            @ApiResponse(responseCode = "400", description = "Invalid status transition"),
            @ApiResponse(responseCode = "403", description = "Access denied"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    public ResponseEntity<TaskResponse> updateTask(
            @PathVariable("id") UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(taskService.updateTask(taskId, request, currentUser));
    }

    // ---------------------------------------------------------------------------
    // DELETE /api/v1/tasks/{id}
    // ---------------------------------------------------------------------------

    /**
     * Soft-deletes a task. Returns 204 No Content on success.
     *
     * @param taskId      the task to delete
     * @param currentUser the authenticated user
     * @return 204 No Content
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a task")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Task deleted"),
            @ApiResponse(responseCode = "403", description = "Not the task creator or admin"),
            @ApiResponse(responseCode = "404", description = "Task not found")
    })
    public ResponseEntity<Void> deleteTask(
            @PathVariable("id") UUID taskId,
            @AuthenticationPrincipal User currentUser) {
        taskService.deleteTask(taskId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
