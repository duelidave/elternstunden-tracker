package de.elternstunden.user.domain;

import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.domain.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** An account as admins see it in the user administration (FS-ADM 7.7). */
public record UserAccount(
        UUID id,
        String email,
        String displayName,
        UserRole role,
        UserStatus status,
        @Nullable UUID familyId,
        boolean managedByConfig,
        boolean emailVerified,
        @Nullable Instant lastLoginAt,
        Instant createdAt,
        @Nullable Instant anonymizedAt,
        boolean totpEnabled) {

    /** An active family member without family (7.1.7). */
    public boolean unassigned() {
        return role == UserRole.FAMILY_MEMBER && status == UserStatus.ACTIVE && familyId == null;
    }
}
