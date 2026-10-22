package de.elternstunden.family.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AdminOnly;
import de.elternstunden.common.text.Search;
import de.elternstunden.family.domain.Child;
import de.elternstunden.family.domain.Family;
import de.elternstunden.family.domain.Member;
import de.elternstunden.family.repo.ChildRepository;
import de.elternstunden.family.repo.FamilyRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Families for admins (FS-ADM 7.7): list, detail, label and note. Merging families is phase 2. */
@Service
public class FamilyService {

    /** A family with what the admin list shows. */
    public record Summary(Family family, String displayLabel, List<Child> children, int activeMembers) {}

    /** A family with children and all members. */
    public record Detail(Family family, String displayLabel, List<Child> children, List<Member> members) {}

    private final FamilyRepository families;
    private final ChildRepository children;
    private final AuditLog audit;
    private final Clock clock;

    public FamilyService(FamilyRepository families, ChildRepository children, AuditLog audit, Clock clock) {
        this.families = families;
        this.children = children;
        this.audit = audit;
        this.clock = clock;
    }

    /**
     * All families matching the search in label, children's names, members' display names and e-mail addresses,
     * sorted by display label.
     */
    @AdminOnly
    public List<Summary> search(@Nullable String query) {
        Search search = Search.of(query);
        Map<UUID, String> labels = families.displayLabels();
        Map<UUID, List<Child>> childrenByFamily =
                children.findAll().stream().collect(Collectors.groupingBy(Child::familyId));
        Map<UUID, Integer> memberCounts = families.activeMemberCounts();
        Map<UUID, List<Member>> members = search.isActive() ? families.membersByFamily() : Map.of();
        List<Summary> result = new ArrayList<>();
        for (Family family : families.findAll()) {
            List<Child> familyChildren = childrenByFamily.getOrDefault(family.id(), List.of());
            List<String> haystack = new ArrayList<>();
            haystack.add(family.label());
            familyChildren.forEach(child -> {
                haystack.add(child.firstName());
                haystack.add(child.lastName());
            });
            members.getOrDefault(family.id(), List.of()).forEach(member -> {
                haystack.add(member.displayName());
                haystack.add(member.email());
            });
            if (search.matches(haystack)) {
                result.add(new Summary(
                        family,
                        labels.getOrDefault(family.id(), ""),
                        familyChildren,
                        memberCounts.getOrDefault(family.id(), 0)));
            }
        }
        result.sort(Comparator.comparing(Summary::displayLabel, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(summary -> summary.family().id()));
        return result;
    }

    @AdminOnly
    public Detail get(UUID id) {
        Family family = families.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return new Detail(family, families.displayLabel(id), children.findByFamily(id), families.members(id));
    }

    public String displayLabel(UUID id) {
        return families.displayLabel(id);
    }

    public Map<UUID, String> displayLabels() {
        return families.displayLabels();
    }

    public boolean exists(UUID id) {
        return families.exists(id);
    }

    /** Merge patch: {@code null} keeps the value, an empty text clears it. */
    @Transactional
    @AdminOnly
    public Detail update(UUID actor, UUID id, @Nullable String label, @Nullable String note) {
        Family family = families.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        families.update(
                id,
                label == null ? family.label() : blankToNull(label),
                note == null ? family.note() : blankToNull(note),
                clock.instant());
        audit.user(actor, "FAMILY_UPDATED", "family", id);
        return get(id);
    }

    /** A new family, e.g. for a new child (7.7). */
    @Transactional
    @AdminOnly
    public UUID create(UUID actor, @Nullable String label) {
        UUID id = families.insert(label == null ? null : blankToNull(label), clock.instant());
        audit.user(actor, "FAMILY_CREATED", "family", id);
        return id;
    }

    static @Nullable String blankToNull(String value) {
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
