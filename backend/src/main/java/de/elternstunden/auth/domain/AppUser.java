package de.elternstunden.auth.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Read model of {@code app_user} for the auth module. */
public record AppUser(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        @Nullable UUID familyId,
        boolean managedByConfig) {

    public boolean isMaster() {
        return role == UserRole.MASTER_ADMIN;
    }
}
