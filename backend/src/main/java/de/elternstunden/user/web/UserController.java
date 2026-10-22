package de.elternstunden.user.web;

import de.elternstunden.api.UsersApi;
import de.elternstunden.api.model.AdminUserDetailDto;
import de.elternstunden.api.model.AdminUserDto;
import de.elternstunden.api.model.FamilyAssignmentDto;
import de.elternstunden.api.model.RoleChangeDto;
import de.elternstunden.api.model.UserPageDto;
import de.elternstunden.api.model.UserStatusDto;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.service.AdminTotpService;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.common.web.Paging;
import de.elternstunden.family.service.FamilyService;
import de.elternstunden.user.service.UserAdminService;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** User administration (FS-ADM 7.7, F5, DP-05). */
@RestController
public class UserController implements UsersApi {

    private final UserAdminService users;
    private final FamilyService families;
    private final AdminTotpService totp;

    public UserController(UserAdminService users, FamilyService families, AdminTotpService totp) {
        this.users = users;
        this.families = families;
        this.totp = totp;
    }

    @Override
    public ResponseEntity<UserPageDto> listUsers(
            @Nullable String q,
            @Nullable UserStatusDto status,
            @Nullable Boolean unassigned,
            Integer page,
            Integer size) {
        Paging paging = Paging.of(page, size);
        List<UserAdminService.UserView> all = users.search(
                q, status == null ? null : UserStatus.valueOf(status.getValue()), Boolean.TRUE.equals(unassigned));
        return ResponseEntity.ok(new UserPageDto(
                page,
                size,
                (long) all.size(),
                paging.totalPages(all.size()),
                paging.slice(all).stream().map(UserMapper::user).toList()));
    }

    @Override
    public ResponseEntity<AdminUserDetailDto> getUser(UUID userId) {
        return ResponseEntity.ok(UserMapper.detail(users.get(userId), families.displayLabels()));
    }

    @Override
    public ResponseEntity<AdminUserDto> assignUserToFamily(UUID userId, FamilyAssignmentDto body) {
        return ResponseEntity.ok(UserMapper.user(users.assignFamily(actor(), userId, body.getFamilyId())));
    }

    @Override
    public ResponseEntity<Void> removeUserFromFamily(UUID userId) {
        users.removeFamily(actor(), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AdminUserDto> setUserRole(UUID userId, RoleChangeDto body) {
        return ResponseEntity.ok(UserMapper.user(
                users.setRole(actor(), userId, UserRole.valueOf(body.getRole().getValue()))));
    }

    @Override
    public ResponseEntity<AdminUserDto> disableUser(UUID userId) {
        return ResponseEntity.ok(UserMapper.user(users.disable(actor(), userId)));
    }

    @Override
    public ResponseEntity<AdminUserDto> enableUser(UUID userId) {
        return ResponseEntity.ok(UserMapper.user(users.enable(actor(), userId)));
    }

    @Override
    public ResponseEntity<Void> terminateUserSessions(UUID userId) {
        users.terminateSessions(actor(), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> resetUserTotp(UUID userId) {
        totp.reset(actor(), userId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AdminUserDto> anonymizeUser(UUID userId) {
        return ResponseEntity.ok(UserMapper.user(users.anonymize(actor(), userId)));
    }

    private static UUID actor() {
        return CurrentUser.principal().userId();
    }
}
