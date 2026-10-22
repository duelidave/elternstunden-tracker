package de.elternstunden.schoolyear.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.schoolyear.domain.SchoolYear;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** School year rules of 5.5. */
class SchoolYearRulesTest {

    private static final SchoolYear Y2026 = year("2026/27", "2026-09-01", "2027-07-31");
    private static final SchoolYear Y2027 = year("2027/28", "2027-09-01", "2028-08-31");

    @Test
    void theCurrentYearContainsToday() {
        assertThat(SchoolYearService.current(List.of(Y2027, Y2026), LocalDate.of(2027, 7, 31)))
                .hasValue(Y2026);
        assertThat(SchoolYearService.current(List.of(Y2027, Y2026), LocalDate.of(2027, 9, 1)))
                .hasValue(Y2027);
    }

    /** 5.5 rule 4: in a gap the year that ended last counts as current. */
    @Test
    void inAGapTheYearThatEndedLastIsCurrent() {
        assertThat(SchoolYearService.current(List.of(Y2027, Y2026), LocalDate.of(2027, 8, 15)))
                .hasValue(Y2026);
        assertThat(SchoolYearService.current(List.of(Y2027), LocalDate.of(2027, 8, 15)))
                .isEmpty();
    }

    @Test
    void theNextDefaultYearStartsOnTheFirstOfSeptember() {
        assertThat(SchoolYearJob.nextDefaultStart(LocalDate.of(2026, 8, 31))).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(SchoolYearJob.nextDefaultStart(LocalDate.of(2026, 9, 1))).isEqualTo(LocalDate.of(2027, 9, 1));
        assertThat(SchoolYearJob.label(LocalDate.of(2026, 9, 1))).isEqualTo("2026/27");
        assertThat(SchoolYearJob.label(LocalDate.of(2099, 9, 1))).isEqualTo("2099/00");
    }

    private static SchoolYear year(String label, String startsOn, String endsOn) {
        return new SchoolYear(UUID.randomUUID(), label, LocalDate.parse(startsOn), LocalDate.parse(endsOn), 600, null);
    }
}
