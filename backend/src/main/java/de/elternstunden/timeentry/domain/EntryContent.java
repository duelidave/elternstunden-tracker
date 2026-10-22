package de.elternstunden.timeentry.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import org.jspecify.annotations.Nullable;

/** The changeable content of an entry (7.4): date, optional start time, duration, text. */
public record EntryContent(
        LocalDate workDate, @Nullable LocalTime startTime, int durationMinutes, String description) {}
