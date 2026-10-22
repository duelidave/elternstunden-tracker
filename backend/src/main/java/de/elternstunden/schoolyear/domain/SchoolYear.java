package de.elternstunden.schoolyear.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A school year with cut-off dates and required minutes (5.5, D-01, D-02). */
public record SchoolYear(
        UUID id,
        String label,
        LocalDate startsOn,
        LocalDate endsOn,
        int requiredMinutes,
        @Nullable Instant closedAt) {

    public boolean closed() {
        return closedAt != null;
    }

    /** {@code starts_on <= date <= ends_on} (5.5 rule 2). */
    public boolean contains(LocalDate date) {
        return !date.isBefore(startsOn) && !date.isAfter(endsOn);
    }
}
