package de.elternstunden.common.mail;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Mails outside the auth outbox, e.g. "Neue Zuordnung" (7.11): sent asynchronously after the transaction committed,
 * with up to 3 retries. A failure is logged without the address and never changes the answer to the client.
 */
@Component
public class AsyncMailer {

    private static final Logger log = LoggerFactory.getLogger(AsyncMailer.class);
    static final List<Duration> RETRY_DELAYS =
            List.of(Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    private final MailService mailService;
    private final TaskScheduler scheduler;

    public AsyncMailer(MailService mailService, TaskScheduler scheduler) {
        this.mailService = mailService;
        this.scheduler = scheduler;
    }

    /** Sends after the current transaction commits; nothing is sent if it rolls back. */
    public void sendAfterCommit(MailMessage mail) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    schedule(mail, 0);
                }
            });
        } else {
            schedule(mail, 0);
        }
    }

    // send() handles its own failures, so there is nothing to learn from the future
    @SuppressWarnings("FutureReturnValueIgnored")
    private void schedule(MailMessage mail, int attempt) {
        Duration delay = attempt == 0 ? Duration.ZERO : RETRY_DELAYS.get(attempt - 1);
        // Real time on purpose: retry timing is infrastructure, not business time (0.4 rule 7)
        scheduler.schedule(() -> send(mail, attempt), Instant.now().plus(delay));
    }

    private void send(MailMessage mail, int attempt) {
        try {
            mailService.send(mail);
        } catch (MailException ex) {
            if (attempt < RETRY_DELAYS.size()) {
                log.warn(
                        "Notification mail failed ({}), retry {}", ex.getClass().getSimpleName(), attempt + 1);
                schedule(mail, attempt + 1);
            } else {
                log.error(
                        "Notification mail failed ({}), giving up",
                        ex.getClass().getSimpleName());
            }
        }
    }
}
