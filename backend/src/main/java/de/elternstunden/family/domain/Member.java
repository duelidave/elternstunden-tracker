package de.elternstunden.family.domain;

import de.elternstunden.auth.domain.UserStatus;
import java.util.UUID;

/** An account assigned to a family, as admins see it. */
public record Member(UUID id, String displayName, String email, UserStatus status) {}
