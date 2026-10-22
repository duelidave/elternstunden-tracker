package de.elternstunden.timeentry.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** One entry of parent hours (D-09, D-10). Soft-deleted entries never leave the repository. */
public record TimeEntry(
        UUID id,
        UUID familyId,
        UUID schoolYearId,
        UUID createdBy,
        LocalDate workDate,
        @Nullable LocalTime startTime,
        int durationMinutes,
        String description,
        EntryStatus status,
        @Nullable UUID confirmedBy,
        @Nullable Instant confirmedAt,
        Instant createdAt,
        int version) {}
