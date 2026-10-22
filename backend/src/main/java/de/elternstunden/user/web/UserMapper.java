package de.elternstunden.user.web;

import de.elternstunden.api.model.AdminClaimDto;
import de.elternstunden.api.model.AdminUserDetailDto;
import de.elternstunden.api.model.AdminUserDto;
import de.elternstunden.api.model.ChildCandidateDto;
import de.elternstunden.api.model.UserStatusDto;
import de.elternstunden.family.domain.Child;
import de.elternstunden.family.domain.Claim;
import de.elternstunden.family.repo.ChildRepository;
import de.elternstunden.user.domain.UserAccount;
import de.elternstunden.user.service.UserAdminService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

/** Domain to API models of the user administration; entities never leave the web layer (4.4). */
public final class UserMapper {

    private UserMapper() {}

    public static AdminUserDto user(UserAdminService.UserView view) {
        UserAccount account = view.account();
        AdminUserDto dto = new AdminUserDto(
                account.id(),
                account.displayName(),
                account.email(),
                AdminUserDto.RoleEnum.fromValue(account.role().name()),
                UserStatusDto.fromValue(account.status().name()),
                account.managedByConfig(),
                utc(account.createdAt()));
        dto.setFamilyId(account.familyId());
        dto.setFamilyLabel(view.familyLabel());
        dto.setLastLoginAt(account.lastLoginAt() == null ? null : utc(account.lastLoginAt()));
        return dto;
    }

    public static AdminUserDetailDto detail(UserAdminService.UserDetail detail, Map<UUID, String> familyLabels) {
        AdminUserDetailDto dto = new AdminUserDetailDto(
                user(detail.user()),
                detail.candidates().stream()
                        .map(candidate -> candidate(candidate, familyLabels))
                        .toList());
        detail.claim().ifPresent(claim -> dto.setClaim(claim(claim)));
        return dto;
    }

    static AdminClaimDto claim(Claim claim) {
        return new AdminClaimDto(
                claim.id(),
                claim.firstName(),
                claim.lastName(),
                claim.birthDate(),
                AdminClaimDto.MatchResultEnum.fromValue(claim.result().name()),
                utc(claim.createdAt()));
    }

    static ChildCandidateDto candidate(ChildRepository.Candidate candidate, Map<UUID, String> familyLabels) {
        Child child = candidate.child();
        return new ChildCandidateDto(
                child.id(),
                child.firstName(),
                child.lastName(),
                child.birthDate(),
                child.familyId(),
                familyLabels.getOrDefault(child.familyId(), ""));
    }

    public static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
