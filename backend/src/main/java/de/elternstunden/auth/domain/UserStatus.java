package de.elternstunden.auth.domain;

/** Account status; stored as the PostgreSQL enum {@code user_status}. */
public enum UserStatus {
    /** Registered, magic link not redeemed yet: no access (7.1.4). */
    PENDING_VERIFICATION,
    ACTIVE,
    DISABLED
}
