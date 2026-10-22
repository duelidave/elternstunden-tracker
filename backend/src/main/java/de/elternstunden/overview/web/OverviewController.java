package de.elternstunden.overview.web;

import de.elternstunden.api.OverviewApi;
import de.elternstunden.api.model.ChildNameDto;
import de.elternstunden.api.model.OverviewPageDto;
import de.elternstunden.api.model.OverviewRowDto;
import de.elternstunden.api.model.SchoolYearRefDto;
import de.elternstunden.common.web.Paging;
import de.elternstunden.overview.service.AdminOverviewService;
import de.elternstunden.schoolyear.domain.SchoolYear;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Admin overview with the counts for the hint bar (FS-ADMOV 7.6). */
@RestController
public class OverviewController implements OverviewApi {

    private final AdminOverviewService overview;

    public OverviewController(AdminOverviewService overview) {
        this.overview = overview;
    }

    @Override
    public ResponseEntity<OverviewPageDto> getAdminOverview(
            @Nullable UUID schoolYearId,
            @Nullable String q,
            Boolean onlyMissing,
            String sort,
            Integer page,
            Integer size) {
        Paging paging = Paging.of(page, size);
        AdminOverviewService.Overview result = overview.get(schoolYearId, q, onlyMissing, sort);
        SchoolYear year = result.schoolYear();
        return ResponseEntity.ok(new OverviewPageDto(
                new SchoolYearRefDto(year.id(), year.label(), year.startsOn(), year.endsOn(), year.closed()),
                year.requiredMinutes(),
                result.familyCount(),
                result.missingCount(),
                paging.slice(result.rows()).stream()
                        .map(OverviewController::row)
                        .toList(),
                page,
                size,
                (long) result.rows().size(),
                paging.totalPages(result.rows().size())));
    }

    private static OverviewRowDto row(AdminOverviewService.Row row) {
        return new OverviewRowDto(
                row.family().family().id(),
                row.family().displayLabel(),
                row.family().children().stream()
                        .map(child -> new ChildNameDto(child.id(), child.firstName(), child.lastName()))
                        .toList(),
                row.family().activeMembers(),
                row.minutes().confirmedMinutes(),
                row.minutes().openMinutes(),
                row.minutes().missingMinutes());
    }
}
