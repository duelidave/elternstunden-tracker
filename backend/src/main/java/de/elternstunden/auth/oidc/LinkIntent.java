package de.elternstunden.auth.oidc;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/** Session note that the next provider callback links an identity to this account (7.2 "Profil"). */
public record LinkIntent(UUID userId, String provider, Instant createdAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String ATTRIBUTE = LinkIntent.class.getName();
}
