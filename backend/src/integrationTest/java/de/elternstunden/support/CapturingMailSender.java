package de.elternstunden.support;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Mail sender for tests: keeps sent mails in memory and can simulate SMTP failures (AC-MAIL-02). */
public class CapturingMailSender extends JavaMailSenderImpl {

    private static final Pattern TOKEN = Pattern.compile("/login/link#token=([A-Za-z0-9_-]+)");

    public record SentMail(String to, String subject, String text, String html) {

        public String token() {
            Matcher matcher = TOKEN.matcher(text);
            if (!matcher.find()) {
                throw new AssertionError("no login link in mail '" + subject + "'");
            }
            return matcher.group(1);
        }
    }

    private final List<SentMail> sent = new CopyOnWriteArrayList<>();
    private final AtomicInteger failures = new AtomicInteger();

    public List<SentMail> sent() {
        return List.copyOf(sent);
    }

    public List<SentMail> sentTo(String to) {
        return sent.stream().filter(mail -> mail.to().equalsIgnoreCase(to)).toList();
    }

    public void failNext(int count) {
        failures.set(count);
    }

    public void clear() {
        sent.clear();
        failures.set(0);
    }

    @Override
    protected void doSend(MimeMessage[] mimeMessages, Object[] originalMessages) {
        for (MimeMessage message : mimeMessages) {
            if (failures.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new MailSendException("simulated SMTP failure");
            }
            try {
                // The real sender updates the headers when sending; without it the content type is still text/plain
                message.saveChanges();
                String to = ((InternetAddress) message.getAllRecipients()[0]).getAddress();
                List<String> texts = new ArrayList<>();
                List<String> htmls = new ArrayList<>();
                collect(message, texts, htmls);
                sent.add(new SentMail(to, message.getSubject(), String.join("\n", texts), String.join("\n", htmls)));
            } catch (MessagingException | IOException ex) {
                throw new MailSendException("could not read message", ex);
            }
        }
    }

    private static void collect(Part part, List<String> texts, List<String> htmls)
            throws MessagingException, IOException {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart body = multipart.getBodyPart(i);
                collect(body, texts, htmls);
            }
        } else if (content instanceof String text) {
            (part.isMimeType("text/html") ? htmls : texts).add(text);
        }
    }
}
