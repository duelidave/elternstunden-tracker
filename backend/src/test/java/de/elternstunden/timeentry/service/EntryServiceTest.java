package de.elternstunden.timeentry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.elternstunden.common.error.ApiException;
import de.elternstunden.timeentry.domain.EntryChange;
import de.elternstunden.timeentry.domain.EntryContent;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EntryServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 14);

    @ParameterizedTest
    @ValueSource(ints = {0, 10, 20, 735, -15})
    void durationOffTheGridIsRejected(int minutes) {
        assertThatThrownBy(() -> EntryService.normalize(new EntryContent(DAY, null, minutes, "Garten")))
                .isInstanceOf(ApiException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {15, 135, 720})
    void durationOnTheGridIsAccepted(int minutes) {
        assertThat(EntryService.normalize(new EntryContent(DAY, null, minutes, "Garten"))
                        .durationMinutes())
                .isEqualTo(minutes);
    }

    @Test
    void descriptionIsTrimmedAndCountedInCharacters() {
        assertThat(EntryService.normalize(new EntryContent(DAY, null, 60, "  abc \n"))
                        .description())
                .isEqualTo("abc");
        assertThatThrownBy(() -> EntryService.normalize(new EntryContent(DAY, null, 60, "  ab  ")))
                .isInstanceOf(ApiException.class);
        // 255 emoji are 510 UTF-16 units but 255 characters, like char_length in the database
        assertThat(EntryService.normalize(new EntryContent(DAY, null, 60, "🌱".repeat(255)))
                        .description())
                .hasSize(510);
        assertThatThrownBy(() -> EntryService.normalize(new EntryContent(DAY, null, 60, "🌱".repeat(256))))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void mergePatchKeepsMissingFieldsAndCanRemoveTheStartTime() {
        EntryContent current = new EntryContent(DAY, LocalTime.of(15, 30), 60, "Laternenfest");

        assertThat(new EntryChange(null, false, null, 90, null).applyTo(current))
                .isEqualTo(new EntryContent(DAY, LocalTime.of(15, 30), 90, "Laternenfest"));
        assertThat(new EntryChange(null, true, null, null, null).applyTo(current))
                .isEqualTo(new EntryContent(DAY, null, 60, "Laternenfest"));
    }
}
