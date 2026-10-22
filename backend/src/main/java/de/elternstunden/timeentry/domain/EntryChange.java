package de.elternstunden.timeentry.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/**
 * A merge patch of an entry (8.1): {@code null} keeps the value. The start time is only touched with
 * {@code startTimeGiven}; then {@code null} removes it.
 */
public record EntryChange(
        @Nullable LocalDate workDate,
        boolean startTimeGiven,
        @Nullable LocalTime startTime,
        @Nullable Integer durationMinutes,
        @Nullable String description) {

    public EntryContent applyTo(EntryContent current) {
        return new EntryContent(
                workDate == null ? current.workDate() : workDate,
                startTimeGiven ? startTime : current.startTime(),
                durationMinutes == null ? current.durationMinutes() : durationMinutes,
                description == null ? current.description() : description);
    }
}
