package de.elternstunden.family.web;

import de.elternstunden.api.FamiliesApi;
import de.elternstunden.api.model.FamilyDetailDto;
import de.elternstunden.api.model.FamilyPageDto;
import de.elternstunden.api.model.FamilyUpdateDto;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.common.web.Paging;
import de.elternstunden.family.service.ChildService;
import de.elternstunden.family.service.FamilyService;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Families for admins (FS-ADM 7.7). */
@RestController
public class FamilyController implements FamiliesApi {

    private final FamilyService families;
    private final ChildService children;

    public FamilyController(FamilyService families, ChildService children) {
        this.families = families;
        this.children = children;
    }

    @Override
    public ResponseEntity<FamilyPageDto> listFamilies(@Nullable String q, Integer page, Integer size) {
        Paging paging = Paging.of(page, size);
        List<FamilyService.Summary> all = families.search(q);
        return ResponseEntity.ok(new FamilyPageDto(
                page,
                size,
                (long) all.size(),
                paging.totalPages(all.size()),
                paging.slice(all).stream().map(FamilyMapper::summary).toList()));
    }

    @Override
    public ResponseEntity<FamilyDetailDto> getFamily(UUID familyId) {
        return ResponseEntity.ok(detail(families.get(familyId)));
    }

    @Override
    public ResponseEntity<FamilyDetailDto> updateFamily(UUID familyId, FamilyUpdateDto body) {
        return ResponseEntity.ok(
                detail(families.update(CurrentUser.principal().userId(), familyId, body.getLabel(), body.getNote())));
    }

    private FamilyDetailDto detail(FamilyService.Detail detail) {
        return FamilyMapper.detail(detail, children.ofFamily(detail.family().id()));
    }
}
