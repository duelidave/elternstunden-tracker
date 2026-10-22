package de.elternstunden.family.service;

import static org.assertj.core.api.Assertions.assertThat;

import de.elternstunden.family.service.ChildImportService.RowError;
import de.elternstunden.family.service.ChildImportService.RowErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Parsing of the CSV import (7.7). */
class ChildImportParsingTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

    @Test
    void readsBothDateFormatsQuotesAndABom() {
        List<RowError> errors = new ArrayList<>();
        String csv = String.join(
                "\r\n",
                "\uFEFFFirst_Name;LAST_NAME;birth_date;family_key",
                "\"Anna\";Müller;03.05.2017;f1",
                "",
                "Ben;Müller;2019-01-31;f1",
                "");
        var rows = ChildImportService.parse(csv, TODAY, errors);

        assertThat(errors).isEmpty();
        assertThat(rows).extracting(ChildImportService.Row::firstName).containsExactly("Anna", "Ben");
        assertThat(rows.getFirst().birthDate()).isEqualTo(LocalDate.of(2017, 5, 3));
        assertThat(rows.getFirst().line()).isEqualTo(2);
        assertThat(rows.get(1).line()).as("blank lines keep the numbering").isEqualTo(4);
    }

    @Test
    void reportsOneErrorPerFaultyLine() {
        List<RowError> errors = new ArrayList<>();
        ChildImportService.parse("""
                first_name;last_name;birth_date;family_key;external_ref
                Zu;Spät;2027-01-01;f;
                Lang;%s;2018-01-01;f;
                Ohne;Key;2018-01-01;;
                """.formatted("x".repeat(81)), TODAY, errors);

        assertThat(errors)
                .extracting(RowError::code)
                .containsExactly(RowErrorCode.INVALID_DATE, RowErrorCode.VALUE_TOO_LONG, RowErrorCode.MISSING_VALUE);
    }

    @Test
    void rejectsImpossibleDates() {
        assertThat(ChildImportService.date("29.02.2019")).isEmpty();
        assertThat(ChildImportService.date("29.02.2020")).hasValue(LocalDate.of(2020, 2, 29));
        assertThat(ChildImportService.date("2020-13-01")).isEmpty();
    }
}
