package de.elternstunden.overview.web;

import de.elternstunden.api.DashboardApi;
import de.elternstunden.api.model.AdminDashboardDto;
import de.elternstunden.api.model.AutoAssignmentDto;
import de.elternstunden.family.service.FamilyService;
import de.elternstunden.overview.service.DashboardService;
import de.elternstunden.user.web.UserMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Admin start page (7.6). */
@RestController
public class DashboardController implements DashboardApi {

    private final DashboardService dashboard;
    private final FamilyService families;

    public DashboardController(DashboardService dashboard, FamilyService families) {
        this.dashboard = dashboard;
        this.families = families;
    }

    @Override
    public ResponseEntity<AdminDashboardDto> getAdminDashboard() {
        DashboardService.Dashboard current = dashboard.get();
        Map<UUID, String> labels = families.displayLabels();
        return ResponseEntity.ok(new AdminDashboardDto(
                current.openEntryCount(),
                current.unassigned().stream()
                        .map(detail -> UserMapper.detail(detail, labels))
                        .toList(),
                current.recent().stream()
                        .map(recent -> new AutoAssignmentDto(
                                recent.assignment().userId(),
                                recent.assignment().displayName(),
                                recent.assignment().familyId(),
                                recent.familyLabel(),
                                UserMapper.utc(recent.assignment().assignedAt())))
                        .toList()));
    }
}
