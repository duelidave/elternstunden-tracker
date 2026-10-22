package de.elternstunden.auth.oidc;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/** Provider tokens are never stored, neither in the database nor in the session (SEC-06, AC-OIDC-08). */
public class NoOpAuthorizedClientRepository implements OAuth2AuthorizedClientRepository {

    @Override
    @SuppressWarnings("TypeParameterUnusedInFormals") // the signature of the Spring interface
    public <T extends OAuth2AuthorizedClient> @Nullable T loadAuthorizedClient(
            String clientRegistrationId, @Nullable Authentication principal, HttpServletRequest request) {
        return null;
    }

    @Override
    public void saveAuthorizedClient(
            OAuth2AuthorizedClient authorizedClient,
            Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // intentionally empty
    }

    @Override
    public void removeAuthorizedClient(
            String clientRegistrationId,
            @Nullable Authentication principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        // intentionally empty
    }
}
