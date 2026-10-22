package de.elternstunden.timeentry.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.ratelimit.RateLimiter;
import de.elternstunden.common.security.AdminOnly;
import de.elternstunden.common.security.AppUserPrincipal;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.family.service.FamilyService;
import de.elternstunden.schoolyear.domain.SchoolYear;
import de.elternstunden.schoolyear.service.SchoolYearService;
import de.elternstunden.timeentry.domain.EntryChange;
import de.elternstunden.timeentry.domain.EntryContent;
import de.elternstunden.timeentry.domain.EntryStatus;
import de.elternstunden.timeentry.domain.EntryView;
import de.elternstunden.timeentry.domain.TimeEntry;
import de.elternstunden.timeentry.repo.TimeEntryRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Entering, changing and confirming parent hours (FS-ENTRY 7.4, FS-CONF 7.5). Every rule is enforced here, never only
 * in the client. Family members only ever reach entries of their own family (INV-02, INV-10); the family comes from
 * the session, never from the request.
 */
@Service
public class EntryService {

    /** Duplicate protection (7.4 rule 5, [PROPOSED]). */
    static final Duration DUPLICATE_WINDOW = Duration.ofMinutes(2);

    // OPEN-10: 15-minute grid with an upper bound of 720 minutes
    static final int STEP_MINUTES = 15;
    static final int MAX_MINUTES = 720;
    static final int MIN_DESCRIPTION = 3;
    static final int MAX_DESCRIPTION = 500;

    /** Result of a bulk confirmation (7.5 point 4). */
    public record Confirmation(int confirmed, int skipped) {}

    private final TimeEntryRepository entries;
    private final SchoolYearService schoolYears;
    private final FamilyService families;
    private final RateLimiter rateLimiter;
    private final AppProperties.RateLimits limits;
    private final AuditLog audit;
    private final Clock clock;

    public EntryService(
            TimeEntryRepository entries,
            SchoolYearService schoolYears,
            FamilyService families,
            RateLimiter rateLimiter,
            AppProperties properties,
            AuditLog audit,
            Clock clock) {
        this.entries = entries;
        this.schoolYears = schoolYears;
        this.families = families;
        this.rateLimiter = rateLimiter;
        this.limits = properties.rateLimits();
        this.audit = audit;
        this.clock = clock;
    }

    /** The family of a family member; unassigned accounts see no family data (INV-10, 7.3 rule 8). */
    public static UUID requireFamily(AppUserPrincipal user) {
        UUID familyId = user.familyId();
        if (familyId == null) {
            throw new ApiException(ErrorCode.FAMILY_ASSIGNMENT_PENDING);
        }
        return familyId;
    }

    /** Entries of a family in a school year, newest first (7.3 rule 3). */
    public List<EntryView> ofFamily(UUID familyId, UUID schoolYearId) {
        return entries.findByFamilyAndYear(familyId, schoolYearId);
    }

    public TimeEntryRepository.Sums sums(UUID familyId, UUID schoolYearId) {
        return entries.sums(familyId, schoolYearId);
    }

    /** INV-04 to INV-06: only open entries of open school years. */
    public static boolean changeableByFamily(EntryView view) {
        return view.entry().status() == EntryStatus.OPEN && !view.schoolYearClosed();
    }

    @Transactional
    public EntryView createForOwnFamily(AppUserPrincipal user, EntryContent content) {
        return create(user.userId(), requireFamily(user), content);
    }

    /**
     * Version before status (AC-ENTRY-12): a patch based on an old state always fails with CONCURRENT_MODIFICATION,
     * also if the entry was confirmed meanwhile.
     */
    @Transactional
    public EntryView updateOwn(AppUserPrincipal user, UUID id, int version, EntryChange change) {
        TimeEntry entry = lockOwn(user, id);
        requireVersion(entry, version);
        requireOpenForFamily(entry);
        return update(user.userId(), entry, change);
    }

    @Transactional
    public void deleteOwn(AppUserPrincipal user, UUID id) {
        TimeEntry entry = lockOwn(user, id);
        requireOpenForFamily(entry);
        delete(user.userId(), entry);
    }

    /** Admin list (7.5 point 1): oldest creation first. */
    @AdminOnly
    public List<EntryView> search(
            @Nullable EntryStatus status, @Nullable UUID familyId, @Nullable LocalDate from, @Nullable LocalDate to) {
        return entries.search(status, familyId, from, to);
    }

    @AdminOnly
    public int countOpen() {
        return entries.countOpen();
    }

    @Transactional
    @AdminOnly
    public EntryView createForFamily(UUID actor, UUID familyId, EntryContent content) {
        if (!families.exists(familyId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return create(actor, familyId, content);
    }

    /** Admin correction; a changed confirmed entry becomes OPEN again (INV-07). */
    @Transactional
    @AdminOnly
    public EntryView update(UUID actor, UUID id, int version, EntryChange change) {
        TimeEntry entry = lock(id);
        requireVersion(entry, version);
        return update(actor, entry, change);
    }

    @Transactional
    @AdminOnly
    public void delete(UUID actor, UUID id) {
        delete(actor, lock(id));
    }

    /** Idempotent (7.5 point 3); closed school year: 409 (7.5 point 7). */
    @Transactional
    @AdminOnly
    public EntryView confirm(UUID actor, UUID id) {
        TimeEntry entry = lock(id);
        requireYearOpen(entry.schoolYearId());
        if (entry.status() == EntryStatus.OPEN) {
            entries.confirm(List.of(id), actor, clock.instant());
            audit.user(actor, "ENTRY_CONFIRMED", "time_entry", id);
        }
        return view(id);
    }

    /** Already confirmed, unknown and entries of closed years are skipped (7.5 point 4). */
    @Transactional
    @AdminOnly
    public Confirmation confirm(UUID actor, List<UUID> ids) {
        List<UUID> distinct = List.copyOf(new LinkedHashSet<>(ids));
        List<UUID> confirmed = entries.confirm(distinct, actor, clock.instant());
        confirmed.forEach(id -> audit.user(actor, "ENTRY_CONFIRMED", "time_entry", id));
        return new Confirmation(confirmed.size(), distinct.size() - confirmed.size());
    }

    private EntryView create(UUID actor, UUID familyId, EntryContent input) {
        rateLimiter.acquireOrThrow("entry-user", actor.toString(), limits.entryPerUser());
        EntryContent content = normalize(input);
        SchoolYear year = schoolYearFor(content.workDate());
        Instant now = clock.instant();
        // Check and insert under one lock per family, so a double click creates one entry (AC-ENTRY-11)
        entries.lockFamily(familyId);
        if (entries.duplicateSince(familyId, content, now.minus(DUPLICATE_WINDOW))) {
            throw new ApiException(ErrorCode.DUPLICATE_ENTRY);
        }
        UUID id = entries.insert(familyId, year.id(), actor, content, now);
        audit.write(
                actor,
                AuditLog.ActorKind.USER,
                "ENTRY_CREATED",
                "time_entry",
                id,
                Map.of("familyId", familyId.toString()));
        return view(id);
    }

    private EntryView update(UUID actor, TimeEntry entry, EntryChange change) {
        requireYearOpen(entry.schoolYearId());
        EntryContent current = content(entry);
        EntryContent changed = normalize(change.applyTo(current));
        if (changed.equals(current)) {
            return view(entry.id());
        }
        SchoolYear year = schoolYearFor(changed.workDate());
        boolean reopen = entry.status() == EntryStatus.CONFIRMED;
        entries.updateContent(entry.id(), year.id(), changed, reopen, clock.instant());
        audit.write(
                actor,
                AuditLog.ActorKind.USER,
                "ENTRY_UPDATED",
                "time_entry",
                entry.id(),
                Map.of("version", entry.version() + 1, "reopened", reopen));
        return view(entry.id());
    }

    private void delete(UUID actor, TimeEntry entry) {
        requireYearOpen(entry.schoolYearId());
        entries.softDelete(entry.id(), clock.instant());
        audit.user(actor, "ENTRY_DELETED", "time_entry", entry.id());
    }

    /** Entries of other families do not exist for a family member (AC-ENTRY-07, SEC-08). */
    private TimeEntry lockOwn(AppUserPrincipal user, UUID id) {
        UUID familyId = requireFamily(user);
        TimeEntry entry = lock(id);
        if (!entry.familyId().equals(familyId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        return entry;
    }

    private TimeEntry lock(UUID id) {
        return entries.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private EntryView view(UUID id) {
        return entries.findView(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static void requireVersion(TimeEntry entry, int version) {
        if (entry.version() != version) {
            throw new ApiException(ErrorCode.CONCURRENT_MODIFICATION);
        }
    }

    private static void requireOpenForFamily(TimeEntry entry) {
        if (entry.status() == EntryStatus.CONFIRMED) {
            throw new ApiException(ErrorCode.ENTRY_CONFIRMED);
        }
    }

    private void requireYearOpen(UUID schoolYearId) {
        if (schoolYears.get(schoolYearId).closed()) {
            throw new ApiException(ErrorCode.SCHOOL_YEAR_CLOSED);
        }
    }

    /** INV-01, INV-03, INV-04: not in the future, in an existing and open school year. */
    private SchoolYear schoolYearFor(LocalDate workDate) {
        if (workDate.isAfter(BusinessTime.today(clock))) {
            throw new ApiException(ErrorCode.DATE_IN_FUTURE);
        }
        SchoolYear year = schoolYears.forDate(workDate).orElseThrow(() -> new ApiException(ErrorCode.NO_SCHOOL_YEAR));
        if (year.closed()) {
            throw new ApiException(ErrorCode.SCHOOL_YEAR_CLOSED);
        }
        return year;
    }

    /** 7.4 rules 1 and 2: description trimmed to 3 to 500 characters, duration on the 15-minute grid. */
    static EntryContent normalize(EntryContent content) {
        String description = content.description().strip();
        // Characters as the database counts them (char_length), not UTF-16 units
        int length = description.codePointCount(0, description.length());
        if (length < MIN_DESCRIPTION || length > MAX_DESCRIPTION) {
            throw ApiException.validation("description", "Size", "must be 3 to 500 characters after trimming");
        }
        int duration = content.durationMinutes();
        if (duration < STEP_MINUTES || duration > MAX_MINUTES || duration % STEP_MINUTES != 0) {
            throw ApiException.validation("durationMinutes", "MultipleOf", "must be 15 to 720 in steps of 15");
        }
        return new EntryContent(content.workDate(), content.startTime(), duration, description);
    }

    private static EntryContent content(TimeEntry entry) {
        return new EntryContent(entry.workDate(), entry.startTime(), entry.durationMinutes(), entry.description());
    }
}
