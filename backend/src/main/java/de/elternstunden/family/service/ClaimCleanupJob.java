package de.elternstunden.family.service;

import de.elternstunden.common.config.AppProperties;
import de.elternstunden.family.repo.ClaimRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DP-05, OPEN-16 exception: child data in claims may come from unauthorized people, so done and superseded claims
 * are deleted after 30 days. The open claim of an unassigned user stays until an admin assigns.
 */
@Component
public class ClaimCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(ClaimCleanupJob.class);

    private final ClaimRepository claims;
    private final AppProperties.Claims settings;
    private final Clock clock;

    public ClaimCleanupJob(ClaimRepository claims, AppProperties properties, Clock clock) {
        this.claims = claims;
        this.settings = properties.claims();
        this.clock = clock;
    }

    @Scheduled(cron = "${app.auth.cleanup-cron}", zone = "Europe/Berlin")
    @Transactional
    public int run() {
        int deleted = claims.deleteClosedBefore(clock.instant().minus(settings.retention()));
        log.info("Claim cleanup removed {} claims", deleted);
        return deleted;
    }
}
