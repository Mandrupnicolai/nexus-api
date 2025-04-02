package com.nexusapi.service;

import com.nexusapi.dto.request.CreateTaskRequest;
import com.nexusapi.dto.request.UpdateTaskRequest;
import com.nexusapi.dto.response.TaskResponse;
import com.nexusapi.entity.*;
import com.nexusapi.exception.ForbiddenException;
import com.nexusapi.exception.ResourceNotFoundException;
import com.nexusapi.mapper.TaskMapper;
import com.nexusapi.repository.ProjectRepository;
import com.nexusapi.repository.TaskRepository;
import com.nexusapi.repository.UserRepository;
import com.nexusapi.service.impl.TaskServiceImpl;
import com.nexusapi.websocket.TaskNotificationService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TaskServiceImpl}.
 *
 * <p>Uses Mockito to isolate the service from all infrastructure dependencies
 * (database, Redis, WebSocket). Each test verifies a single behaviour.
 *
 * <p>Test naming convention: {@code methodName_condition_expectedResult}
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskServiceImpl")
class TaskServiceImplTest {

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private TaskMapper taskMapper;
    @Mock private TaskNotificationService notificationService;

    @InjectMocks
    private TaskServiceImpl taskService;

    // ---------------------------------------------------------------------------
    // Fixtures
    // ---------------------------------------------------------------------------

    private User adminUser;
    private User regularUser;
    private Project project;
    private Task task;
    private TaskResponse taskResponse;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
            .id(UUID.randomUUID())
            .email("admin@test.com")
            .username("admin")
            .role(User.Role.ADMIN)
            .build();

        regularUser = User.builder()
            .id(UUID.randomUUID())
            .email("user@test.com")
            .username("user")
            .role(User.Role.USER)
            .build();

        project = Project.builder()
            .id(UUID.randomUUID())
            .name("Test Project")
            .owner(adminUser)
            .build();

        task = Task.builder()
            .id(UUID.randomUUID())
            .title("Test Task")
            .project(project)
            .createdBy(adminUser)
            .status(Task.Status.TODO)
            .priority(Task.Priority.MEDIUM)
            .build();

        taskResponse = new TaskResponse(
            task.getId(), task.getTitle(), null,
            project.getId(), null, null,
            adminUser.getId(), "admin",
            Task.Status.TODO, Task.Priority.MEDIUM,
            null, null, 0, null, null
        );
    }

    // ---------------------------------------------------------------------------
    // createTask tests
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("createTask()")
    class CreateTask {

        @Test
        @DisplayName("creates task and returns response DTO when user is admin")
        void createTask_asAdmin_returnsCreatedTask() {
            // Arrange
            CreateTaskRequest request = new CreateTaskRequest(
                "New Task", "Description", project.getId(), null, Task.Priority.HIGH, null
            );
            when(projectRepository.findByIdAndDeletedAtIsNull(project.getId()))
                .thenReturn(Optional.of(project));
            when(taskRepository.save(any(Task.class))).thenReturn(task);
            when(taskMapper.toResponse(task)).thenReturn(taskResponse);

            // Act
            TaskResponse result = taskService.createTask(request, adminUser);

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(task.getId());
            verify(taskRepository).save(any(Task.class));
            verify(notificationService).notifyTaskCreated(eq(project.getId()), any());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when project does not exist")
        void createTask_projectNotFound_throwsNotFoundException() {
            // Arrange
            UUID missingId = UUID.randomUUID();
            CreateTaskRequest request = new CreateTaskRequest(
                "Task", null, missingId, null, null, null
            );
            when(projectRepository.findByIdAndDeletedAtIsNull(missingId))
                .thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> taskService.createTask(request, adminUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Project");

            verify(taskRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws ForbiddenException when regular user is not a team member")
        void createTask_notTeamMember_throwsForbiddenException() {
            // Arrange
            CreateTaskRequest request = new CreateTaskRequest(
                "Task", null, project.getId(), null, null, null
            );
            when(projectRepository.findByIdAndDeletedAtIsNull(project.getId()))
                .thenReturn(Optional.of(project));
            when(projectRepository.isUserMemberOfProjectTeam(project.getId(), regularUser.getId()))
                .thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> taskService.createTask(request, regularUser))
                .isInstanceOf(ForbiddenException.class);

            verify(taskRepository, never()).save(any());
        }

        @Test
        @DisplayName("resolves assignee when assigneeId is provided")
        void createTask_withAssigneeId_resolvesAssignee() {
            // Arrange
            CreateTaskRequest request = new CreateTaskRequest(
                "Task", null, project.getId(), regularUser.getId(), null, null
            );
            when(projectRepository.findByIdAndDeletedAtIsNull(project.getId()))
                .thenReturn(Optional.of(project));
            when(userRepository.findByIdAndDeletedAtIsNull(regularUser.getId()))
                .thenReturn(Optional.of(regularUser));
            when(taskRepository.save(any(Task.class))).thenReturn(task);
            when(taskMapper.toResponse(task)).thenReturn(taskResponse);

            // Act
            taskService.createTask(request, adminUser);

            // Assert — verify assignee lookup was called
            verify(userRepository).findByIdAndDeletedAtIsNull(regularUser.getId());
        }
    }

    // ---------------------------------------------------------------------------
    // getTaskById tests
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("getTaskById()")
    class GetTaskById {

        @Test
        @DisplayName("returns task when found and user has access")
        void getTaskById_exists_returnsTask() {
            when(taskRepository.findByIdAndDeletedAtIsNull(task.getId()))
                .thenReturn(Optional.of(task));
            when(taskMapper.toResponse(task)).thenReturn(taskResponse);

            TaskResponse result = taskService.getTaskById(task.getId(), adminUser);

            assertThat(result).isEqualTo(taskResponse);
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when task does not exist")
        void getTaskById_notFound_throwsNotFoundException() {
            UUID missingId = UUID.randomUUID();
            when(taskRepository.findByIdAndDeletedAtIsNull(missingId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.getTaskById(missingId, adminUser))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Task");
        }
    }

    // ---------------------------------------------------------------------------
    // updateTask tests
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("updateTask()")
    class UpdateTask {

        @Test
        @DisplayName("updates title when title is provided in request")
        void updateTask_withTitle_updatesTitle() {
            UpdateTaskRequest request = new UpdateTaskRequest(
                "Updated Title", null, null, null, null, null
            );
            when(taskRepository.findByIdAndDeletedAtIsNull(task.getId()))
                .thenReturn(Optional.of(task));
            when(taskRepository.save(task)).thenReturn(task);
            when(taskMapper.toResponse(task)).thenReturn(taskResponse);

            taskService.updateTask(task.getId(), request, adminUser);

            assertThat(task.getTitle()).isEqualTo("Updated Title");
            verify(taskRepository).save(task);
        }

        @Test
        @DisplayName("triggers status transition when new status provided")
        void updateTask_withStatusChange_triggersTransition() {
            UpdateTaskRequest request = new UpdateTaskRequest(
                null, null, Task.Status.IN_PROGRESS, null, null, null
            );
            when(taskRepository.findByIdAndDeletedAtIsNull(task.getId()))
                .thenReturn(Optional.of(task));
            when(taskRepository.save(task)).thenReturn(task);
            when(taskMapper.toResponse(task)).thenReturn(taskResponse);

            taskService.updateTask(task.getId(), request, adminUser);

            // Task status should have changed to IN_PROGRESS
            assertThat(task.getStatus()).isEqualTo(Task.Status.IN_PROGRESS);
        }

        @Test
        @DisplayName("sends WebSocket notification on successful update")
        void updateTask_success_sendsNotification() {
            UpdateTaskRequest request = new UpdateTaskRequest(
                "New Title", null, null, null, null, null
            );
            when(taskRepository.findByIdAndDeletedAtIsNull(task.getId()))
                .thenReturn(Optional.of(task));
            when(taskRepository.save(task)).thenReturn(task);
            when(taskMapper.toResponse(task)).thenReturn(taskResponse);

            taskService.updateTask(task.getId(), request, adminUser);

            verify(notificationService).notifyTaskUpdated(eq(project.getId()), any());
        }
    }

    // ---------------------------------------------------------------------------
    // deleteTask tests
    // ---------------------------------------------------------------------------

    @Nested
    @DisplayName("deleteTask()")
    class DeleteTask {

        @Test
        @DisplayName("soft-deletes task and sends notification")
        void deleteTask_asAdmin_softDeletesTask() {
            when(taskRepository.findByIdAndDeletedAtIsNull(task.getId()))
                .thenReturn(Optional.of(task));
            when(taskRepository.save(task)).thenReturn(task);

            taskService.deleteTask(task.getId(), adminUser);

            assertThat(task.isDeleted()).isTrue();
            verify(taskRepository).save(task);
            verify(notificationService).notifyTaskDeleted(project.getId(), task.getId());
        }

        @Test
        @DisplayName("throws ResourceNotFoundException when deleting non-existent task")
        void deleteTask_notFound_throwsNotFoundException() {
            UUID missingId = UUID.randomUUID();
            when(taskRepository.findByIdAndDeletedAtIsNull(missingId))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() -> taskService.deleteTask(missingId, adminUser))
                .isInstanceOf(ResourceNotFoundException.class);

            verify(taskRepository, never()).save(any());
        }
    }
}
