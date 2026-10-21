package de.elternstunden.auth.service;

import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.mail.MailMessage;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * The auth mails (7.11): German, informal, plain text plus HTML, no tracking, no external images. The link appears as
 * a button and as plain URL. The mails never contain names or other text supplied by the requester, because anyone
 * can trigger them for any address (no phishing text in someone else's inbox).
 */
@Component
public class AuthMails {

    private static final String IGNORE = "Du hast diese Mail nicht angefordert? Dann ignoriere sie einfach.";
    private static final String SIGNATURE = "Dein Hort-Team";

    private final URI baseUrl;

    public AuthMails(AppProperties properties) {
        this.baseUrl = properties.baseUrl();
    }

    /** Link of the confirmation page with the token in the fragment (7.2, SEC-17). */
    public String loginLink(String token) {
        return base() + "/login/link#token=" + token;
    }

    public MailMessage registrationConfirmation(String to, String token) {
        return mail(
                to,
                "Bitte bestätige deine Registrierung",
                List.of(
                        "Hallo,",
                        "klicke auf den Link, um deine Registrierung bei den Elternstunden abzuschließen."
                                + " Der Link ist 15 Minuten gültig."),
                "Registrierung abschließen",
                loginLink(token),
                List.of("Ohne Klick löschen wir die Registrierung nach 24 Stunden."
                        + " Einen neuen Link kannst du jederzeit auf der Anmeldeseite anfordern."));
    }

    public MailMessage alreadyRegistered(String to, String token) {
        return mail(
                to,
                "Du hast schon ein Konto",
                List.of(
                        "Hallo,",
                        "für diese Adresse gibt es schon ein Konto. Mit dem Link meldest du dich an."
                                + " Er ist 15 Minuten gültig."),
                "Jetzt anmelden",
                loginLink(token),
                List.of());
    }

    public MailMessage loginLinkMail(String to, String token) {
        return mail(
                to,
                "Dein Anmeldelink",
                List.of(
                        "Hallo,",
                        "mit diesem Link meldest du dich an. Er ist 15 Minuten gültig und nur einmal nutzbar."),
                "Jetzt anmelden",
                loginLink(token),
                List.of());
    }

    public MailMessage noAccount(String to) {
        return mail(
                to,
                "Kein Konto mit dieser Adresse",
                List.of(
                        "Hallo,",
                        "jemand wollte sich mit dieser Adresse anmelden, aber dafür gibt es noch kein Konto."
                                + " Hier kannst du dich registrieren:"),
                "Registrieren",
                base() + "/register",
                List.of());
    }

    public MailMessage masterLoginAlarm(String to, int failures) {
        return mail(
                to,
                "Fehlgeschlagene Master-Anmeldungen",
                List.of(
                        "Hallo,",
                        "in der letzten Stunde gab es mindestens " + failures
                                + " fehlgeschlagene Anmeldeversuche für den Master-Zugang."
                                + " Das Konto ist nicht gesperrt. Prüfe bitte die Logs auf dem Server."),
                "Zur Anmeldung",
                base() + "/login/master",
                List.of());
    }

    private String base() {
        String value = baseUrl.toString();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static MailMessage mail(
            String to, String subject, List<String> intro, String button, String link, List<String> outro) {
        StringBuilder text = new StringBuilder();
        intro.forEach(line -> text.append(line).append("\n\n"));
        text.append(link).append("\n\n");
        outro.forEach(line -> text.append(line).append("\n\n"));
        text.append(IGNORE).append("\n\n").append(SIGNATURE).append('\n');

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"de\"><body style=\"font-family:system-ui,sans-serif;")
                .append("color:#2e2e2e;line-height:1.5;max-width:36rem;margin:0 auto;padding:1rem\">");
        intro.forEach(line -> html.append("<p>").append(escape(line)).append("</p>"));
        html.append("<p><a href=\"")
                .append(escape(link))
                .append("\" style=\"display:inline-block;background:#f5c400;color:#2e2e2e;padding:0.75rem 1.25rem;")
                .append("border-radius:0.5rem;text-decoration:none;font-weight:600\">")
                .append(escape(button))
                .append("</a></p>")
                .append("<p style=\"word-break:break-all;color:#6e6e6e\">")
                .append(escape(link))
                .append("</p>");
        outro.forEach(line -> html.append("<p>").append(escape(line)).append("</p>"));
        html.append("<p style=\"color:#6e6e6e\">")
                .append(escape(IGNORE))
                .append("</p><p>")
                .append(escape(SIGNATURE))
                .append("</p></body></html>");
        return new MailMessage(to, subject, text.toString(), html.toString());
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
