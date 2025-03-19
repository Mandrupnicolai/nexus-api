package com.nexusapi.mapper;

import com.nexusapi.dto.response.TaskResponse;
import com.nexusapi.dto.response.UserResponse;
import com.nexusapi.entity.Task;
import com.nexusapi.entity.User;
import org.springframework.stereotype.Component;

@Component
public class TaskMapper {

    public TaskResponse toResponse(Task task) {
        if (task == null) return null;
        TaskResponse r = new TaskResponse();
        r.setId(task.getId());
        r.setTitle(task.getTitle());
        r.setDescription(task.getDescription());
        r.setStatus(task.getStatus());
        r.setPriority(task.getPriority());
        r.setDueAt(task.getDueAt());
        r.setProjectId(task.getProject() != null ? task.getProject().getId() : null);
        r.setAssignee(toUserResponse(task.getAssignee()));
        r.setCreatedBy(toUserResponse(task.getCreatedBy()));
        r.setCreatedAt(task.getCreatedAt());
        r.setUpdatedAt(task.getUpdatedAt());
        return r;
    }

    public UserResponse toUserResponse(User user) {
        if (user == null) return null;
        UserResponse r = new UserResponse();
        r.setId(user.getId());
        r.setDisplayName(user.getDisplayName());
        r.setEmail(user.getEmail());
        r.setAvatarUrl(user.getAvatarUrl());
        return r;
    }
}