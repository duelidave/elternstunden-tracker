package de.elternstunden.auth.service;

import de.elternstunden.auth.passkey.PasskeyService;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Everything an account can sign in with, owned by the auth module: link tokens, provider identities, recovery codes
 * and passkeys. Anonymizing (DP-05) removes it here, so a new sign-in way cannot be forgotten elsewhere.
 */
@Component
public class AccountCredentials {

    private final JdbcClient jdbc;

    public AccountCredentials(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Joins the caller's transaction. */
    public void deleteAll(UUID userId) {
        jdbc.sql("DELETE FROM user_token WHERE user_id = :id")
                .param("id", userId)
                .update();
        jdbc.sql("DELETE FROM user_identity WHERE user_id = :id")
                .param("id", userId)
                .update();
        jdbc.sql("DELETE FROM user_recovery_code WHERE user_id = :id")
                .param("id", userId)
                .update();
        // Passkeys go with their user entity (cascade)
        jdbc.sql("DELETE FROM user_entities WHERE id = :entityId")
                .param("entityId", PasskeyService.userEntityId(userId).toBase64UrlString())
                .update();
    }
}
