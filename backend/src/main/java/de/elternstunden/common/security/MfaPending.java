package de.elternstunden.common.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

/**
 * Session state MFA_PENDING (7.2): an admin passed the first factor (magic link or OIDC) and still needs the TOTP
 * code, or has to set TOTP up first ({@code setupRequired}). The principal name is the user id, so ending all
 * sessions of the user also ends this one.
 */
public record MfaPending(UUID userId, boolean setupRequired, Instant startedAt) implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public String getName() {
        return userId.toString();
    }
}
