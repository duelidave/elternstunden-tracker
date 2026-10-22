package de.elternstunden.auth.service;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.auth.repo.AuthRequestRepository;
import de.elternstunden.auth.repo.AuthRequestRepository.AuthRequest;
import de.elternstunden.auth.repo.UserTokenRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.crypto.Hashing;
import de.elternstunden.common.mail.MailMessage;
import de.elternstunden.common.mail.MailService;
import de.elternstunden.common.ratelimit.RateLimiter;
import de.elternstunden.family.domain.ChildData;
import de.elternstunden.family.service.ClaimService;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Processes the auth outbox (7.2 "Auth-Worker", 7.1.4 step 4). Per row, case distinction, token and mail run in one
 * transaction; the mail is sent last. If sending fails, the transaction rolls back and the row is retried at most
 * {@code mailMaxAttempts} times and only within the token lifetime. Each attempt decides anew, so retries are
 * idempotent. Per-address limits are counted once per request (first attempt only).
 */
@Component
public class AuthWorker {

    private static final Logger log = LoggerFactory.getLogger(AuthWorker.class);
    static final int TOKEN_BYTES = 32;

    private final AuthRequestRepository requests;
    private final AppUserRepository users;
    private final UserTokenRepository tokens;
    private final AuthMails mails;
    private final ClaimService claims;
    private final MailService mailService;
    private final RateLimiter rateLimiter;
    private final AuditLog audit;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final MeterRegistry meters;
    private final Clock clock;
    private final AppProperties.Auth settings;
    private final AppProperties.RateLimits limits;
    private final String keySecret;

    public AuthWorker(
            AuthRequestRepository requests,
            AppUserRepository users,
            UserTokenRepository tokens,
            AuthMails mails,
            ClaimService claims,
            MailService mailService,
            RateLimiter rateLimiter,
            AuditLog audit,
            TransactionTemplate tx,
            JsonMapper json,
            MeterRegistry meters,
            Clock clock,
            AppProperties properties) {
        this.requests = requests;
        this.users = users;
        this.tokens = tokens;
        this.mails = mails;
        this.claims = claims;
        this.mailService = mailService;
        this.rateLimiter = rateLimiter;
        this.audit = audit;
        this.tx = tx;
        this.json = json;
        this.meters = meters;
        this.clock = clock;
        this.settings = properties.auth();
        this.limits = properties.rateLimits();
        this.keySecret = properties.security().encryptionKey();
    }

    @Scheduled(fixedDelayString = "${app.auth.worker-delay}", initialDelayString = "${app.auth.worker-delay}")
    public void run() {
        for (int i = 0; i < settings.workerBatchSize(); i++) {
            if (!processNext()) {
                return;
            }
        }
    }

    /** Processes one due request; false if none was due. */
    public boolean processNext() {
        Instant now = clock.instant();
        AtomicReference<AuthRequest> current = new AtomicReference<>();
        try {
            Boolean processed = tx.execute(status -> {
                Optional<AuthRequest> next = requests.lockDue(now, 1).stream().findFirst();
                if (next.isEmpty()) {
                    return false;
                }
                current.set(next.get());
                if (retryWindowOver(next.get(), now)) {
                    // A link mail is pointless after the token lifetime (7.11): give up without sending
                    requests.delete(next.get().id());
                    dropped("retry-window-over");
                    return true;
                }
                handle(next.get(), now);
                requests.delete(next.get().id());
                return true;
            });
            return Boolean.TRUE.equals(processed);
        } catch (RuntimeException ex) {
            AuthRequest failed = current.get();
            if (failed == null) {
                throw ex;
            }
            log.warn(
                    "Auth request {} failed ({}), will retry if allowed",
                    failed.id(),
                    ex.getClass().getSimpleName());
            meters.counter("auth.mail.failures").increment();
            scheduleRetry(failed, now);
            return true;
        }
    }

    private void scheduleRetry(AuthRequest failed, Instant now) {
        tx.executeWithoutResult(status -> {
            int attempts = failed.attempts() + 1;
            Instant giveUpAt = failed.createdAt().plus(settings.tokenTtl());
            Instant next = now.plus(settings.mailRetryDelay());
            if (attempts >= settings.mailMaxAttempts() || !next.isBefore(giveUpAt)) {
                requests.delete(failed.id());
                dropped("retries-exhausted");
            } else {
                requests.reschedule(failed.id(), attempts, next);
            }
        });
    }

    private boolean retryWindowOver(AuthRequest request, Instant now) {
        return request.attempts() > 0 && !now.isBefore(request.createdAt().plus(settings.tokenTtl()));
    }

    private void handle(AuthRequest request, Instant now) {
        switch (request.kind()) {
            case MAGIC_LINK -> handleMagicLink(request, now);
            case REGISTER -> handleRegister(request, now);
        }
    }

    /** Case distinction of 7.2 "Auth-Worker". */
    private void handleMagicLink(AuthRequest request, Instant now) {
        Optional<AppUser> user = users.findByEmailForUpdate(request.email());
        if (user.isEmpty()) {
            if (firstAttempt(request)
                    && !rateLimiter.tryAcquire(
                            "no-account-mail", addressKey(request.email()), limits.noAccountMailPerTarget())) {
                dropped("no-account-limit");
                return;
            }
            meters.counter("auth.magic-link.unknown-address").increment();
            mailService.send(mails.noAccount(request.email()));
            return;
        }
        AppUser account = user.get();
        if (ignored(account)) {
            audit.system("MAGIC_LINK_REQUEST_IGNORED", "app_user", account.id(), Map.of("reason", reason(account)));
            return;
        }
        if (!withinMailLimit(request)) {
            return;
        }
        String token = issueToken(account.id(), request.bindingHash(), now);
        audit.system("MAGIC_LINK_REQUESTED", "app_user", account.id(), Map.of());
        mailService.send(mails.loginLinkMail(request.email(), token));
    }

    /** Case distinction of 7.1.4 step 4. */
    private void handleRegister(AuthRequest request, Instant now) {
        Optional<AppUser> user = users.findByEmailForUpdate(request.email());
        if (user.isEmpty()) {
            if (!withinMailLimit(request)) {
                return;
            }
            Optional<UUID> created = users.insertPending(request.email(), displayName(request), now);
            if (created.isPresent()) {
                claims.create(created.get(), child(request), request.bindingHash(), now);
                String token = issueToken(created.get(), request.bindingHash(), now);
                audit.system("USER_REGISTERED", "app_user", created.get(), Map.of());
                send(mails.registrationConfirmation(request.email(), token));
                return;
            }
            // A parallel registration created the account first: continue as "account exists"
            user = users.findByEmailForUpdate(request.email());
            if (user.isEmpty()) {
                return;
            }
        } else if (!withinMailLimit(request)) {
            return;
        }
        AppUser account = user.get();
        if (ignored(account)) {
            audit.system("REGISTRATION_IGNORED", "app_user", account.id(), Map.of("reason", reason(account)));
            return;
        }
        String token = issueToken(account.id(), request.bindingHash(), now);
        if (account.status() == UserStatus.PENDING_VERIFICATION) {
            // The new child data replace the open claim (7.1.4 step 4); for active accounts they are discarded
            claims.create(account.id(), child(request), request.bindingHash(), now);
            send(mails.registrationConfirmation(request.email(), token));
        } else {
            send(mails.alreadyRegistered(request.email(), token));
        }
    }

    /** A new token replaces all older, unused ones; the account row is already locked (7.2). */
    private String issueToken(UUID userId, String bindingHash, Instant now) {
        tokens.deleteOpenTokens(userId);
        String token = Hashing.randomToken(TOKEN_BYTES);
        tokens.insert(userId, Hashing.sha256Hex(token), bindingHash, now.plus(settings.tokenTtl()), now);
        return token;
    }

    private boolean withinMailLimit(AuthRequest request) {
        if (firstAttempt(request)
                && !rateLimiter.tryAcquire("mail-target", addressKey(request.email()), limits.mailPerTarget())) {
            dropped("mail-target-limit");
            return false;
        }
        return true;
    }

    private static boolean firstAttempt(AuthRequest request) {
        return request.attempts() == 0;
    }

    /** Master and disabled accounts never get a link (INV-12, 7.2). */
    private static boolean ignored(AppUser account) {
        return account.isMaster() || account.status() == UserStatus.DISABLED;
    }

    private static String reason(AppUser account) {
        return account.isMaster() ? "MASTER" : "DISABLED";
    }

    /** Rate-limit key for an address: HMAC, never the address itself (9.1). */
    private String addressKey(String email) {
        return Hashing.hmacSha256Hex(keySecret, email);
    }

    private String displayName(AuthRequest request) {
        JsonNode payload = json.readTree(request.payload() == null ? "{}" : request.payload());
        String name = payload.path("displayName").asString("");
        return name.isBlank() ? "Neues Mitglied" : name;
    }

    private ChildData child(AuthRequest request) {
        JsonNode child = json.readTree(request.payload() == null ? "{}" : request.payload())
                .path("child");
        return new ChildData(
                child.path("firstName").asString(""),
                child.path("lastName").asString(""),
                LocalDate.parse(child.path("birthDate").asString()));
    }

    private void send(MailMessage mail) {
        mailService.send(mail);
    }

    private void dropped(String reason) {
        meters.counter("auth.requests.dropped", "reason", reason).increment();
    }
}
