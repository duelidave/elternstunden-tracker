package de.elternstunden.common.security;

/**
 * Authorities beyond the roles (8.2): every full login has {@link #USER}; the half-finished logins have only their
 * own authority, so they reach nothing but their few endpoints (OP and MP in 8.2).
 */
public final class Authorities {

    /** x-roles U: any full login. */
    public static final String USER = "ROLE_USER";
    /** x-roles OP: after the provider login without an account (7.1.5 step 4). */
    public static final String OIDC_PENDING = "ROLE_OIDC_PENDING";
    /** x-roles MP: an admin after the first factor (7.2). */
    public static final String MFA_PENDING = "ROLE_MFA_PENDING";

    private Authorities() {}
}
