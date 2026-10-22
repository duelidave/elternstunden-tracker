package de.elternstunden.family.web;

import de.elternstunden.api.ChildrenApi;
import de.elternstunden.api.model.AdminChildDto;
import de.elternstunden.api.model.ChildCreateDto;
import de.elternstunden.api.model.ChildPageDto;
import de.elternstunden.api.model.ChildUpdateDto;
import de.elternstunden.api.model.ImportErrorDto;
import de.elternstunden.api.model.ImportResultDto;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.common.web.Paging;
import de.elternstunden.family.service.ChildImportService;
import de.elternstunden.family.service.ChildService;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Children, enrollments and CSV import for admins (FS-ADM 7.7). */
@RestController
public class ChildController implements ChildrenApi {

    private final ChildService children;
    private final ChildImportService imports;

    public ChildController(ChildService children, ChildImportService imports) {
        this.children = children;
        this.imports = imports;
    }

    @Override
    public ResponseEntity<ChildPageDto> listChildren(
            @Nullable String q, @Nullable UUID schoolYearId, Integer page, Integer size) {
        Paging paging = Paging.of(page, size);
        List<ChildService.ChildView> all = children.search(q, schoolYearId);
        return ResponseEntity.ok(new ChildPageDto(
                page,
                size,
                (long) all.size(),
                paging.totalPages(all.size()),
                paging.slice(all).stream().map(FamilyMapper::child).toList()));
    }

    @Override
    public ResponseEntity<AdminChildDto> getChild(UUID childId) {
        return ResponseEntity.ok(FamilyMapper.child(children.get(childId)));
    }

    @Override
    public ResponseEntity<AdminChildDto> createChild(ChildCreateDto body) {
        ChildService.ChildView created = children.create(
                CurrentUser.principal().userId(),
                body.getFirstName(),
                body.getLastName(),
                body.getBirthDate(),
                body.getExternalRef(),
                body.getFamilyId(),
                body.getNewFamilyLabel());
        return ResponseEntity.status(HttpStatus.CREATED).body(FamilyMapper.child(created));
    }

    @Override
    public ResponseEntity<AdminChildDto> updateChild(UUID childId, ChildUpdateDto body) {
        ChildService.ChildView updated = children.update(
                CurrentUser.principal().userId(),
                childId,
                body.getFirstName(),
                body.getLastName(),
                body.getBirthDate(),
                body.getExternalRef(),
                body.getFamilyId());
        return ResponseEntity.ok(FamilyMapper.child(updated));
    }

    @Override
    public ResponseEntity<Void> enrollChild(UUID childId, UUID schoolYearId) {
        children.enroll(CurrentUser.principal().userId(), childId, schoolYearId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> unenrollChild(UUID childId, UUID schoolYearId) {
        children.unenroll(CurrentUser.principal().userId(), childId, schoolYearId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<ImportResultDto> importChildren(String body, Boolean dryRun) {
        ChildImportService.Result result =
                imports.importCsv(CurrentUser.principal().userId(), body, Boolean.TRUE.equals(dryRun));
        return ResponseEntity.ok(new ImportResultDto(
                result.dryRun(),
                result.applied(),
                result.created(),
                result.updated(),
                result.unchanged(),
                result.errors().stream()
                        .map(error -> new ImportErrorDto(
                                error.line(),
                                ImportErrorDto.CodeEnum.fromValue(error.code().name())))
                        .toList()));
    }
}
