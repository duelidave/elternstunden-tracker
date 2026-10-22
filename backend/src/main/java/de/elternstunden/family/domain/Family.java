package de.elternstunden.family.domain;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A family: children plus the accounts assigned to it (D-03). */
public record Family(
        UUID id, @Nullable String label, @Nullable String note) {}
