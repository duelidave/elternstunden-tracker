package de.elternstunden.auth.service;

import de.elternstunden.api.model.ChildClaimDto;
import de.elternstunden.api.model.IdentityDto;
import de.elternstunden.api.model.MeDto;
import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.oidc.LinkIntent;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.auth.repo.IdentityRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AppUserPrincipal;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.family.domain.ChildData;
import de.elternstunden.family.service.ClaimService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The own account ({@code /me}): profile, linked providers, admin TOTP status and, for unassigned family members, the
 * own child data (7.1.7, 7.2 "Profil").
 */
@Service
public class MeService {

    private final AppUserRepository users;
    private final IdentityRepository identities;
    private final ClaimService claims;
    private final AdminTotpService totp;
    private final ClientRegistrationRepository providers;
    private final AuditLog audit;
    private final AppProperties properties;
    private final Clock clock;

    public MeService(
            AppUserRepository users,
            IdentityRepository identities,
            ClaimService claims,
            AdminTotpService totp,
            ClientRegistrationRepository providers,
            AuditLog audit,
            AppProperties properties,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.claims = claims;
        this.totp = totp;
        this.providers = providers;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    public MeDto current() {
        return toMe(currentUser());
    }

    public MeDto updateClaim(ChildClaimDto child) {
        UUID userId = CurrentUser.principal().userId();
        claims.submitOwn(userId, new ChildData(child.getFirstName(), child.getLastName(), child.getBirthDate()));
        return current();
    }

    @Transactional
    public MeDto updateDisplayName(String displayName) {
        AppUser user = currentUser();
        String name = displayName.strip();
        if (name.isEmpty()) {
            throw ApiException.validation("displayName", "NotBlank", "must not be blank");
        }
        if (user.isMaster()) {
            // The master's name comes from the configuration (7.9)
            throw new ApiException(ErrorCode.ACCOUNT_MANAGED_BY_CONFIG);
        }
        users.setDisplayName(user.id(), name, clock.instant());
        audit.user(user.id(), "DISPLAY_NAME_CHANGED", "app_user", user.id());
        return current();
    }

    /**
     * Starts linking a provider (7.2 "Profil"): only with a fresh login, never for the master (INV-12). The SPA then
     * navigates to the returned URL with a top-level GET (SEC-10).
     */
    public String startLink(String provider, HttpServletRequest request) {
        AppUserPrincipal principal = CurrentUser.principal();
        if (providers.findByRegistrationId(provider) == null) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (principal.role() == UserRole.MASTER_ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        Instant now = clock.instant();
        if (now.isAfter(principal.authenticatedAt().plus(properties.oidc().reauthWindow()))) {
            throw new ApiException(ErrorCode.REAUTH_REQUIRED);
        }
        request.getSession().setAttribute(LinkIntent.ATTRIBUTE, new LinkIntent(principal.userId(), provider, now));
        return "/oauth2/authorization/" + provider;
    }

    /** Own identities only (SEC-08); always allowed, the magic link stays (INV-11). */
    @Transactional
    public void deleteIdentity(UUID identityId) {
        UUID userId = CurrentUser.principal().userId();
        if (!identities.delete(identityId, userId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        audit.write(userId, AuditLog.ActorKind.USER, "IDENTITY_UNLINKED", "user_identity", identityId, Map.of());
    }

    public AdminTotpService.Setup setupTotp() {
        UUID userId = CurrentUser.isMfaPending()
                ? CurrentUser.mfaPending().userId()
                : CurrentUser.principal().userId();
        return totp.setup(userId);
    }

    public void disableTotp() {
        AppUserPrincipal principal = CurrentUser.principal();
        totp.disable(principal.userId(), principal.authenticatedAt());
    }

    public MeDto toMe(AppUser user) {
        MeDto me = new MeDto(
                user.id(),
                user.displayName(),
                user.email(),
                MeDto.RoleEnum.fromValue(user.role().name()),
                user.familyId() != null);
        me.setIdentities(identities.findByUser(user.id()).stream()
                .map(identity -> {
                    IdentityDto dto = new IdentityDto(identity.id(), identity.provider(), utc(identity.createdAt()));
                    dto.setEmailAtLink(identity.emailAtLink());
                    dto.setLastLoginAt(identity.lastLoginAt() == null ? null : utc(identity.lastLoginAt()));
                    return dto;
                })
                .toList());
        if (user.role() == UserRole.ADMIN) {
            me.setTotpEnabled(totp.enabled(user.id()));
            me.setTotpRequired(totp.required());
        }
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

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
