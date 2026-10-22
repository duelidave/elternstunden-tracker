package de.elternstunden.auth.oidc;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.auth.repo.IdentityRepository;
import de.elternstunden.auth.service.AuthMails;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.config.AppProperties.EmailTrust;
import de.elternstunden.common.crypto.Hashing;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.mail.AsyncMailer;
import de.elternstunden.common.security.PendingOidc;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.family.domain.ChildData;
import de.elternstunden.family.domain.Claim;
import de.elternstunden.family.domain.MatchResult;
import de.elternstunden.family.service.AssignmentService;
import de.elternstunden.family.service.ClaimService;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Login and registration with an OIDC provider (7.1.5): the case table from top to bottom, linking only where the
 * provider is authoritative for the address, no account before "Noch ein Schritt", never for the master (INV-12).
 */
@Service
public class OidcLoginService {

    /** Codes for the login page after a refused provider login; the UI shows its own texts (UI-02). */
    public static final String EMAIL_UNVERIFIED = "OIDC_EMAIL_UNVERIFIED";

    public static final String MASTER_ONLY = "OIDC_MASTER_ONLY";
    public static final String REJECTED = "OIDC_REJECTED";
    public static final String NOT_AUTHORITATIVE = "OIDC_NOT_AUTHORITATIVE";
    public static final String IDENTITY_TAKEN = "OIDC_IDENTITY_TAKEN";

    /** The result of a provider login. */
    public sealed interface Outcome {}

    public record LoggedIn(AppUser user) implements Outcome {}

    public record Pending(PendingOidc pending) implements Outcome {}

    public record Rejected(String code) implements Outcome {}

    private final AppUserRepository users;
    private final IdentityRepository identities;
    private final ClaimService claims;
    private final AssignmentService assignments;
    private final AuthMails mails;
    private final AsyncMailer mailer;
    private final AuditLog audit;
    private final AppProperties properties;
    private final Clock clock;

    public OidcLoginService(
            AppUserRepository users,
            IdentityRepository identities,
            ClaimService claims,
            AssignmentService assignments,
            AuthMails mails,
            AsyncMailer mailer,
            AuditLog audit,
            AppProperties properties,
            Clock clock) {
        this.users = users;
        this.identities = identities;
        this.claims = claims;
        this.assignments = assignments;
        this.mails = mails;
        this.mailer = mailer;
        this.audit = audit;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public Outcome login(OidcIdentity identity) {
        Instant now = clock.instant();
        if (!identity.emailVerified() || identity.email() == null) {
            audit.system("OIDC_LOGIN_REJECTED", "user_identity", null, Map.of("reason", "EMAIL_UNVERIFIED"));
            return new Rejected(EMAIL_UNVERIFIED);
        }
        Optional<IdentityRepository.Identity> known =
                identities.findByIssuerAndSubject(identity.issuer(), identity.subject());
        if (known.isPresent()) {
            AppUser user = users.findByIdForUpdate(known.get().userId()).orElseThrow();
            if (user.status() == UserStatus.DISABLED) {
                return rejected(user.id(), "DISABLED", REJECTED);
            }
            if (user.isMaster()) {
                // Must not exist (INV-12): refuse and alarm the master
                masterAlarm();
                return rejected(user.id(), "MASTER_IDENTITY", REJECTED);
            }
            identities.touchLogin(known.get().id(), now);
            audit.user(user.id(), "OIDC_LOGIN", "app_user", user.id());
            return new LoggedIn(user);
        }

        String email = Hashing.normalizeEmail(identity.email());
        Optional<AppUser> byEmail = users.findByEmailForUpdate(email);
        if (byEmail.isEmpty()) {
            String name = identity.name() == null || identity.name().isBlank()
                    ? ""
                    : identity.name().strip();
            return new Pending(
                    new PendingOidc(identity.provider(), identity.issuer(), identity.subject(), email, name, now));
        }
        AppUser user = byEmail.get();
        if (user.isMaster()) {
            return rejected(user.id(), "MASTER", MASTER_ONLY);
        }
        if (user.status() == UserStatus.DISABLED) {
            return rejected(user.id(), "DISABLED", REJECTED);
        }
        if (!authoritative(identity, email)) {
            return rejected(user.id(), "NOT_AUTHORITATIVE", NOT_AUTHORITATIVE);
        }
        UUID identityId =
                identities.insert(user.id(), identity.provider(), identity.issuer(), identity.subject(), email, now);
        audit.write(
                user.id(),
                AuditLog.ActorKind.USER,
                "IDENTITY_LINKED",
                "user_identity",
                identityId,
                Map.of("provider", identity.provider(), "method", "EMAIL"));
        if (user.status() == UserStatus.PENDING_VERIFICATION) {
            // The provider confirmed the address; the open claim may come from someone else (7.1.4 step 7)
            users.activate(user.id(), now);
            claims.discard(user.id(), "OIDC_ACTIVATION", now);
            audit.user(user.id(), "USER_ACTIVATED", "app_user", user.id());
        }
        return new LoggedIn(users.findById(user.id()).orElseThrow());
    }

    /**
     * "Noch ein Schritt" (7.1.5 step 4): account, identity and claim; with a match the family is assigned at once.
     * Idempotent: if the identity exists already (double click), it only signs in.
     */
    @Transactional
    public AppUser complete(PendingOidc pending, String displayName, ChildData child) {
        Optional<IdentityRepository.Identity> known =
                identities.findByIssuerAndSubject(pending.issuer(), pending.subject());
        if (known.isPresent()) {
            return users.findById(known.get().userId()).orElseThrow();
        }
        String name = displayName.strip();
        if (name.isEmpty()) {
            throw ApiException.validation("displayName", "NotBlank", "must not be blank");
        }
        ClaimService.validate(child, BusinessTime.today(clock));
        Instant now = clock.instant();
        UUID userId = users.insertActive(pending.email(), name, now)
                // Someone registered the address meanwhile: the user signs in with the provider again
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN));
        UUID identityId = identities.insert(
                userId, pending.provider(), pending.issuer(), pending.subject(), pending.email(), now);
        audit.write(userId, AuditLog.ActorKind.USER, "USER_REGISTERED", "app_user", userId, Map.of("method", "OIDC"));
        audit.write(
                userId,
                AuditLog.ActorKind.USER,
                "IDENTITY_LINKED",
                "user_identity",
                identityId,
                Map.of("provider", pending.provider(), "method", "REGISTRATION"));
        // Entered in this session, so no browser condition (7.1.5 step 4)
        Claim claim = claims.create(userId, child, null, now);
        if (claim.result() == MatchResult.MATCHED) {
            assignments.applyMatch(userId, name, claim, now);
        }
        return users.findById(userId).orElseThrow();
    }

    /** Linking in the profile (7.2): the user is signed in already, so the e-mail rule does not apply. */
    @Transactional
    public Optional<String> link(UUID userId, OidcIdentity identity) {
        AppUser user = users.findByIdForUpdate(userId).orElseThrow();
        if (user.isMaster()) {
            return Optional.of(REJECTED);
        }
        Optional<IdentityRepository.Identity> known =
                identities.findByIssuerAndSubject(identity.issuer(), identity.subject());
        if (known.isPresent()) {
            return known.get().userId().equals(userId) ? Optional.empty() : Optional.of(IDENTITY_TAKEN);
        }
        UUID identityId = identities.insert(
                userId,
                identity.provider(),
                identity.issuer(),
                identity.subject(),
                identity.email() == null ? null : Hashing.normalizeEmail(identity.email()),
                clock.instant());
        audit.write(
                userId,
                AuditLog.ActorKind.USER,
                "IDENTITY_LINKED",
                "user_identity",
                identityId,
                Map.of("provider", identity.provider(), "method", "PROFILE"));
        return Optional.empty();
    }

    /** 7.1.5: only an authoritative provider may link an existing account by e-mail. */
    boolean authoritative(OidcIdentity identity, String email) {
        AppProperties.OidcProvider provider = properties.oidc().providers().get(identity.provider());
        EmailTrust trust = provider == null ? EmailTrust.NEVER : provider.trustEmailForLinking();
        return switch (trust) {
            case NEVER -> false;
            case GMAIL_OR_HD ->
                email.toLowerCase(Locale.ROOT).endsWith("@gmail.com")
                        || (identity.hostedDomain() != null
                                && !identity.hostedDomain().isBlank());
        };
    }

    private Rejected rejected(UUID userId, String reason, String code) {
        audit.system("OIDC_LOGIN_REJECTED", "app_user", userId, Map.of("reason", reason));
        return new Rejected(code);
    }

    private void masterAlarm() {
        String master = properties.master().email();
        if (master != null && !master.isBlank()) {
            mailer.sendAfterCommit(mails.oidcMasterAlarm(master));
        }
    }
}
