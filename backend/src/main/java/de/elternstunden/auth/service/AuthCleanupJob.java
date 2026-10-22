package de.elternstunden.auth.service;

import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.auth.repo.UserTokenRepository;
import de.elternstunden.common.config.AppProperties;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Daily cleanup (7.2 "Aufräumen", 7.1.4 step 9): expired and redeemed tokens, and accounts that stayed unconfirmed
 * for 24 hours without a valid token. Claim retention (DP-05): ClaimCleanupJob.
 */
@Component
public class AuthCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(AuthCleanupJob.class);

    private final UserTokenRepository tokens;
    private final AppUserRepository users;
    private final AppProperties.Auth settings;
    private final Clock clock;

    public AuthCleanupJob(UserTokenRepository tokens, AppUserRepository users, AppProperties properties, Clock clock) {
        this.tokens = tokens;
        this.users = users;
        this.settings = properties.auth();
        this.clock = clock;
    }

    @Scheduled(cron = "${app.auth.cleanup-cron}", zone = "Europe/Berlin")
    @Transactional
    public void run() {
        Instant now = clock.instant();
        int deletedTokens = tokens.deleteExpiredOrUsed(now);
        int deletedAccounts = users.deleteStalePending(now.minus(settings.pendingAccountTtl()), now);
        log.info("Auth cleanup removed {} tokens and {} unconfirmed accounts", deletedTokens, deletedAccounts);
    }
}
