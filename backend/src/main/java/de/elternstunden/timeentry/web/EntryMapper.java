package de.elternstunden.timeentry.web;

import de.elternstunden.api.model.AdminEntryDto;
import de.elternstunden.api.model.EntryCreateDto;
import de.elternstunden.api.model.EntryStatusDto;
import de.elternstunden.api.model.EntryUpdateDto;
import de.elternstunden.api.model.FamilyEntryDto;
import de.elternstunden.timeentry.domain.EntryChange;
import de.elternstunden.timeentry.domain.EntryContent;
import de.elternstunden.timeentry.domain.EntryView;
import de.elternstunden.timeentry.domain.TimeEntry;
import de.elternstunden.timeentry.service.EntryService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;

/** API models of entries to domain and back; entities never leave the web layer (4.4). */
public final class EntryMapper {

    private EntryMapper() {}

    public static EntryContent content(EntryCreateDto body) {
        return new EntryContent(
                body.getWorkDate(), time(body.getStartTime()), body.getDurationMinutes(), body.getDescription());
    }

    /** Merge patch (8.1): a missing field keeps the value, an empty start time removes it. */
    public static EntryChange change(EntryUpdateDto body) {
        return new EntryChange(
                body.getWorkDate(),
                body.getStartTime() != null,
                time(body.getStartTime()),
                body.getDurationMinutes(),
                body.getDescription());
    }

    /** For the family's own view (7.3 rule 5); {@code writableYear}: the family may still write in its school year. */
    public static FamilyEntryDto family(EntryView view, boolean writableYear) {
        TimeEntry entry = view.entry();
        boolean changeable = EntryService.changeableByFamily(view, writableYear);
        FamilyEntryDto dto = new FamilyEntryDto(
                entry.id(),
                entry.workDate(),
                entry.durationMinutes(),
                entry.description(),
                EntryStatusDto.fromValue(entry.status().name()),
                view.createdByName(),
                changeable,
                changeable,
                entry.version());
        dto.setStartTime(text(entry.startTime()));
        return dto;
    }

    static AdminEntryDto admin(EntryView view, String familyLabel) {
        TimeEntry entry = view.entry();
        AdminEntryDto dto = new AdminEntryDto(
                entry.id(),
                entry.familyId(),
                familyLabel,
                entry.schoolYearId(),
                view.schoolYearClosed(),
                entry.workDate(),
                entry.durationMinutes(),
                entry.description(),
                EntryStatusDto.fromValue(entry.status().name()),
                view.createdByName(),
                utc(entry.createdAt()),
                entry.version());
        dto.setStartTime(text(entry.startTime()));
        dto.setConfirmedAt(entry.confirmedAt() == null ? null : utc(entry.confirmedAt()));
        dto.setConfirmedByDisplayName(view.confirmedByName());
        return dto;
    }

    private static @Nullable LocalTime time(@Nullable String value) {
        return value == null || value.isEmpty() ? null : LocalTime.parse(value);
    }

    private static @Nullable String text(@Nullable LocalTime time) {
        return time == null ? null : time.toString();
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
