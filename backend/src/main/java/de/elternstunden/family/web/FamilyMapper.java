package de.elternstunden.family.web;

import de.elternstunden.api.model.AdminChildDto;
import de.elternstunden.api.model.ChildNameDto;
import de.elternstunden.api.model.FamilyDetailDto;
import de.elternstunden.api.model.FamilyMemberDto;
import de.elternstunden.api.model.FamilySummaryDto;
import de.elternstunden.api.model.UserStatusDto;
import de.elternstunden.family.domain.Child;
import de.elternstunden.family.service.ChildService.ChildView;
import de.elternstunden.family.service.FamilyService;
import java.util.List;

/** Domain to API models of the family module; entities never leave the web layer (4.4). */
final class FamilyMapper {

    private FamilyMapper() {}

    static AdminChildDto child(ChildView view) {
        Child child = view.child();
        AdminChildDto dto = new AdminChildDto(
                child.id(),
                child.firstName(),
                child.lastName(),
                child.birthDate(),
                child.familyId(),
                view.familyLabel(),
                List.copyOf(view.enrolledSchoolYearIds()));
        dto.setExternalRef(child.externalRef());
        return dto;
    }

    static FamilySummaryDto summary(FamilyService.Summary summary) {
        FamilySummaryDto dto = new FamilySummaryDto(
                summary.family().id(),
                summary.displayLabel(),
                summary.children().stream()
                        .map(child -> new ChildNameDto(child.id(), child.firstName(), child.lastName()))
                        .toList(),
                summary.activeMembers());
        dto.setLabel(summary.family().label());
        return dto;
    }

    static FamilyDetailDto detail(FamilyService.Detail detail, List<ChildView> children) {
        FamilyDetailDto dto = new FamilyDetailDto(
                detail.family().id(),
                detail.displayLabel(),
                children.stream().map(FamilyMapper::child).toList(),
                detail.members().stream()
                        .map(member -> new FamilyMemberDto(
                                member.id(),
                                member.displayName(),
                                member.email(),
                                UserStatusDto.fromValue(member.status().name())))
                        .toList());
        dto.setLabel(detail.family().label());
        dto.setNote(detail.family().note());
        return dto;
    }
}
