package de.elternstunden.common.time;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The one clock of the application (Lastenheft 0.4 rule 7). Business code gets this {@link Clock} injected and passes
 * the time into SQL as a parameter, so tests can control time.
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
