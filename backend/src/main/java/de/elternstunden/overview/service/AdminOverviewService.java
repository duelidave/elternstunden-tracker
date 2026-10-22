package de.elternstunden.overview.service;

import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AdminOnly;
import de.elternstunden.family.service.FamilyService;
import de.elternstunden.overview.repo.OverviewRepository;
import de.elternstunden.schoolyear.domain.SchoolYear;
import de.elternstunden.schoolyear.service.SchoolYearService;
import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Admin overview (FS-ADMOV 7.6, D-11): the families owing hours in a school year with confirmed, open and missing
 * minutes. The lists are small (1.2), so search, filter and sorting run in memory on top of the reference query (5.6).
 */
@Service
@AdminOnly
public class AdminOverviewService {

    /** One family in the overview. */
    public record Row(FamilyService.Summary family, OverviewRepository.Minutes minutes) {

        String childNames() {
            return family.children().stream()
                    .map(child -> child.firstName() + " " + child.lastName())
                    .collect(Collectors.joining(", "));
        }
    }

    /** {@code familyCount} and {@code missingCount} cover the whole year, regardless of search and filter. */
    public record Overview(SchoolYear schoolYear, int familyCount, int missingCount, List<Row> rows) {}

    /** The sort fields of the whitelist (SEC-07); the contract rejects everything else. */
    enum SortField {
        FAMILY_LABEL("familyLabel"),
        CHILDREN("children"),
        MEMBER_COUNT("memberCount"),
        CONFIRMED_MINUTES("confirmedMinutes"),
        OPEN_MINUTES("openMinutes"),
        MISSING_MINUTES("missingMinutes");

        private final String apiName;

        SortField(String apiName) {
            this.apiName = apiName;
        }

        static SortField of(String apiName) {
            for (SortField field : values()) {
                if (field.apiName.equals(apiName)) {
                    return field;
                }
            }
            throw ApiException.validation("sort", "Pattern", "unknown sort field");
        }
    }

    private final SchoolYearService schoolYears;
    private final FamilyService families;
    private final OverviewRepository overview;

    public AdminOverviewService(SchoolYearService schoolYears, FamilyService families, OverviewRepository overview) {
        this.schoolYears = schoolYears;
        this.families = families;
        this.overview = overview;
    }

    /** {@code sort} is {@code field,direction}; then always the family label ascending. */
    public Overview get(@Nullable UUID schoolYearId, @Nullable String query, boolean onlyMissing, String sort) {
        SchoolYear year = schoolYearId == null
                ? schoolYears.current().orElseThrow(() -> new ApiException(ErrorCode.NO_CURRENT_SCHOOL_YEAR))
                : schoolYears.get(schoolYearId);
        Map<UUID, OverviewRepository.Minutes> minutes = overview.minutesByFamily(year.id());
        List<Row> all = families.search(null).stream()
                .filter(family -> minutes.containsKey(family.family().id()))
                .map(family -> new Row(family, minutes.get(family.family().id())))
                .toList();
        Set<UUID> matching = query == null
                ? null
                : families.search(query).stream()
                        .map(family -> family.family().id())
                        .collect(Collectors.toSet());
        List<Row> rows = all.stream()
                .filter(row -> matching == null
                        || matching.contains(row.family().family().id()))
                .filter(row -> !onlyMissing || row.minutes().missingMinutes() > 0)
                .sorted(comparator(sort))
                .toList();
        int missing = (int)
                all.stream().filter(row -> row.minutes().missingMinutes() > 0).count();
        return new Overview(year, all.size(), missing, rows);
    }

    static Comparator<Row> comparator(String sort) {
        String[] parts = sort.split(",", 2);
        SortField field = SortField.of(parts[0]);
        boolean descending = parts.length == 2 && parts[1].equals("desc");
        Collator german = Collator.getInstance(Locale.GERMAN);
        Comparator<Row> byLabel = Comparator.comparing(row -> row.family().displayLabel(), german);
        Comparator<Row> primary = switch (field) {
            case FAMILY_LABEL -> byLabel;
            case CHILDREN -> Comparator.comparing(Row::childNames, german);
            case MEMBER_COUNT -> numeric(row -> row.family().activeMembers());
            case CONFIRMED_MINUTES -> numeric(row -> row.minutes().confirmedMinutes());
            case OPEN_MINUTES -> numeric(row -> row.minutes().openMinutes());
            case MISSING_MINUTES -> numeric(row -> row.minutes().missingMinutes());
        };
        return (descending ? primary.reversed() : primary)
                .thenComparing(byLabel)
                .thenComparing(row -> row.family().family().id());
    }

    private static Comparator<Row> numeric(Function<Row, Integer> value) {
        return Comparator.comparing(value);
    }
}
