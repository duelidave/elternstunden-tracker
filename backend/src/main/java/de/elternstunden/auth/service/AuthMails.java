package de.elternstunden.auth.service;

import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.mail.MailLayout;
import de.elternstunden.common.mail.MailMessage;
import java.net.URI;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * The auth mails (7.11): German, informal, plain text plus HTML, no tracking, no external images. The link appears as
 * a button and as plain URL. The mails never contain names or other text supplied by the requester, because anyone
 * can trigger them for any address (no phishing text in someone else's inbox).
 */
@Component
public class AuthMails {

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

    public MailMessage globalLimitAlarm(String to, String limit) {
        return mail(
                to,
                "Globales Limit erreicht",
                List.of(
                        "Hallo,",
                        "das globale Limit \"" + limit
                                + "\" ist erreicht. Bis zum Ende der Stunde lehnt die App weitere"
                                + " Anfragen dieser Art für alle ab. Das kann ein Angriff oder ein fehlerhafter"
                                + " Client sein. Prüfe bitte die Logs auf dem Server."),
                "Zur Anmeldung",
                base() + "/login/master",
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

    public MailMessage oidcMasterAlarm(String to) {
        return mail(
                to,
                "Anmeldeversuch über einen Anbieter",
                List.of(
                        "Hallo,",
                        "für den Master-Zugang gab es einen Anmeldeversuch über einen OIDC-Anbieter."
                                + " Das darf nicht vorkommen und wurde abgelehnt. Prüfe bitte die Logs auf dem Server."),
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
        return MailLayout.mail(to, subject, intro, new MailLayout.Action(button, link), outro);
    }
}
