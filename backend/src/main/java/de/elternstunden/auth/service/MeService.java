package de.elternstunden.auth.service;

import de.elternstunden.api.model.ChildClaimDto;
import de.elternstunden.api.model.MeDto;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.family.domain.ChildData;
import de.elternstunden.family.service.ClaimService;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** The own account ({@code GET /me}) and, for unassigned family members, the own child data (7.1.7). */
@Service
public class MeService {

    private final AppUserRepository users;
    private final ClaimService claims;

    public MeService(AppUserRepository users, ClaimService claims) {
        this.users = users;
        this.claims = claims;
    }

    public MeDto current() {
        return toMe(currentUser());
    }

    public MeDto updateClaim(ChildClaimDto child) {
        UUID userId = CurrentUser.principal().userId();
        claims.submitOwn(userId, new ChildData(child.getFirstName(), child.getLastName(), child.getBirthDate()));
        return current();
    }

    /** Unassigned family members also see their own open claim and the corrections left (7.1.7, SEC-08). */
    public MeDto toMe(AppUser user) {
        MeDto me = new MeDto(
                user.id(),
                user.displayName(),
                user.email(),
                MeDto.RoleEnum.fromValue(user.role().name()),
                user.familyId() != null);
        if (user.role() == UserRole.FAMILY_MEMBER && user.familyId() == null) {
            claims.openClaim(user.id())
                    .ifPresent(claim -> me.setPendingClaim(
                            new ChildClaimDto(claim.firstName(), claim.lastName(), claim.birthDate())));
            me.setClaimCorrectionsLeft(claims.correctionsLeft(user.id()));
        }
        return me;
    }

    private AppUser currentUser() {
        return users.findById(CurrentUser.principal().userId())
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }
}
