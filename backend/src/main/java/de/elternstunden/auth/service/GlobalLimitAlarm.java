package de.elternstunden.auth.service;

import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.mail.MailService;
import de.elternstunden.common.ratelimit.RateLimiter;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

/**
 * 9.1: when a global limit (registration, link requests) is reached, the master gets an alarm mail, at most one per
 * hour and limit. Many requests with valid captchas in a short time mean an attack or a broken client.
 */
@Component
public class GlobalLimitAlarm {

    private static final Logger log = LoggerFactory.getLogger(GlobalLimitAlarm.class);
    private static final AppProperties.Limit ONE_PER_HOUR = new AppProperties.Limit(1, Duration.ofHours(1));

    private final AppProperties.Master master;
    private final RateLimiter rateLimiter;
    private final MailService mailService;
    private final AuthMails mails;

    public GlobalLimitAlarm(
            AppProperties properties, RateLimiter rateLimiter, MailService mailService, AuthMails mails) {
        this.master = properties.master();
        this.rateLimiter = rateLimiter;
        this.mailService = mailService;
        this.mails = mails;
    }

    /** {@code limit} names the limit in the mail, without any personal data. */
    public void reached(String limit) {
        log.warn("Global rate limit reached: {}", limit);
        if (!master.configured() || !rateLimiter.tryAcquire("global-limit-alarm", limit, ONE_PER_HOUR)) {
            return;
        }
        try {
            mailService.send(mails.globalLimitAlarm(master.email(), limit));
        } catch (MailException ex) {
            log.warn(
                    "Global limit alarm mail could not be sent ({})",
                    ex.getClass().getSimpleName());
        }
    }
}
