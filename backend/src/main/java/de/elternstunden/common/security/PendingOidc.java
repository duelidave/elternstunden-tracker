package de.elternstunden.common.security;

import java.io.Serial;
import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;

/**
 * Session state OIDC_PENDING (7.2): the provider confirmed the person, but there is no account yet. Only the data
 * needed for "Noch ein Schritt"; no provider tokens (SEC-06).
 */
public record PendingOidc(
        String provider, String issuer, String subject, String email, String displayName, Instant startedAt)
        implements Principal, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** No personal data in the session index (SEC-12). */
    @Override
    public String getName() {
        return "oidc-pending";
    }
}
