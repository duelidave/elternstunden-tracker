package de.elternstunden.family.domain;

import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A child of the Hort (D-03). Never deleted, only anonymized later (DP-05). */
public record Child(
        UUID id,
        UUID familyId,
        String firstName,
        String lastName,
        LocalDate birthDate,
        @Nullable String externalRef) {}
