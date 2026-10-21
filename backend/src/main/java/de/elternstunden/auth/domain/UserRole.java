package de.elternstunden.auth.domain;

/** Roles (D-13); stored as the PostgreSQL enum {@code user_role}. */
public enum UserRole {
    FAMILY_MEMBER,
    ADMIN,
    MASTER_ADMIN;

    /** Spring Security authority, e.g. {@code ROLE_ADMIN}. MASTER_ADMIN also holds ADMIN rights (Lastenheft 6). */
    public String authority() {
        return "ROLE_" + name();
    }
}
