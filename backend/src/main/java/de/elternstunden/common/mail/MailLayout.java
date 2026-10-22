package de.elternstunden.common.mail;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.web.util.HtmlUtils;

/**
 * The shared layout of all transactional mails (7.11): German, plain text plus HTML, no tracking, no external images.
 * A link appears as a button and as plain URL, and every mail with a link carries the "not requested" note.
 */
public final class MailLayout {

    static final String IGNORE = "Du hast diese Mail nicht angefordert? Dann ignoriere sie einfach.";
    static final String SIGNATURE = "Dein Hort-Team";

    /** A call to action: button text and absolute link. */
    public record Action(String button, String link) {}

    private MailLayout() {}

    public static MailMessage mail(
            String to, String subject, List<String> intro, @Nullable Action action, List<String> outro) {
        StringBuilder text = new StringBuilder();
        intro.forEach(line -> text.append(line).append("\n\n"));
        if (action != null) {
            text.append(action.link()).append("\n\n");
        }
        outro.forEach(line -> text.append(line).append("\n\n"));
        if (action != null) {
            text.append(IGNORE).append("\n\n");
        }
        text.append(SIGNATURE).append('\n');

        StringBuilder html = new StringBuilder();
        html.append("<!doctype html><html lang=\"de\"><body style=\"font-family:system-ui,sans-serif;")
                .append("color:#333333;line-height:1.5;max-width:36rem;margin:0 auto;padding:1rem\">");
        intro.forEach(line -> html.append("<p>").append(escape(line)).append("</p>"));
        if (action != null) {
            html.append("<p><a href=\"")
                    .append(escape(action.link()))
                    .append("\" style=\"display:inline-block;background:#f28d13;color:#333333;")
                    .append("padding:0.75rem 1.25rem;border-radius:0.5rem;text-decoration:none;font-weight:600\">")
                    .append(escape(action.button()))
                    .append("</a></p>")
                    .append("<p style=\"word-break:break-all;color:#666666\">")
                    .append(escape(action.link()))
                    .append("</p>");
        }
        outro.forEach(line -> html.append("<p>").append(escape(line)).append("</p>"));
        if (action != null) {
            html.append("<p style=\"color:#666666\">").append(escape(IGNORE)).append("</p>");
        }
        html.append("<p>").append(escape(SIGNATURE)).append("</p></body></html>");
        return new MailMessage(to, subject, text.toString(), html.toString());
    }

    private static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
