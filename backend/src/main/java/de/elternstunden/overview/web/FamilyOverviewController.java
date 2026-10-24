package de.elternstunden.overview.web;

import de.elternstunden.api.MyFamilyApi;
import de.elternstunden.api.model.EntryCreateDto;
import de.elternstunden.api.model.EntryUpdateDto;
import de.elternstunden.api.model.FamilyChildDto;
import de.elternstunden.api.model.FamilyEntryDto;
import de.elternstunden.api.model.FamilyMemberNameDto;
import de.elternstunden.api.model.FamilyOverviewDto;
import de.elternstunden.api.model.SchoolYearRefDto;
import de.elternstunden.common.security.AppUserPrincipal;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.overview.service.FamilyOverviewService;
import de.elternstunden.schoolyear.domain.SchoolYear;
import de.elternstunden.timeentry.service.EntryService;
import de.elternstunden.timeentry.web.EntryMapper;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The family's own start page and entries (FS-FAM 7.3, FS-ENTRY 7.4). */
@RestController
public class FamilyOverviewController implements MyFamilyApi {

    private final FamilyOverviewService overview;
    private final EntryService entries;

    public FamilyOverviewController(FamilyOverviewService overview, EntryService entries) {
        this.overview = overview;
        this.entries = entries;
    }

    @Override
    public ResponseEntity<FamilyOverviewDto> getFamilyOverview(@Nullable UUID schoolYearId) {
        AppUserPrincipal user = CurrentUser.principal();
        FamilyOverviewService.Overview current = overview.get(user, schoolYearId);
        return ResponseEntity.ok(new FamilyOverviewDto(
                schoolYear(current.schoolYear()),
                current.schoolYears().stream()
                        .map(FamilyOverviewController::schoolYear)
                        .toList(),
                current.obligated(),
                current.schoolYear().requiredMinutes(),
                current.sums().confirmedMinutes(),
                current.sums().openMinutes(),
                current.remainingMinutes(),
                current.fulfilled(),
                current.canAddEntries(),
                // 7.3 rule 7: first names and display names only
                current.children().stream()
                        .map(child -> new FamilyChildDto(child.firstName()))
                        .toList(),
                current.activeMembers().stream()
                        .map(member -> new FamilyMemberNameDto(
                                member.displayName(), member.id().equals(user.userId())))
                        .toList(),
                current.partnerHint(),
                current.entries().stream()
                        .map(view -> EntryMapper.family(view, current.canAddEntries()))
                        .toList()));
    }

    @Override
    public ResponseEntity<List<FamilyEntryDto>> listFamilyEntries(@Nullable UUID schoolYearId) {
        FamilyOverviewService.Entries result = overview.entries(CurrentUser.principal(), schoolYearId);
        return ResponseEntity.ok(result.entries().stream()
                .map(view -> EntryMapper.family(view, result.writable()))
                .toList());
    }

    @Override
    public ResponseEntity<FamilyEntryDto> createFamilyEntry(EntryCreateDto body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                // Only possible in the running school year, so the entry stays changeable (INV-14)
                .body(EntryMapper.family(
                        entries.createForOwnFamily(CurrentUser.principal(), EntryMapper.content(body)), true));
    }

    @Override
    public ResponseEntity<FamilyEntryDto> updateFamilyEntry(UUID entryId, EntryUpdateDto body) {
        return ResponseEntity.ok(EntryMapper.family(
                entries.updateOwn(CurrentUser.principal(), entryId, body.getVersion(), EntryMapper.change(body)),
                true));
    }

    @Override
    public ResponseEntity<Void> deleteFamilyEntry(UUID entryId) {
        entries.deleteOwn(CurrentUser.principal(), entryId);
        return ResponseEntity.noContent().build();
    }

    private static SchoolYearRefDto schoolYear(SchoolYear year) {
        return new SchoolYearRefDto(year.id(), year.label(), year.startsOn(), year.endsOn(), year.closed());
    }
}
