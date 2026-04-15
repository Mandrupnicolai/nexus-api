ackage com.nexusapi.repository;

import com.nexusapi.entity.Task;
import com.nexusapi.entity.Task.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    Page<Task> findByProjectIdAndDeletedAtIsNull(UUID projectId, Pageable pageable);
    Page<Task> findByProjectIdAndStatusAndDeletedAtIsNull(UUID projectId, TaskStatus status, Pageable pageable);
    Optional<Task> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT t FROM Task t WHERE t.project.id = :projectId AND t.assignee.id = :userId AND t.deletedAt IS NULL")
    Page<Task> findByProjectIdAndAssigneeIdAndDeletedAtIsNull(@Param("projectId") UUID projectId,
                                                               @Param("userId") UUID userId,
                                                               Pageable pageable);
}