package de.elternstunden.overview.service;

import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AppUserPrincipal;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.family.domain.Child;
import de.elternstunden.family.domain.Member;
import de.elternstunden.family.service.FamilyService;
import de.elternstunden.schoolyear.domain.SchoolYear;
import de.elternstunden.schoolyear.service.SchoolYearService;
import de.elternstunden.timeentry.domain.EntryView;
import de.elternstunden.timeentry.repo.TimeEntryRepository;
import de.elternstunden.timeentry.service.EntryService;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Start page of a family (FS-FAM 7.3): progress in a school year, children, members, partner hint and entries. Only
 * confirmed minutes count (D-10); the family always comes from the session (INV-10).
 */
@Service
public class FamilyOverviewService {

    public record Overview(
            SchoolYear schoolYear,
            List<SchoolYear> schoolYears,
            boolean obligated,
            TimeEntryRepository.Sums sums,
            boolean canAddEntries,
            List<Child> children,
            List<Member> activeMembers,
            List<EntryView> entries) {

        public int remainingMinutes() {
            return Math.max(schoolYear.requiredMinutes() - sums.confirmedMinutes(), 0);
        }

        public boolean fulfilled() {
            return sums.confirmedMinutes() >= schoolYear.requiredMinutes();
        }

        /** 7.3 rule 6: exactly one active member. */
        public boolean partnerHint() {
            return activeMembers.size() == 1;
        }
    }

    private final SchoolYearService schoolYears;
    private final FamilyService families;
    private final EntryService entries;
    private final Clock clock;

    public FamilyOverviewService(
            SchoolYearService schoolYears, FamilyService families, EntryService entries, Clock clock) {
        this.schoolYears = schoolYears;
        this.families = families;
        this.entries = entries;
        this.clock = clock;
    }

    public Overview get(AppUserPrincipal user, @Nullable UUID schoolYearId) {
        UUID familyId = EntryService.requireFamily(user);
        List<SchoolYear> started = schoolYears.started();
        SchoolYear year = schoolYear(started, schoolYearId);
        boolean canAdd = !year.closed() && year.contains(BusinessTime.today(clock));
        return new Overview(
                year,
                started,
                families.obligated(familyId, year.id()),
                entries.sums(familyId, year.id()),
                canAdd,
                families.childrenOf(familyId),
                families.membersOf(familyId).stream()
                        .filter(member -> member.status() == UserStatus.ACTIVE)
                        .toList(),
                entries.ofFamily(familyId, year.id()));
    }

    public List<EntryView> entries(AppUserPrincipal user, @Nullable UUID schoolYearId) {
        UUID familyId = EntryService.requireFamily(user);
        return entries.ofFamily(
                familyId, schoolYear(schoolYears.started(), schoolYearId).id());
    }

    /** Without an id the current school year (5.5 rule 4); school years that have not started are not visible. */
    private SchoolYear schoolYear(List<SchoolYear> started, @Nullable UUID schoolYearId) {
        if (schoolYearId == null) {
            return schoolYears.current().orElseThrow(() -> new ApiException(ErrorCode.NO_CURRENT_SCHOOL_YEAR));
        }
        return started.stream()
                .filter(year -> year.id().equals(schoolYearId))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
