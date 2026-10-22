package de.elternstunden.overview.service;

import de.elternstunden.common.security.AdminOnly;
import de.elternstunden.family.service.FamilyService;
import de.elternstunden.overview.repo.DashboardRepository;
import de.elternstunden.timeentry.service.EntryService;
import de.elternstunden.user.service.UserAdminService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Widgets of the admin start page (7.6): unassigned users, new automatic assignments of the last 14 days and the
 * number of open entries (7.5 point 6).
 */
@Service
@AdminOnly
public class DashboardService {

    static final Duration RECENT = Duration.ofDays(14);

    /** An automatic assignment with the family's display label. */
    public record RecentAssignment(DashboardRepository.AutoAssignment assignment, String familyLabel) {}

    public record Dashboard(
            List<UserAdminService.UserDetail> unassigned, List<RecentAssignment> recent, int openEntryCount) {}

    private final UserAdminService users;
    private final FamilyService families;
    private final DashboardRepository dashboard;
    private final EntryService entries;
    private final Clock clock;

    public DashboardService(
            UserAdminService users,
            FamilyService families,
            DashboardRepository dashboard,
            EntryService entries,
            Clock clock) {
        this.users = users;
        this.families = families;
        this.dashboard = dashboard;
        this.entries = entries;
        this.clock = clock;
    }

    public Dashboard get() {
        Map<UUID, String> labels = families.displayLabels();
        List<RecentAssignment> recent = dashboard
                .autoAssignmentsSince(clock.instant().minus(RECENT))
                .stream()
                .map(assignment -> new RecentAssignment(assignment, labels.getOrDefault(assignment.familyId(), "")))
                .toList();
        return new Dashboard(users.unassigned(), recent, entries.countOpen());
    }
}
