ackage com.nexusapi.service;

import com.nexusapi.dto.request.CreateTaskRequest;
import com.nexusapi.dto.request.UpdateTaskRequest;
import com.nexusapi.dto.response.PagedResponse;
import com.nexusapi.dto.response.TaskResponse;
import com.nexusapi.entity.Task.TaskStatus;
import com.nexusapi.entity.User;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface TaskService {
    TaskResponse createTask(CreateTaskRequest request, User currentUser);
    TaskResponse getTask(UUID taskId, User currentUser);
    PagedResponse<TaskResponse> getTasksByProject(UUID projectId, TaskStatus status, Pageable pageable, User currentUser);
    TaskResponse updateTask(UUID taskId, UpdateTaskRequest request, User currentUser);
    void deleteTask(UUID taskId, User currentUser);
}