package de.elternstunden.report.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.security.AdminOnly;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.family.domain.Child;
import de.elternstunden.family.service.FamilyService;
import de.elternstunden.overview.repo.OverviewRepository;
import de.elternstunden.report.domain.ReportKind;
import de.elternstunden.report.domain.ReportScope;
import de.elternstunden.schoolyear.domain.SchoolYear;
import de.elternstunden.schoolyear.service.SchoolYearService;
import de.elternstunden.timeentry.domain.EntryStatus;
import de.elternstunden.timeentry.domain.EntryView;
import de.elternstunden.timeentry.service.EntryService;
import java.text.Collator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/**
 * Billing as PDF (FS-PDF 7.8, D-12): the total list of all families owing hours, or the entries of one family. Only
 * confirmed minutes count (D-10). No birth dates and no e-mail addresses (rule 2); generated on demand and never
 * stored, every generation is audited without contents (rule 5).
 */
@Service
@AdminOnly
public class BillingReportService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.GERMAN);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm", Locale.GERMAN);
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** The PDF with its download name; handed over once, never compared, so the array is fine here. */
    @SuppressWarnings("ArrayRecordComponent")
    public record Report(String fileName, byte[] pdf) {}

    /** One line of the total list; all values formatted for display. */
    public record FamilyLine(
            String label, String children, String confirmed, String required, String missing, boolean fulfilled) {}

    /** One entry of a family's billing. */
    public record EntryLine(String date, String time, String duration, String description, String confirmedAt) {}

    private final SchoolYearService schoolYears;
    private final FamilyService families;
    private final OverviewRepository overview;
    private final EntryService entries;
    private final PdfRenderer renderer;
    private final AuditLog audit;
    private final Clock clock;

    public BillingReportService(
            SchoolYearService schoolYears,
            FamilyService families,
            OverviewRepository overview,
            EntryService entries,
            PdfRenderer renderer,
            AuditLog audit,
            Clock clock) {
        this.schoolYears = schoolYears;
        this.families = families;
        this.overview = overview;
        this.entries = entries;
        this.renderer = renderer;
        this.audit = audit;
        this.clock = clock;
    }

    public Report billing(
            UUID actor,
            @Nullable UUID schoolYearId,
            ReportScope scope,
            @Nullable UUID familyId,
            @Nullable ReportKind requestedKind) {
        SchoolYear year = schoolYearId == null
                ? schoolYears.current().orElseThrow(() -> new ApiException(ErrorCode.NO_CURRENT_SCHOOL_YEAR))
                : schoolYears.get(schoolYearId);
        LocalDate today = BusinessTime.today(clock);
        boolean finished = year.closed() || year.endsOn().isBefore(today);
        ReportKind kind = requestedKind == null ? (finished ? ReportKind.FINAL : ReportKind.INTERIM) : requestedKind;
        if (kind == ReportKind.FINAL && !finished) {
            throw new ApiException(ErrorCode.SCHOOL_YEAR_NOT_FINISHED);
        }
        if (scope == ReportScope.FAMILY && familyId == null) {
            throw ApiException.validation("familyId", "NotNull", "required for scope FAMILY");
        }

        Map<String, Object> variables = new HashMap<>();
        variables.put("schoolYear", year.label());
        variables.put("isFinal", kind == ReportKind.FINAL);
        variables.put(
                "createdAt",
                ZonedDateTime.now(clock.withZone(BusinessTime.ZONE)).format(DATE_TIME));
        variables.put("required", formatDuration(year.requiredMinutes()));
        byte[] pdf;
        if (scope == ReportScope.ALL) {
            pdf = renderer.render("report/billing-all", allFamilies(year, variables));
        } else {
            UUID family = familyId;
            if (family == null || !families.exists(family)) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
            pdf = renderer.render("report/billing-family", oneFamily(year, family, variables));
        }

        Map<String, Object> details = new HashMap<>();
        details.put("scope", scope.name());
        details.put("kind", kind.name());
        details.put("schoolYearId", year.id().toString());
        if (familyId != null) {
            details.put("familyId", familyId.toString());
        }
        audit.write(actor, AuditLog.ActorKind.USER, "REPORT_GENERATED", "school_year", year.id(), details);
        String fileName = "elternstunden_" + year.label().replace('/', '-') + "_" + scope.name() + "_"
                + today.format(FILE_DATE) + ".pdf";
        return new Report(fileName, pdf);
    }

    private Map<String, Object> allFamilies(SchoolYear year, Map<String, Object> variables) {
        Map<UUID, OverviewRepository.Minutes> minutes = overview.minutesByFamily(year.id());
        Collator german = Collator.getInstance(Locale.GERMAN);
        List<FamilyLine> lines = families.search(null).stream()
                .filter(family -> minutes.containsKey(family.family().id()))
                .sorted(Comparator.comparing(FamilyService.Summary::displayLabel, german))
                .map(family -> {
                    OverviewRepository.Minutes familyMinutes =
                            minutes.get(family.family().id());
                    return new FamilyLine(
                            family.displayLabel(),
                            names(family.children()),
                            formatDuration(familyMinutes.confirmedMinutes()),
                            formatDuration(year.requiredMinutes()),
                            formatDuration(familyMinutes.missingMinutes()),
                            familyMinutes.missingMinutes() == 0);
                })
                .toList();
        variables.put("families", lines);
        variables.put("familyCount", lines.size());
        variables.put(
                "fulfilledCount", lines.stream().filter(FamilyLine::fulfilled).count());
        return variables;
    }

    private Map<String, Object> oneFamily(SchoolYear year, UUID familyId, Map<String, Object> variables) {
        // Oldest first, like a bill
        List<EntryView> all = entries.ofFamily(familyId, year.id()).reversed();
        List<EntryView> confirmed = all.stream()
                .filter(view -> view.entry().status() == EntryStatus.CONFIRMED)
                .toList();
        List<EntryView> open = all.stream()
                .filter(view -> view.entry().status() == EntryStatus.OPEN)
                .toList();
        int confirmedMinutes = sum(confirmed);
        int missing = Math.max(year.requiredMinutes() - confirmedMinutes, 0);
        variables.put("family", families.displayLabel(familyId));
        variables.put("children", names(families.childrenOf(familyId)));
        variables.put("obligated", families.obligated(familyId, year.id()));
        variables.put("confirmed", confirmed.stream().map(this::line).toList());
        variables.put("open", open.stream().map(this::line).toList());
        variables.put("confirmedSum", formatDuration(confirmedMinutes));
        variables.put("openSum", formatDuration(sum(open)));
        variables.put("missing", formatDuration(missing));
        variables.put("fulfilled", missing == 0);
        return variables;
    }

    private EntryLine line(EntryView view) {
        var entry = view.entry();
        return new EntryLine(
                entry.workDate().format(DATE),
                entry.startTime() == null ? "–" : entry.startTime().toString(),
                formatDuration(entry.durationMinutes()),
                entry.description(),
                entry.confirmedAt() == null
                        ? "–"
                        : entry.confirmedAt().atZone(BusinessTime.ZONE).format(DATE));
    }

    private static int sum(List<EntryView> views) {
        return views.stream().mapToInt(view -> view.entry().durationMinutes()).sum();
    }

    /** Rule 2: children with first and last name, nothing else. */
    private static String names(List<Child> children) {
        return children.stream()
                .map(child -> child.firstName() + " " + child.lastName())
                .collect(Collectors.joining(", "));
    }

    /** Rule 3: {@code x h y min}, never decimal hours. */
    static String formatDuration(int minutes) {
        int hours = minutes / 60;
        int rest = minutes % 60;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }
}
