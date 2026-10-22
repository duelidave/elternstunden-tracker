package de.elternstunden.schoolyear.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AdminOnly;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.schoolyear.domain.SchoolYear;
import de.elternstunden.schoolyear.repo.SchoolYearRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * School years (5.5, FS-ADM 7.7). Overlaps and entries outside new cut-off dates are rejected (INV-09); a closed year
 * is read-only until an admin reopens it (OPEN-06 interim default).
 */
@Service
public class SchoolYearService {

    private final SchoolYearRepository schoolYears;
    private final AuditLog audit;
    private final Clock clock;

    public SchoolYearService(SchoolYearRepository schoolYears, AuditLog audit, Clock clock) {
        this.schoolYears = schoolYears;
        this.audit = audit;
        this.clock = clock;
    }

    /** Newest first. */
    @AdminOnly
    public List<SchoolYear> list() {
        return schoolYears.findAll();
    }

    public SchoolYear get(UUID id) {
        return schoolYears.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /**
     * The current school year (5.5 rule 4): the one containing today (Europe/Berlin); in a gap between two years the
     * one that ended last. Empty if there is neither.
     */
    public Optional<SchoolYear> current() {
        return current(schoolYears.findAll(), BusinessTime.today(clock));
    }

    static Optional<SchoolYear> current(List<SchoolYear> all, LocalDate today) {
        Optional<SchoolYear> containing =
                all.stream().filter(year -> year.contains(today)).findFirst();
        if (containing.isPresent()) {
            return containing;
        }
        return all.stream().filter(year -> year.endsOn().isBefore(today)).max(Comparator.comparing(SchoolYear::endsOn));
    }

    /** School years that have started (Europe/Berlin), newest first: the ones a family can look at (7.3 rule 2). */
    public List<SchoolYear> started() {
        LocalDate today = BusinessTime.today(clock);
        return schoolYears.findAll().stream()
                .filter(year -> !year.startsOn().isAfter(today))
                .toList();
    }

    /** The school year a work date belongs to (5.5 rule 2, INV-01). */
    public Optional<SchoolYear> forDate(LocalDate date) {
        return schoolYears.findAll().stream()
                .filter(year -> year.contains(date))
                .findFirst();
    }

    @Transactional
    @AdminOnly
    public SchoolYear create(UUID actor, String label, LocalDate startsOn, LocalDate endsOn, int requiredMinutes) {
        String trimmed = label.strip();
        validate(trimmed, startsOn, endsOn, null);
        UUID id = schoolYears.insert(trimmed, startsOn, endsOn, requiredMinutes, clock.instant());
        audit.user(actor, "SCHOOL_YEAR_CREATED", "school_year", id);
        return get(id);
    }

    @Transactional
    @AdminOnly
    public SchoolYear update(
            UUID actor,
            UUID id,
            @Nullable String label,
            @Nullable LocalDate startsOn,
            @Nullable LocalDate endsOn,
            @Nullable Integer requiredMinutes) {
        SchoolYear year = schoolYears.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        requireOpen(year);
        String newLabel = label == null ? year.label() : label.strip();
        LocalDate newStart = startsOn == null ? year.startsOn() : startsOn;
        LocalDate newEnd = endsOn == null ? year.endsOn() : endsOn;
        int newRequired = requiredMinutes == null ? year.requiredMinutes() : requiredMinutes;
        validate(newLabel, newStart, newEnd, id);
        if (schoolYears.hasEntriesOutside(id, newStart, newEnd)) {
            throw new ApiException(ErrorCode.ENTRIES_OUTSIDE_RANGE);
        }
        schoolYears.update(id, newLabel, newStart, newEnd, newRequired);
        audit.write(
                actor,
                AuditLog.ActorKind.USER,
                "SCHOOL_YEAR_UPDATED",
                "school_year",
                id,
                Map.of("startsOn", newStart.toString(), "endsOn", newEnd.toString(), "requiredMinutes", newRequired));
        return get(id);
    }

    @Transactional
    @AdminOnly
    public SchoolYear close(UUID actor, UUID id) {
        SchoolYear year = schoolYears.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!year.closed()) {
            schoolYears.setClosedAt(id, clock.instant());
            audit.user(actor, "SCHOOL_YEAR_CLOSED", "school_year", id);
        }
        return get(id);
    }

    @Transactional
    @AdminOnly
    public SchoolYear reopen(UUID actor, UUID id) {
        SchoolYear year = schoolYears.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (year.closed()) {
            schoolYears.setClosedAt(id, null);
            audit.user(actor, "SCHOOL_YEAR_REOPENED", "school_year", id);
        }
        return get(id);
    }

    /** "Einschreibung aus Vorjahr übernehmen" (7.7): enrolls the children of the previous year in this one. */
    @Transactional
    @AdminOnly
    public int copyPreviousEnrollments(UUID actor, UUID id) {
        SchoolYear year = schoolYears.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        requireOpen(year);
        Optional<SchoolYear> previous = schoolYears.findPrevious(year.startsOn());
        if (previous.isEmpty()) {
            return 0;
        }
        int copied = schoolYears.copyEnrollments(previous.get().id(), id);
        audit.write(
                actor,
                AuditLog.ActorKind.USER,
                "ENROLLMENTS_COPIED",
                "school_year",
                id,
                Map.of("fromSchoolYearId", previous.get().id().toString(), "copied", copied));
        return copied;
    }

    /** For changes that belong to a school year, e.g. enrollments (OPEN-06: closed = read-only for everyone). */
    public SchoolYear requireOpen(UUID id) {
        SchoolYear year = get(id);
        requireOpen(year);
        return year;
    }

    private static void requireOpen(SchoolYear year) {
        if (year.closed()) {
            throw new ApiException(ErrorCode.SCHOOL_YEAR_CLOSED);
        }
    }

    private void validate(String label, LocalDate startsOn, LocalDate endsOn, @Nullable UUID exceptId) {
        if (label.isEmpty()) {
            throw ApiException.validation("label", "NotBlank", "must not be blank");
        }
        if (!endsOn.isAfter(startsOn)) {
            throw ApiException.validation("endsOn", "DateRange", "must be after startsOn");
        }
        if (schoolYears.labelTaken(label, exceptId)) {
            throw new ApiException(ErrorCode.SCHOOL_YEAR_LABEL_TAKEN);
        }
        if (schoolYears.overlapsOther(startsOn, endsOn, exceptId)) {
            throw new ApiException(ErrorCode.SCHOOL_YEAR_OVERLAP);
        }
    }
}
