package de.elternstunden.schoolyear.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.time.BusinessTime;
import de.elternstunden.schoolyear.repo.SchoolYearRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Month;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily job (5.5 rule 3): creates the next school year with the default cut-off dates (1 Sep to 31 Aug) as soon as it
 * is missing 90 days before it starts. Never creates a duplicate or an overlap (AC-SY-04).
 */
@Component
public class SchoolYearJob {

    private final SchoolYearRepository schoolYears;
    private final AuditLog audit;
    private final AppProperties.SchoolYears settings;
    private final Clock clock;

    public SchoolYearJob(SchoolYearRepository schoolYears, AuditLog audit, AppProperties properties, Clock clock) {
        this.schoolYears = schoolYears;
        this.audit = audit;
        this.settings = properties.schoolYears();
        this.clock = clock;
    }

    @Scheduled(cron = "${app.school-years.job-cron}", zone = "Europe/Berlin")
    @Transactional
    public Optional<UUID> run() {
        LocalDate today = BusinessTime.today(clock);
        LocalDate nextStart = nextDefaultStart(today);
        if (today.isBefore(nextStart.minusDays(settings.leadTime().toDays()))) {
            return Optional.empty();
        }
        LocalDate end = nextStart.plusYears(1).minusDays(1);
        Optional<UUID> created = schoolYears.insertIfFree(label(nextStart), nextStart, end, clock.instant());
        created.ifPresent(
                id -> audit.system("SCHOOL_YEAR_CREATED", "school_year", id, Map.of("startsOn", nextStart.toString())));
        return created;
    }

    /** The next 1 September after today. */
    static LocalDate nextDefaultStart(LocalDate today) {
        LocalDate thisYear = LocalDate.of(today.getYear(), Month.SEPTEMBER, 1);
        return thisYear.isAfter(today) ? thisYear : thisYear.plusYears(1);
    }

    /** {@code 2026/27} for a year starting in 2026 (5.5 rule 1). */
    static String label(LocalDate start) {
        return String.format(Locale.ROOT, "%d/%02d", start.getYear(), (start.getYear() + 1) % 100);
    }
}
