package com.nexusapi.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.*;

/**
 * Persistent user entity.
 *
 * <p>Implements {@link UserDetails} so Spring Security can load this entity
 * directly from the repository without a separate DTO conversion step.
 * This is a deliberate architectural choice: it keeps the authentication
 * path simple while still separating the API representation (via DTOs).
 *
 * <p>Uses soft deletes ({@code deletedAt}) to preserve audit trails.
 * All queries should filter on {@code deletedAt IS NULL}.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    /**
     * BCrypt-hashed password. Never store or log plain-text passwords.
     * The hash is produced by {@code PasswordEncoder} in the service layer.
     */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", length = 100)
    private String fullName;

    /**
     * Application role — stored as a string enum for readability in the DB.
     * Uses {@link EnumType#STRING} rather than ORDINAL to survive enum reordering.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

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

    /**
     * Teams this user owns. Lazy-loaded — only fetched when explicitly accessed.
     * {@code mappedBy} references the field name in {@link Team}, not the column.
     */
    @OneToMany(mappedBy = "owner", fetch = FetchType.LAZY)
    @Builder.Default
    private Set<Team> ownedTeams = new HashSet<>();

    // ---------------------------------------------------------------------------
    // UserDetails implementation (Spring Security)
    // ---------------------------------------------------------------------------

    /**
     * Translates the application {@link Role} into a Spring Security authority.
     * The "ROLE_" prefix is required by Spring Security's role-checking methods.
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /** Returns the BCrypt hash — Spring Security uses this for authentication. */
    @Override
    public String getPassword() {
        return passwordHash;
    }

    /** Returns the email as the username for authentication purposes. */
    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive && deletedAt == null;
    }

    // ---------------------------------------------------------------------------
    // Domain behaviour
    // ---------------------------------------------------------------------------

    /** Soft-deletes the user by setting the deletion timestamp. */
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.isActive = false;
    }

    /** Returns {@code true} if this user has been soft-deleted. */
    public boolean isDeleted() {
        return deletedAt != null;
    }

    // ---------------------------------------------------------------------------
    // Role enum
    // ---------------------------------------------------------------------------

    public enum Role {
        USER,
        ADMIN
    }
}
