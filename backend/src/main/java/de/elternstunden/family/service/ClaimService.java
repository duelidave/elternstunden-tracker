package de.elternstunden.family.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.crypto.Hashing;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.text.NameNormalizer;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.family.domain.Child;
import de.elternstunden.family.domain.ChildData;
import de.elternstunden.family.domain.Claim;
import de.elternstunden.family.domain.MatchResult;
import de.elternstunden.family.repo.ChildRepository;
import de.elternstunden.family.repo.ClaimRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Claims: the child data a parent enters to be assigned to the family (FS-REG 7.1). Matching is exact on the
 * normalized names and the birth date (7.1.3); the child list itself is never shown to the parent.
 */
@Service
public class ClaimService {

    static final int MAX_CHILD_AGE_YEARS = 25;
    static final int MAX_CANDIDATES = 10;

    private final ClaimRepository claims;
    private final ChildRepository children;
    private final AppUserRepository users;
    private final AssignmentService assignments;
    private final AuditLog audit;
    private final AppProperties.Claims settings;
    private final Clock clock;

    public ClaimService(
            ClaimRepository claims,
            ChildRepository children,
            AppUserRepository users,
            AssignmentService assignments,
            AuditLog audit,
            AppProperties properties,
            Clock clock) {
        this.claims = claims;
        this.children = children;
        this.users = users;
        this.assignments = assignments;
        this.audit = audit;
        this.settings = properties.claims();
        this.clock = clock;
    }

    /** {@code SHA-256-hex(first_norm|last_norm|birth_date)} (7.1.3). */
    static String claimKey(ChildData child) {
        return Hashing.sha256Hex(NameNormalizer.normalize(child.firstName()) + "|"
                + NameNormalizer.normalize(child.lastName()) + "|" + child.birthDate());
    }

    /**
     * Stores a new claim, replacing the user's open one, and computes the match (7.1.4 step 5). Runs in the caller's
     * transaction: the advisory lock per child holds until it ends.
     *
     * @param bindingHash hash of the {@code auth_binding} cookie for claims from the registration, {@code null} for
     *     data entered in a session
     */
    @Transactional
    public Claim create(UUID userId, ChildData child, @Nullable String bindingHash, Instant now) {
        String key = claimKey(child);
        claims.lockClaimKey(key);
        claims.supersedeOpen(userId, now);
        MatchResult result;
        UUID matchedChild = null;
        if (claims.countSince(key, now.minus(settings.window())) >= settings.maxPerTarget()) {
            result = MatchResult.THROTTLED;
        } else {
            List<Child> matches = children.findByIdentity(child.firstName(), child.lastName(), child.birthDate());
            Set<UUID> familiesFound = matches.stream().map(Child::familyId).collect(Collectors.toSet());
            if (familiesFound.isEmpty()) {
                result = MatchResult.NO_MATCH;
            } else if (familiesFound.size() == 1) {
                result = MatchResult.MATCHED;
                matchedChild = matches.getFirst().id();
            } else {
                result = MatchResult.AMBIGUOUS;
            }
        }
        UUID id = claims.insert(
                userId,
                child.firstName(),
                child.lastName(),
                child.birthDate(),
                key,
                result,
                matchedChild,
                bindingHash,
                now);
        audit.system(
                "CLAIM_CREATED",
                "registration_claim",
                id,
                Map.of("userId", userId.toString(), "result", result.name()));
        return claims.findOpen(userId).orElseThrow();
    }

    /**
     * After the first redemption of a link (7.1.4 step 7): the claim applies only in the browser it was created in.
     * There a MATCHED claim assigns the family; elsewhere the claim is discarded and the user enters the data again.
     */
    public void onActivation(AppUser user, Optional<String> browserBindingHash, Instant now) {
        Optional<Claim> open = claims.findOpen(user.id());
        if (open.isEmpty()) {
            return;
        }
        Claim claim = open.get();
        boolean sameBrowser = claim.bindingHash() != null
                && browserBindingHash.map(claim.bindingHash()::equals).orElse(false);
        if (!sameBrowser) {
            claims.supersedeOpen(user.id(), now);
            audit.system("CLAIM_DISCARDED", "registration_claim", claim.id(), Map.of("reason", "OTHER_BROWSER"));
            return;
        }
        if (claim.result() == MatchResult.MATCHED) {
            assignments.applyMatch(user.id(), user.displayName(), claim, now);
        }
    }

    /**
     * {@code PUT /me/claim} (7.1.7): unassigned family members enter or correct their child data. The first entry
     * without an open claim is free, then at most {@code maxCorrections} corrections. A match assigns at once.
     */
    @Transactional
    public void submitOwn(UUID userId, ChildData child) {
        Instant now = clock.instant();
        AppUser user = users.findByIdForUpdate(userId).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (user.role() != UserRole.FAMILY_MEMBER) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (user.familyId() != null) {
            throw new ApiException(ErrorCode.ALREADY_ASSIGNED);
        }
        validate(child, BusinessTime.today(clock));
        if (claims.findOpen(userId).isPresent()) {
            if (users.claimCorrections(userId) >= settings.maxCorrections()) {
                throw new ApiException(ErrorCode.CLAIM_CORRECTION_LIMIT);
            }
            users.incrementClaimCorrections(userId);
        }
        Claim claim = create(userId, child, null, now);
        if (claim.result() == MatchResult.MATCHED) {
            assignments.applyMatch(userId, user.displayName(), claim, now);
        }
    }

    public Optional<Claim> openClaim(UUID userId) {
        return claims.findOpen(userId);
    }

    /** Remaining corrections on /pending (7.1.7). */
    public int correctionsLeft(UUID userId) {
        return Math.max(0, settings.maxCorrections() - users.claimCorrections(userId));
    }

    /** Suggestions for admins: same birth date or similar last name (7.1.7). */
    public List<ChildRepository.Candidate> candidates(Claim claim) {
        return children.findCandidates(claim.lastName(), claim.birthDate(), MAX_CANDIDATES);
    }

    /** Rules of 7.1.2 beyond the contract's schema. */
    public static void validate(ChildData child, LocalDate today) {
        if (child.firstName().isEmpty()) {
            throw ApiException.validation("firstName", "NotBlank", "must not be blank");
        }
        if (child.lastName().isEmpty()) {
            throw ApiException.validation("lastName", "NotBlank", "must not be blank");
        }
        if (child.birthDate().isAfter(today)) {
            throw ApiException.validation("birthDate", "PastOrPresent", "must not be in the future");
        }
        if (child.birthDate().isBefore(today.minusYears(MAX_CHILD_AGE_YEARS))) {
            throw ApiException.validation("birthDate", "MaxAge", "must not be older than 25 years");
        }
    }
}
