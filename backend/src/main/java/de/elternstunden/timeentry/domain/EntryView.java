package de.elternstunden.timeentry.domain;

import org.jspecify.annotations.Nullable;

/** An entry with the names and the school year state needed for display. */
public record EntryView(
        TimeEntry entry, String createdByName, @Nullable String confirmedByName, boolean schoolYearClosed) {}
