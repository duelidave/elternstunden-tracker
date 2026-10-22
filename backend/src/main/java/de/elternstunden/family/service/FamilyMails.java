package de.elternstunden.family.service;

import de.elternstunden.common.mail.MailLayout;
import de.elternstunden.common.mail.MailMessage;
import java.util.List;
import org.springframework.stereotype.Component;

/** Mail "Neue Zuordnung" (7.1.6, 7.11). */
@Component
public class FamilyMails {

    public MailMessage newMember(String to, String newMemberName) {
        return MailLayout.mail(
                to,
                "Neues Mitglied in deiner Familie",
                List.of(
                        "Hallo,",
                        newMemberName + " wurde deiner Familie zugeordnet."
                                + " Falls das nicht stimmt, wende dich bitte an das Hort-Team."),
                null,
                List.of());
    }
}
