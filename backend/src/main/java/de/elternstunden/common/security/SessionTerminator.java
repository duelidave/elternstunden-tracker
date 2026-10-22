package de.elternstunden.common.security;

import java.util.UUID;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Component;

/**
 * Ends all sessions of a user on all devices (7.2 "Sessions folgen dem Kontozustand"). The principal name in
 * Spring Session is the user id ({@link AppUserPrincipal#getName()}), so no personal data is needed.
 */
@Component
public class SessionTerminator {

    private final FindByIndexNameSessionRepository<?> sessions;

    public SessionTerminator(FindByIndexNameSessionRepository<?> sessions) {
        this.sessions = sessions;
    }

    /** Returns the number of ended sessions. */
    public int terminateAll(UUID userId) {
        var ids = sessions.findByPrincipalName(userId.toString()).keySet();
        ids.forEach(sessions::deleteById);
        return ids.size();
    }
}
