package de.elternstunden.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class BusinessTimeTest {

    @Test
    void todayIsTheBerlinDateNotTheUtcDate() {
        // 23:30 UTC on 31 Aug is already 1 Sep in Berlin (summer time, UTC+2): new school year (5.5)
        Clock clock = Clock.fixed(Instant.parse("2027-08-31T22:30:00Z"), ZoneOffset.UTC);

        assertThat(BusinessTime.today(clock)).isEqualTo(LocalDate.of(2027, 9, 1));
    }

    @Test
    void handlesWinterTime() {
        // UTC+1 in winter: 23:30 UTC on 31 Dec is 00:30 on 1 Jan in Berlin
        Clock clock = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.UTC);

        assertThat(BusinessTime.today(clock)).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void handlesTheDaylightSavingSwitchDay() {
        // 28 Mar 2027: clocks jump from 02:00 to 03:00 in Berlin; 00:30 UTC is 01:30 local, same date
        Clock clock = Clock.fixed(Instant.parse("2027-03-28T00:30:00Z"), ZoneOffset.UTC);

        assertThat(BusinessTime.today(clock)).isEqualTo(LocalDate.of(2027, 3, 28));
    }
}
