package com.nexusapi.entity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
import java.util.Objects;

@Embeddable
public class TeamMemberId implements Serializable {
    private UUID userId;
    private UUID teamId;

    public TeamMemberId() {}

    public TeamMemberId(UUID userId, UUID teamId) {
        this.userId = userId;
        this.teamId = teamId;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getTeamId() { return teamId; }
    public void setTeamId(UUID teamId) { this.teamId = teamId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamMemberId)) return false;
        TeamMemberId that = (TeamMemberId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(teamId, that.teamId);
    }

    @Override
    public int hashCode() { return Objects.hash(userId, teamId); }
}