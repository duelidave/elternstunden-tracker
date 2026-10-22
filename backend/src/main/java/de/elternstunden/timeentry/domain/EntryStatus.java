package de.elternstunden.timeentry.domain;

/** Stored as the PostgreSQL enum {@code entry_status}; only confirmed minutes count (D-10). */
public enum EntryStatus {
    OPEN,
    CONFIRMED
}
