package de.elternstunden.family.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.mail.AsyncMailer;
import de.elternstunden.family.domain.Child;
import de.elternstunden.family.domain.Claim;
import de.elternstunden.family.domain.Member;
import de.elternstunden.family.repo.ChildRepository;
import de.elternstunden.family.repo.ClaimRepository;
import de.elternstunden.family.repo.FamilyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Assigning accounts to families, automatically after a match (7.1.4 step 7) or by an admin (F5, 7.1.7). Every
 * assignment resolves the open claim and is audited; an automatic one notifies the existing members (7.1.6). A family
 * has at most two users (D-28, INV-13). Callers provide the transaction and lock the account first.
 */
@Service
public class AssignmentService {

    /** D-28: e.g. both parents. */
    static final int MAX_USERS = 2;

    private final AppUserRepository users;
    private final ChildRepository children;
    private final FamilyRepository families;
    private final ClaimRepository claims;
    private final FamilyMails mails;
    private final AsyncMailer mailer;
    private final AuditLog audit;

    public AssignmentService(
            AppUserRepository users,
            ChildRepository children,
            FamilyRepository families,
            ClaimRepository claims,
            FamilyMails mails,
            AsyncMailer mailer,
            AuditLog audit) {
        this.users = users;
        this.children = children;
        this.families = families;
        this.claims = claims;
        this.mails = mails;
        this.audit = audit;
        this.mailer = mailer;
    }

    /**
     * Applies a MATCHED claim; empty if the matched child no longer exists or the family is full. A full family leaves
     * the user unassigned with the open claim, so an admin sorts it out (7.1.6).
     */
    public Optional<UUID> applyMatch(UUID userId, String displayName, Claim claim, Instant now) {
        Optional<Child> child = Optional.ofNullable(claim.matchedChildId()).flatMap(children::findById);
        if (child.isEmpty()) {
            return Optional.empty();
        }
        UUID familyId = child.get().familyId();
        if (full(familyId)) {
            audit.system(
                    "AUTO_ASSIGNMENT_SKIPPED",
                    "app_user",
                    userId,
                    Map.of(
                            "reason",
                            "FAMILY_FULL",
                            "familyId",
                            familyId.toString(),
                            "claimId",
                            claim.id().toString()));
            return Optional.empty();
        }
        List<Member> existing = activeMembers(familyId, userId);
        users.setFamily(userId, familyId, now);
        claims.resolveOpen(userId, now);
        audit.system(
                "USER_ASSIGNED_TO_FAMILY",
                "app_user",
                userId,
                Map.of(
                        "method",
                        "AUTO_MATCH",
                        "familyId",
                        familyId.toString(),
                        "claimId",
                        claim.id().toString()));
        // 7.1.6: the existing members learn about it; OPEN-12: no self-correction beyond that
        existing.forEach(member -> mailer.sendAfterCommit(mails.newMember(member.email(), displayName)));
        return Optional.of(familyId);
    }

    /** The account must not be in this family yet. */
    public void assignByAdmin(UUID actor, UUID userId, UUID familyId, Instant now) {
        if (full(familyId)) {
            throw new ApiException(ErrorCode.FAMILY_FULL);
        }
        users.setFamily(userId, familyId, now);
        claims.resolveOpen(userId, now);
        audit.write(
                actor,
                AuditLog.ActorKind.USER,
                "USER_ASSIGNED_TO_FAMILY",
                "app_user",
                userId,
                Map.of("method", "ADMIN", "familyId", familyId.toString()));
    }

    public void removeByAdmin(UUID actor, UUID userId, UUID previousFamilyId, Instant now) {
        users.setFamily(userId, null, now);
        audit.write(
                actor,
                AuditLog.ActorKind.USER,
                "USER_REMOVED_FROM_FAMILY",
                "app_user",
                userId,
                Map.of("familyId", previousFamilyId.toString()));
    }

    /** Locks the family, so parallel assignments cannot pass the limit together (INV-13). */
    private boolean full(UUID familyId) {
        families.lock(familyId);
        return families.memberCount(familyId) >= MAX_USERS;
    }

    private List<Member> activeMembers(UUID familyId, UUID exceptUserId) {
        return families.members(familyId).stream()
                .filter(member -> member.status() == UserStatus.ACTIVE)
                .filter(member -> !member.id().equals(exceptUserId))
                .toList();
    }
}
