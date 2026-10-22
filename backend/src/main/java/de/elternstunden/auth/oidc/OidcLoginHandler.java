package de.elternstunden.auth.oidc;

import de.elternstunden.auth.oidc.OidcLoginService.LoggedIn;
import de.elternstunden.auth.oidc.OidcLoginService.Pending;
import de.elternstunden.auth.oidc.OidcLoginService.Rejected;
import de.elternstunden.auth.service.LoginFlow;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.security.AppUserPrincipal;
import de.elternstunden.common.security.SessionLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * After the provider callback (7.2 "OIDC-Login"): maps the provider account to the own principal, a pending state or
 * a refusal and sends the browser back to the SPA. The provider's authentication object never reaches the session.
 */
@Component
public class OidcLoginHandler implements AuthenticationSuccessHandler, AuthenticationFailureHandler {

    private static final Logger log = LoggerFactory.getLogger(OidcLoginHandler.class);
    static final Duration LINK_INTENT_TTL = Duration.ofMinutes(15);
    static final String FAILED = "OIDC_FAILED";

    private final OidcLoginService oidc;
    private final LoginFlow loginFlow;
    private final SessionLogin sessionLogin;
    private final SecurityContextRepository contextRepository;
    private final String baseUrl;
    private final Clock clock;

    public OidcLoginHandler(
            OidcLoginService oidc,
            LoginFlow loginFlow,
            SessionLogin sessionLogin,
            SecurityContextRepository contextRepository,
            AppProperties properties,
            Clock clock) {
        this.oidc = oidc;
        this.loginFlow = loginFlow;
        this.sessionLogin = sessionLogin;
        this.contextRepository = contextRepository;
        this.baseUrl = properties.baseUrl().toString().replaceAll("/+$", "");
        this.clock = clock;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request, HttpServletResponse response, Authentication authentication)
            throws IOException {
        OAuth2AuthenticationToken token = (OAuth2AuthenticationToken) authentication;
        OidcUser user = (OidcUser) token.getPrincipal();
        OidcIdentity identity = new OidcIdentity(
                token.getAuthorizedClientRegistrationId(),
                user.getIssuer().toString(),
                user.getSubject(),
                user.getEmail(),
                Boolean.TRUE.equals(user.getEmailVerified()),
                user.getFullName(),
                user.getClaimAsString("hd"));

        Optional<LinkIntent> intent = takeLinkIntent(request);
        if (intent.isPresent()) {
            link(intent.get(), identity, request, response);
            return;
        }

        switch (oidc.login(identity)) {
            case LoggedIn(var account) -> {
                LoginFlow.Status status = loginFlow.start(account, request, response);
                redirect(
                        response,
                        switch (status) {
                            case LOGGED_IN -> "/";
                            case MFA_REQUIRED -> "/login/mfa";
                            case MFA_SETUP_REQUIRED -> "/login/mfa?setup=1";
                        });
            }
            case Pending(var pending) -> {
                sessionLogin.pendingOidc(pending, request, response);
                redirect(response, "/register/claim");
            }
            case Rejected(var code) -> redirect(response, "/login?error=" + code);
        }
    }

    /** Manipulated or failed callbacks (state, nonce, signature, iss, aud, exp, PKCE): one neutral answer. */
    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String reason = exception instanceof OAuth2AuthenticationException oauth
                ? oauth.getError().getErrorCode()
                : exception.getClass().getSimpleName();
        log.warn("OIDC login failed: {}", reason);
        boolean linking = takeLinkIntent(request).isPresent();
        redirect(response, (linking ? "/profile?error=" : "/login?error=") + FAILED);
    }

    /** Linking from the profile: the session still holds the own principal, the e-mail rule does not apply. */
    private void link(
            LinkIntent intent, OidcIdentity identity, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Authentication current =
                contextRepository.loadDeferredContext(request).get().getAuthentication();
        Object principal = current == null ? null : current.getPrincipal();
        boolean valid = principal instanceof AppUserPrincipal user
                && user.userId().equals(intent.userId())
                && clock.instant().isBefore(intent.createdAt().plus(LINK_INTENT_TTL));
        if (!valid) {
            redirect(response, "/profile?error=" + FAILED);
            return;
        }
        Optional<String> error = oidc.link(intent.userId(), identity);
        redirect(
                response, error.map(code -> "/profile?error=" + code).orElse("/profile?linked=" + identity.provider()));
    }

    private static Optional<LinkIntent> takeLinkIntent(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(LinkIntent.ATTRIBUTE) instanceof LinkIntent intent)) {
            return Optional.empty();
        }
        session.removeAttribute(LinkIntent.ATTRIBUTE);
        return Optional.of(intent);
    }

    private void redirect(HttpServletResponse response, String path) throws IOException {
        response.sendRedirect(baseUrl + path);
    }
}
