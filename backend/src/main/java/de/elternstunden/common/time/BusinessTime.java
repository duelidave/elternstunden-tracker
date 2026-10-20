package de.elternstunden.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Business dates are calendar days in Europe/Berlin (Lastenheft 0.4 rule 5); timestamps stay UTC. */
public final class BusinessTime {

    public static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    private BusinessTime() {}

    /** Today's date in Europe/Berlin according to the given clock. */
    public static LocalDate today(Clock clock) {
        return LocalDate.now(clock.withZone(ZONE));
    }
}
