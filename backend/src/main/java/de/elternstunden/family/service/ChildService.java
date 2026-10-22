package de.elternstunden.family.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AdminOnly;
import de.elternstunden.common.text.Search;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.family.domain.Child;
import de.elternstunden.family.repo.ChildRepository;
import de.elternstunden.schoolyear.service.SchoolYearService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Children and enrollments for admins (FS-ADM 7.7). No physical deletion (DP-05); the normalized names follow every
 * change (7.1.3). Enrollments of a closed school year are read-only (OPEN-06).
 */
@Service
public class ChildService {

    /** A child with the school years it is enrolled in. */
    public record ChildView(Child child, String familyLabel, List<UUID> enrolledSchoolYearIds) {}

    private final ChildRepository children;
    private final FamilyService families;
    private final SchoolYearService schoolYears;
    private final AuditLog audit;
    private final Clock clock;

    public ChildService(
            ChildRepository children,
            FamilyService families,
            SchoolYearService schoolYears,
            AuditLog audit,
            Clock clock) {
        this.children = children;
        this.families = families;
        this.schoolYears = schoolYears;
        this.audit = audit;
        this.clock = clock;
    }

    /** Children matching the search, optionally only those enrolled in a school year; sorted by name. */
    @AdminOnly
    public List<ChildView> search(@Nullable String query, @Nullable UUID schoolYearId) {
        Search search = Search.of(query);
        Map<UUID, List<UUID>> enrollments = children.enrollments();
        Map<UUID, String> labels = families.displayLabels();
        return children.findAll().stream()
                .filter(child -> search.matches(child.firstName(), child.lastName()))
                .map(child -> new ChildView(
                        child,
                        labels.getOrDefault(child.familyId(), ""),
                        enrollments.getOrDefault(child.id(), List.of())))
                .filter(view ->
                        schoolYearId == null || view.enrolledSchoolYearIds().contains(schoolYearId))
                .toList();
    }

    @AdminOnly
    public ChildView get(UUID id) {
        Child child = children.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return view(child);
    }

    @AdminOnly
    public List<ChildView> ofFamily(UUID familyId) {
        String label = families.displayLabel(familyId);
        return children.findByFamily(familyId).stream()
                .map(child -> new ChildView(child, label, children.enrollmentsOf(child.id())))
                .toList();
    }

    /** With {@code familyId} into an existing family, otherwise into a new one (7.7). */
    @Transactional
    @AdminOnly
    public ChildView create(
            UUID actor,
            String firstName,
            String lastName,
            LocalDate birthDate,
            @Nullable String externalRef,
            @Nullable UUID familyId,
            @Nullable String newFamilyLabel) {
        String first = requireName("firstName", firstName);
        String last = requireName("lastName", lastName);
        requireNotFuture(birthDate);
        String ref = externalRef == null ? null : FamilyService.blankToNull(externalRef);
        if (ref != null && children.externalRefTaken(ref, null)) {
            throw new ApiException(ErrorCode.EXTERNAL_REF_TAKEN);
        }
        UUID family;
        if (familyId != null) {
            if (!families.exists(familyId)) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
            family = familyId;
        } else {
            family = families.create(actor, newFamilyLabel);
        }
        UUID id = insert(family, first, last, birthDate, ref, clock.instant());
        audit.write(
                actor, AuditLog.ActorKind.USER, "CHILD_CREATED", "child", id, Map.of("familyId", family.toString()));
        return get(id);
    }

    /** Merge patch: {@code null} keeps a value, an empty {@code externalRef} clears it. */
    @Transactional
    @AdminOnly
    public ChildView update(
            UUID actor,
            UUID id,
            @Nullable String firstName,
            @Nullable String lastName,
            @Nullable LocalDate birthDate,
            @Nullable String externalRef,
            @Nullable UUID familyId) {
        Child child = children.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        String ref = externalRef == null ? child.externalRef() : FamilyService.blankToNull(externalRef);
        if (ref != null && children.externalRefTaken(ref, id)) {
            throw new ApiException(ErrorCode.EXTERNAL_REF_TAKEN);
        }
        if (familyId != null && !families.exists(familyId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        if (birthDate != null) {
            requireNotFuture(birthDate);
        }
        Child updated = new Child(
                id,
                familyId == null ? child.familyId() : familyId,
                firstName == null ? child.firstName() : requireName("firstName", firstName),
                lastName == null ? child.lastName() : requireName("lastName", lastName),
                birthDate == null ? child.birthDate() : birthDate,
                ref);
        try {
            children.update(updated, clock.instant());
        } catch (DuplicateKeyException ex) {
            throw new ApiException(ErrorCode.EXTERNAL_REF_TAKEN);
        }
        audit.write(
                actor,
                AuditLog.ActorKind.USER,
                updated.familyId().equals(child.familyId()) ? "CHILD_UPDATED" : "CHILD_FAMILY_CHANGED",
                "child",
                id,
                Map.of("familyId", updated.familyId().toString()));
        return get(id);
    }

    @Transactional
    @AdminOnly
    public void enroll(UUID actor, UUID childId, UUID schoolYearId) {
        requireChild(childId);
        schoolYears.requireOpen(schoolYearId);
        if (children.enroll(childId, schoolYearId)) {
            audit.write(
                    actor,
                    AuditLog.ActorKind.USER,
                    "CHILD_ENROLLED",
                    "child",
                    childId,
                    Map.of("schoolYearId", schoolYearId.toString()));
        }
    }

    @Transactional
    @AdminOnly
    public void unenroll(UUID actor, UUID childId, UUID schoolYearId) {
        requireChild(childId);
        schoolYears.requireOpen(schoolYearId);
        if (children.unenroll(childId, schoolYearId)) {
            audit.write(
                    actor,
                    AuditLog.ActorKind.USER,
                    "CHILD_UNENROLLED",
                    "child",
                    childId,
                    Map.of("schoolYearId", schoolYearId.toString()));
        }
    }

    UUID insert(UUID familyId, String first, String last, LocalDate birthDate, @Nullable String ref, Instant now) {
        try {
            return children.insert(familyId, first, last, birthDate, ref, now);
        } catch (DuplicateKeyException ex) {
            throw new ApiException(ErrorCode.EXTERNAL_REF_TAKEN);
        }
    }

    private ChildView view(Child child) {
        return new ChildView(child, families.displayLabel(child.familyId()), children.enrollmentsOf(child.id()));
    }

    private void requireChild(UUID id) {
        if (children.findById(id).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
    }

    private void requireNotFuture(LocalDate birthDate) {
        if (birthDate.isAfter(BusinessTime.today(clock))) {
            throw ApiException.validation("birthDate", "PastOrPresent", "must not be in the future");
        }
    }

    private static String requireName(String field, String value) {
        String trimmed = value.strip();
        if (trimmed.isEmpty()) {
            throw ApiException.validation(field, "NotBlank", "must not be blank");
        }
        return trimmed;
    }
}
