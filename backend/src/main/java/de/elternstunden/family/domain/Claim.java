package de.elternstunden.family.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The child data a user entered for the assignment ({@code registration_claim}, 7.1.2). */
public record Claim(
        UUID id,
        UUID userId,
        String firstName,
        String lastName,
        LocalDate birthDate,
        MatchResult result,
        @Nullable UUID matchedChildId,
        @Nullable String bindingHash,
        Instant createdAt) {}
