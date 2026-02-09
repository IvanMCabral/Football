package com.footballmanager.infrastructure.persistence.entity;

import com.footballmanager.domain.model.aggregate.User;
import com.footballmanager.domain.model.valueobject.UserId;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {
    @Id
    private UUID id;
    private String email;
    private String username;
    private String passwordHash;
    private String role;
    private Instant createdAt;
    private Instant updatedAt;
    private UUID teamId;

    public static UserEntity fromDomain(User user) {
        return new UserEntity(
            null,
            user.getEmail(),
            user.getUsername(),
            user.getPasswordHash(),
            user.getRole().name(),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getTeamId()
        );
    }

    public User toDomain() {
        return User.reconstruct(
                UserId.of(id),
                email,
                username,
                passwordHash,
                User.UserRole.valueOf(role),
                createdAt,
                updatedAt,
                teamId
        );
    }
}
