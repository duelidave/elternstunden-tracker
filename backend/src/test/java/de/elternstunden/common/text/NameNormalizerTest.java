package de.elternstunden.common.text;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** AC-REG-09: the test vectors of Lastenheft 7.1.3. */
class NameNormalizerTest {

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            ignoreLeadingAndTrailingWhitespace = false,
            value = {
                "Müller|mueller",
                "  MÜLLER |mueller",
                "Mueller|mueller",
                "Anna-Lena|anna lena",
                "Straße|strasse",
                "O'Brien|o brien",
                "Çağla|cagla",
            })
    void normalizesTheSpecifiedVectors(String input, String expected) {
        assertThat(NameNormalizer.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                // decomposed umlaut (u + combining diaeresis) is composed first
                "Müller|mueller",
                "Anna – Lena|anna lena",
                "D’Angelo|d angelo",
                "ANNA   LENA|anna lena",
                "ẞtraße|sstrasse",
                "José|jose",
            })
    void handlesFurtherSpellings(String input, String expected) {
        assertThat(NameNormalizer.normalize(input)).isEqualTo(expected);
    }
}
