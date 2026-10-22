package de.elternstunden.family.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AdminOnly;
import de.elternstunden.common.text.NameNormalizer;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.family.domain.Child;
import de.elternstunden.family.repo.ChildRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CSV import of children (7.7, OPEN-03): {@code first_name;last_name;birth_date;family_key;external_ref}. Rows with
 * the same {@code family_key} form a family. Existing children are found by {@code external_ref}, otherwise by
 * normalized name and birth date, so importing the same file again changes nothing. All or nothing: with any faulty
 * row nothing is written. The file itself is never stored.
 */
@Service
@AdminOnly
public class ChildImportService {

    static final int MAX_BYTES = 1024 * 1024;
    static final int MAX_NAME = 80;
    static final int MAX_KEY = 100;
    private static final List<String> HEADER =
            List.of("first_name", "last_name", "birth_date", "family_key", "external_ref");
    private static final DateTimeFormatter GERMAN_DATE =
            DateTimeFormatter.ofPattern("dd.MM.uuuu").withResolverStyle(ResolverStyle.STRICT);

    public enum RowErrorCode {
        INVALID_HEADER,
        INVALID_COLUMNS,
        MISSING_VALUE,
        VALUE_TOO_LONG,
        INVALID_DATE,
        DUPLICATE_CHILD,
        EXTERNAL_REF_CONFLICT,
        FAMILY_KEY_CONFLICT
    }

    public record RowError(int line, RowErrorCode code) {}

    public record Result(
            boolean dryRun, boolean applied, int created, int updated, int unchanged, List<RowError> errors) {}

    record Row(
            int line,
            String firstName,
            String lastName,
            LocalDate birthDate,
            String familyKey,
            @Nullable String externalRef) {}

    /** What happens to one row. {@code existing} is empty for a new child. */
    record Planned(Row row, Optional<Child> existing) {}

    private final ChildRepository children;
    private final FamilyService families;
    private final AuditLog audit;
    private final Clock clock;

    public ChildImportService(ChildRepository children, FamilyService families, AuditLog audit, Clock clock) {
        this.children = children;
        this.families = families;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Result importCsv(UUID actor, String csv, boolean dryRun) {
        if (csv.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new ApiException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        List<RowError> errors = new ArrayList<>();
        List<Row> rows = parse(csv, BusinessTime.today(clock), errors);
        List<Planned> plan = plan(rows, errors);
        Map<String, UUID> familyOfKey = existingFamilies(plan, errors);

        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (Planned planned : plan) {
            if (planned.existing().isEmpty()) {
                created++;
            } else if (changed(planned)) {
                updated++;
            } else {
                unchanged++;
            }
        }
        errors.sort((a, b) -> Integer.compare(a.line(), b.line()));
        if (!errors.isEmpty() || dryRun) {
            return new Result(dryRun, false, created, updated, unchanged, List.copyOf(errors));
        }

        Instant now = clock.instant();
        Map<String, UUID> newFamilies = new HashMap<>();
        for (Planned planned : plan) {
            Row row = planned.row();
            if (planned.existing().isEmpty()) {
                UUID familyId = familyOfKey.get(row.familyKey());
                if (familyId == null) {
                    familyId = newFamilies.computeIfAbsent(row.familyKey(), key -> families.create(actor, null));
                }
                children.insert(familyId, row.firstName(), row.lastName(), row.birthDate(), row.externalRef(), now);
            } else if (changed(planned)) {
                children.update(merged(planned), now);
            }
        }
        audit.write(
                actor,
                AuditLog.ActorKind.USER,
                "CHILDREN_IMPORTED",
                "child",
                null,
                Map.of("created", created, "updated", updated, "unchanged", unchanged));
        return new Result(false, true, created, updated, unchanged, List.of());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Parsing
    // ---------------------------------------------------------------------------------------------------------

    static List<Row> parse(String csv, LocalDate today, List<RowError> errors) {
        String content = csv.startsWith("﻿") ? csv.substring(1) : csv;
        String[] lines = content.split("\r?\n", -1);
        List<String> header = cells(lines[0]).stream()
                .map(cell -> cell.toLowerCase(Locale.ROOT))
                .toList();
        if (!header.equals(HEADER) && !header.equals(HEADER.subList(0, 4))) {
            errors.add(new RowError(1, RowErrorCode.INVALID_HEADER));
            return List.of();
        }
        List<Row> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                continue;
            }
            int line = i + 1;
            List<String> cells = cells(lines[i]);
            if (cells.size() < 4 || cells.size() > 5) {
                errors.add(new RowError(line, RowErrorCode.INVALID_COLUMNS));
                continue;
            }
            RowErrorCode error = check(cells);
            if (error != null) {
                errors.add(new RowError(line, error));
                continue;
            }
            Optional<LocalDate> birthDate = date(cells.get(2)).filter(date -> !date.isAfter(today));
            if (birthDate.isEmpty()) {
                errors.add(new RowError(line, RowErrorCode.INVALID_DATE));
                continue;
            }
            String ref = cells.size() == 5 && !cells.get(4).isEmpty() ? cells.get(4) : null;
            rows.add(new Row(line, cells.get(0), cells.get(1), birthDate.get(), cells.get(3), ref));
        }
        return rows;
    }

    private static @Nullable RowErrorCode check(List<String> cells) {
        for (int i = 0; i < 4; i++) {
            if (cells.get(i).isEmpty()) {
                return RowErrorCode.MISSING_VALUE;
            }
        }
        if (cells.get(0).length() > MAX_NAME || cells.get(1).length() > MAX_NAME) {
            return RowErrorCode.VALUE_TOO_LONG;
        }
        if (cells.get(3).length() > MAX_KEY
                || (cells.size() == 5 && cells.get(4).length() > MAX_KEY)) {
            return RowErrorCode.VALUE_TOO_LONG;
        }
        return null;
    }

    /** Splits by {@code ;}, trims and removes surrounding double quotes (spreadsheet exports). */
    private static List<String> cells(String line) {
        List<String> cells = new ArrayList<>();
        for (String cell : line.split(";", -1)) {
            String value = cell.strip();
            if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1)
                        .replace("\"\"", "\"")
                        .strip();
            }
            cells.add(value);
        }
        return cells;
    }

    /** {@code YYYY-MM-DD} or {@code DD.MM.YYYY}. */
    static Optional<LocalDate> date(String value) {
        try {
            return Optional.of(value.contains(".") ? LocalDate.parse(value, GERMAN_DATE) : LocalDate.parse(value));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Planning: find existing children and families
    // ---------------------------------------------------------------------------------------------------------

    private List<Planned> plan(List<Row> rows, List<RowError> errors) {
        List<Planned> plan = new ArrayList<>();
        Set<String> seenIdentities = new HashSet<>();
        Set<String> seenRefs = new HashSet<>();
        Set<UUID> seenChildren = new HashSet<>();
        for (Row row : rows) {
            String identity = NameNormalizer.normalize(row.firstName()) + "|" + NameNormalizer.normalize(row.lastName())
                    + "|" + row.birthDate();
            if (!seenIdentities.add(identity) || (row.externalRef() != null && !seenRefs.add(row.externalRef()))) {
                errors.add(new RowError(row.line(), RowErrorCode.DUPLICATE_CHILD));
                continue;
            }
            Optional<Child> existing = Optional.empty();
            if (row.externalRef() != null) {
                existing = children.findByExternalRef(row.externalRef());
            }
            if (existing.isEmpty()) {
                List<Child> sameIdentity = children.findByIdentity(row.firstName(), row.lastName(), row.birthDate());
                if (sameIdentity.size() > 1) {
                    errors.add(new RowError(row.line(), RowErrorCode.DUPLICATE_CHILD));
                    continue;
                }
                existing = sameIdentity.stream().findFirst();
                if (existing.isPresent()
                        && existing.get().externalRef() != null
                        && row.externalRef() != null
                        && !existing.get().externalRef().equals(row.externalRef())) {
                    errors.add(new RowError(row.line(), RowErrorCode.EXTERNAL_REF_CONFLICT));
                    continue;
                }
            }
            if (existing.isPresent() && !seenChildren.add(existing.get().id())) {
                errors.add(new RowError(row.line(), RowErrorCode.DUPLICATE_CHILD));
                continue;
            }
            plan.add(new Planned(row, existing));
        }
        return plan;
    }

    /**
     * The family of each {@code family_key} that already has existing children. A key whose children sit in several
     * families, or a family claimed by several keys, is a conflict.
     */
    private static Map<String, UUID> existingFamilies(List<Planned> plan, List<RowError> errors) {
        Map<String, Set<UUID>> familiesOfKey = new LinkedHashMap<>();
        for (Planned planned : plan) {
            familiesOfKey.computeIfAbsent(planned.row().familyKey(), key -> new HashSet<>());
            planned.existing()
                    .ifPresent(child ->
                            familiesOfKey.get(planned.row().familyKey()).add(child.familyId()));
        }
        Map<String, UUID> result = new HashMap<>();
        Map<UUID, String> keyOfFamily = new HashMap<>();
        Set<String> conflicting = new HashSet<>();
        familiesOfKey.forEach((key, found) -> {
            if (found.size() > 1) {
                conflicting.add(key);
            } else if (found.size() == 1) {
                UUID family = found.iterator().next();
                String other = keyOfFamily.putIfAbsent(family, key);
                if (other != null) {
                    conflicting.add(key);
                } else {
                    result.put(key, family);
                }
            }
        });
        plan.stream()
                .filter(planned -> conflicting.contains(planned.row().familyKey()))
                .forEach(planned -> errors.add(new RowError(planned.row().line(), RowErrorCode.FAMILY_KEY_CONFLICT)));
        return result;
    }

    private static boolean changed(Planned planned) {
        Child existing = planned.existing().orElseThrow();
        return !merged(planned).equals(existing);
    }

    /** The existing child with the values of the row; a row without {@code external_ref} keeps the old one. */
    private static Child merged(Planned planned) {
        Child existing = planned.existing().orElseThrow();
        Row row = planned.row();
        return new Child(
                existing.id(),
                existing.familyId(),
                row.firstName(),
                row.lastName(),
                row.birthDate(),
                row.externalRef() != null ? row.externalRef() : existing.externalRef());
    }
}
