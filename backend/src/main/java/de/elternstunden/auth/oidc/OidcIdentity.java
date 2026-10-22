package de.elternstunden.auth.oidc;

import org.jspecify.annotations.Nullable;

/** What a provider confirmed after the callback (ID token validated by Spring: signature, iss, aud, exp, nonce). */
public record OidcIdentity(
        String provider,
        String issuer,
        String subject,
        @Nullable String email,
        boolean emailVerified,
        @Nullable String name,
        @Nullable String hostedDomain) {}
