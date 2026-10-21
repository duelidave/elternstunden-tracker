package de.elternstunden.dev;

import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Dev and prod together would expose the role switcher in production: the start fails (FS-DEV 7.12, AC-DEV-01). */
@Component
@Profile("dev")
public class DevProfileGuard {

    public DevProfileGuard(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("The profiles dev and prod must never be active together");
        }
    }
}
