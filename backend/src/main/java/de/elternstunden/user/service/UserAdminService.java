package de.elternstunden.user.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.service.AccountCredentials;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AdminOnly;
import de.elternstunden.common.security.SessionTerminator;
import de.elternstunden.common.text.Search;
import de.elternstunden.family.domain.Claim;
import de.elternstunden.family.repo.ChildRepository;
import de.elternstunden.family.repo.ClaimRepository;
import de.elternstunden.family.service.AssignmentService;
import de.elternstunden.family.service.ClaimService;
import de.elternstunden.family.service.FamilyService;
import de.elternstunden.user.domain.UserAccount;
import de.elternstunden.user.repo.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User administration (FS-ADM 7.7, F5, DP-05): family assignment, role, disable, sessions, anonymization. The master
 * account is managed by the configuration only (D-22), and admins cannot lock themselves out.
 */
@Service
@AdminOnly
public class UserAdminService {

    /** An account with the display label of its family. */
    public record UserView(UserAccount account, @Nullable String familyLabel) {}

    /** An account with its open claim and the candidate children for the assignment (7.1.7). */
    public record UserDetail(UserView user, Optional<Claim> claim, List<ChildRepository.Candidate> candidates) {}

    private final UserAccountRepository accounts;
    private final ClaimRepository claims;
    private final ClaimService claimService;
    private final FamilyService families;
    private final AssignmentService assignments;
    private final SessionTerminator sessions;
    private final AccountCredentials credentials;
    private final AuditLog audit;
    private final Clock clock;

    public UserAdminService(
            UserAccountRepository accounts,
            ClaimRepository claims,
            ClaimService claimService,
            FamilyService families,
            AssignmentService assignments,
            SessionTerminator sessions,
            AccountCredentials credentials,
            AuditLog audit,
            Clock clock) {
        this.accounts = accounts;
        this.claims = claims;
        this.claimService = claimService;
        this.families = families;
        this.assignments = assignments;
        this.sessions = sessions;
        this.credentials = credentials;
        this.audit = audit;
        this.clock = clock;
    }

    /** Newest first; search in display name and e-mail. */
    public List<UserView> search(@Nullable String query, @Nullable UserStatus status, boolean onlyUnassigned) {
        Search search = Search.of(query);
        Map<UUID, String> labels = families.displayLabels();
        return accounts.findAll().stream()
                .filter(account -> status == null || account.status() == status)
                .filter(account -> !onlyUnassigned || account.unassigned())
                .filter(account -> search.matches(account.displayName(), account.email()))
                .map(account -> view(account, labels))
                .toList();
    }

    public UserDetail get(UUID id) {
        return detail(account(id), families.displayLabels());
    }

    /** Active family members without family, newest first, with claim and candidates (dashboard widget). */
    public List<UserDetail> unassigned() {
        Map<UUID, String> labels = families.displayLabels();
        return accounts.findAll().stream()
                .filter(UserAccount::unassigned)
                .map(account -> detail(account, labels))
                .toList();
    }

    @Transactional
    public UserView assignFamily(UUID actor, UUID id, UUID familyId) {
        UserAccount account = lockedEditable(id);
        if (!families.exists(familyId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (!familyId.equals(account.familyId())) {
            assignments.assignByAdmin(actor, id, familyId, clock.instant());
        }
        return view(account(id), families.displayLabels());
    }

    @Transactional
    public void removeFamily(UUID actor, UUID id) {
        UserAccount account = lockedEditable(id);
        if (account.familyId() != null) {
            assignments.removeByAdmin(actor, id, account.familyId(), clock.instant());
        }
    }

    @Transactional
    public UserView setRole(UUID actor, UUID id, UserRole role) {
        UserAccount account = lockedEditable(id);
        requireOther(actor, id);
        if (role == UserRole.MASTER_ADMIN) {
            throw ApiException.validation("role", "Role", "must be FAMILY_MEMBER or ADMIN");
        }
        if (account.role() != role) {
            accounts.setRole(id, role, clock.instant());
            audit.write(
                    actor,
                    AuditLog.ActorKind.USER,
                    "USER_ROLE_CHANGED",
                    "app_user",
                    id,
                    Map.of("from", account.role().name(), "to", role.name()));
        }
        return view(account(id), families.displayLabels());
    }

    /** Ends all sessions of the user (7.2). */
    @Transactional
    public UserView disable(UUID actor, UUID id) {
        UserAccount account = lockedEditable(id);
        requireOther(actor, id);
        if (account.status() != UserStatus.DISABLED) {
            accounts.setStatus(id, UserStatus.DISABLED, clock.instant());
            audit.user(actor, "USER_DISABLED", "app_user", id);
        }
        sessions.terminateAll(id);
        return view(account(id), families.displayLabels());
    }

    /** Back to active, or to unconfirmed if the address was never confirmed. Anonymized accounts stay disabled. */
    @Transactional
    public UserView enable(UUID actor, UUID id) {
        UserAccount account = lockedEditable(id);
        if (account.anonymizedAt() != null) {
            throw new ApiException(ErrorCode.USER_ANONYMIZED);
        }
        if (account.status() == UserStatus.DISABLED) {
            UserStatus status = account.emailVerified() ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION;
            accounts.setStatus(id, status, clock.instant());
            audit.user(actor, "USER_ENABLED", "app_user", id);
        }
        return view(account(id), families.displayLabels());
    }

    public void terminateSessions(UUID actor, UUID id) {
        account(id);
        int ended = sessions.terminateAll(id);
        audit.write(
                actor, AuditLog.ActorKind.USER, "USER_SESSIONS_TERMINATED", "app_user", id, Map.of("sessions", ended));
    }

    /** DP-05 (a): irreversible. Entries keep pointing at the account. */
    @Transactional
    public UserView anonymize(UUID actor, UUID id) {
        UserAccount account = lockedEditable(id);
        requireOther(actor, id);
        if (account.anonymizedAt() == null) {
            Instant now = clock.instant();
            claims.deleteAllOf(id);
            credentials.deleteAll(id);
            accounts.anonymize(id, now);
            audit.user(actor, "USER_ANONYMIZED", "app_user", id);
        }
        sessions.terminateAll(id);
        return view(account(id), families.displayLabels());
    }

    private UserDetail detail(UserAccount account, Map<UUID, String> labels) {
        Optional<Claim> claim = account.familyId() == null ? claims.findOpen(account.id()) : Optional.empty();
        return new UserDetail(
                view(account, labels),
                claim,
                claim.map(claimService::candidates).orElse(List.of()));
    }

    private static UserView view(UserAccount account, Map<UUID, String> labels) {
        return new UserView(account, account.familyId() == null ? null : labels.getOrDefault(account.familyId(), ""));
    }

    private UserAccount account(UUID id) {
        return accounts.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** Locks the row; the config-managed master cannot be changed here (D-22). */
    private UserAccount lockedEditable(UUID id) {
        UserAccount account = accounts.findByIdForUpdate(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (account.managedByConfig()) {
            throw new ApiException(ErrorCode.ACCOUNT_MANAGED_BY_CONFIG);
        }
        return account;
    }

    private static void requireOther(UUID actor, UUID id) {
        if (actor.equals(id)) {
            throw new ApiException(ErrorCode.OWN_ACCOUNT);
        }
    }
}
