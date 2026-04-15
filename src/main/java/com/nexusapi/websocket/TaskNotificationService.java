ackage com.nexusapi.websocket;

import com.nexusapi.dto.response.TaskResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class TaskNotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public TaskNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Async
    public void notifyTaskCreated(UUID projectId, TaskResponse task) {
        send(projectId, new TaskEvent(TaskEvent.EventType.CREATED, task));
    }

    @Async
    public void notifyTaskUpdated(UUID projectId, TaskResponse task) {
        send(projectId, new TaskEvent(TaskEvent.EventType.UPDATED, task));
    }

    @Async
    public void notifyTaskDeleted(UUID projectId, TaskResponse task) {
        send(projectId, new TaskEvent(TaskEvent.EventType.DELETED, task));
    }

    private void send(UUID projectId, TaskEvent event) {
        messagingTemplate.convertAndSend("/topic/projects/" + projectId + "/tasks", event);
    }
}