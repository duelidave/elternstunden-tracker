package de.elternstunden.common.security;

import de.elternstunden.auth.domain.UserRole;
import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * The only thing stored in the session (7.2): user id, role, family and login time. No Spring OAuth2 objects, no
 * provider tokens, no e-mail. The principal name is the user id, so sessions can be found per user without personal
 * data (Spring Session principal index).
 */
public record AppUserPrincipal(
        UUID userId, UserRole role, @Nullable UUID familyId, Instant authenticatedAt)
        implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return userId.toString();
    }
}
