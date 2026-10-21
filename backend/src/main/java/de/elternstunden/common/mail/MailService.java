package de.elternstunden.common.mail;

import de.elternstunden.common.config.AppProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailException;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends mails over SMTP (7.11). Synchronous on purpose: callers decide about retries (the auth worker retries
 * persistently). Failures surface as {@link MailException}; the address is never logged (SEC-12).
 */
@Service
public class MailService {

    private final JavaMailSender sender;
    private final String from;

    public MailService(JavaMailSender sender, AppProperties properties) {
        this.sender = sender;
        this.from = properties.mail().from();
    }

    public void send(MailMessage mail) {
        MimeMessage message = sender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(mail.to());
            helper.setSubject(mail.subject());
            helper.setText(mail.text(), mail.html());
        } catch (MessagingException ex) {
            throw new MailPreparationException("Could not build mail", ex);
        }
        sender.send(message);
    }
}
