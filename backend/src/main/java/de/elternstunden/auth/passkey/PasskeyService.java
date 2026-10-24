package de.elternstunden.auth.passkey;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.auth.service.LoginFlow;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.ratelimit.RateLimiter;
import de.elternstunden.common.security.AppUserPrincipal;
import de.elternstunden.common.web.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Passkeys (7.2, D-30): creating them in the profile with a fresh login, signing in without an e-mail, listing and
 * removing own passkeys. A passkey login ends like the magic link: admins with TOTP get only MFA_PENDING. Every failed
 * login answers the same neutral 401, without telling why.
 */
@Service
public class PasskeyService {

    static final int MAX_PASSKEYS = 10;
    static final String DEFAULT_LABEL = "Passkey";

    /** A passkey for the profile; {@code id} is the credential id (Base64url). */
    public record Passkey(
            String id,
            String label,
            @Nullable Instant createdAt,
            @Nullable Instant lastUsedAt) {}

    /** Options for the browser and the id to answer with. */
    public record Challenge(UUID challengeId, Map<String, Object> options) {}

    private final WebAuthnRelyingPartyOperations relyingParty;
    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final UserCredentialRepository credentials;
    private final PasskeyChallenges challenges;
    private final PasskeyJson json;
    private final AppUserRepository users;
    private final LoginFlow loginFlow;
    private final RateLimiter rateLimiter;
    private final AppProperties properties;
    private final AuditLog audit;
    private final Clock clock;

    public PasskeyService(
            WebAuthnRelyingPartyOperations relyingParty,
            PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository credentials,
            PasskeyChallenges challenges,
            PasskeyJson json,
            AppUserRepository users,
            LoginFlow loginFlow,
            RateLimiter rateLimiter,
            AppProperties properties,
            AuditLog audit,
            Clock clock) {
        this.relyingParty = relyingParty;
        this.userEntities = userEntities;
        this.credentials = credentials;
        this.challenges = challenges;
        this.json = json;
        this.users = users;
        this.loginFlow = loginFlow;
        this.rateLimiter = rateLimiter;
        this.properties = properties;
        this.audit = audit;
        this.clock = clock;
    }

    /** The WebAuthn user handle of an account: its id as 16 bytes, so a changed e-mail does not matter. */
    public static Bytes userEntityId(UUID userId) {
        return new Bytes(ByteBuffer.allocate(16)
                .putLong(userId.getMostSignificantBits())
                .putLong(userId.getLeastSignificantBits())
                .array());
    }

    static @Nullable UUID userId(Bytes userEntityId) {
        byte[] bytes = userEntityId.getBytes();
        if (bytes.length != 16) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /** Newest first. */
    public List<Passkey> list(UUID userId) {
        return credentials.findByUserId(userEntityId(userId)).stream()
                .sorted(Comparator.comparing(
                        CredentialRecord::getCreated, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(PasskeyService::passkey)
                .toList();
    }

    /** Only with a fresh login, never for the master (INV-12), at most {@value #MAX_PASSKEYS} per account. */
    @Transactional
    public Challenge startRegistration(AppUserPrincipal principal) {
        if (principal.role() == UserRole.MASTER_ADMIN) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (clock.instant()
                .isAfter(principal.authenticatedAt().plus(properties.oidc().reauthWindow()))) {
            throw new ApiException(ErrorCode.REAUTH_REQUIRED);
        }
        AppUser user = users.findById(principal.userId()).orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        Bytes entityId = userEntityId(user.id());
        requireRoomFor(entityId);
        // Our own user handle; the e-mail is what the passkey manager shows (7.2 "Speicherung")
        userEntities.save(ImmutablePublicKeyCredentialUserEntity.builder()
                .id(entityId)
                .name(user.email())
                .displayName(user.displayName())
                .build());
        PublicKeyCredentialCreationOptions options = relyingParty.createPublicKeyCredentialCreationOptions(
                new ImmutablePublicKeyCredentialCreationOptionsRequest(
                        UsernamePasswordAuthenticationToken.authenticated(user.email(), null, List.of())));
        return new Challenge(challenges.addRegistration(user.id(), options), json.toMap(options));
    }

    @Transactional
    public Passkey register(UUID userId, UUID challengeId, Map<String, Object> credential, @Nullable String label) {
        PublicKeyCredentialCreationOptions options = challenges
                .takeRegistration(challengeId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.PASSKEY_INVALID));
        requireRoomFor(userEntityId(userId));
        String name = label == null || label.isBlank() ? DEFAULT_LABEL : label.strip();
        CredentialRecord record;
        try {
            record = relyingParty.registerCredential(new ImmutableRelyingPartyRegistrationRequest(
                    options, new RelyingPartyPublicKey(json.attestation(credential), name)));
        } catch (RuntimeException ex) {
            // Wrong origin, challenge or signature, unreadable data: no details to the client
            throw new ApiException(ErrorCode.PASSKEY_INVALID);
        }
        audit.user(userId, "PASSKEY_ADDED", "app_user", userId);
        return passkey(record);
    }

    /** Own passkeys only (SEC-08); the magic link stays (INV-11). */
    @Transactional
    public void delete(UUID userId, String passkeyId) {
        Bytes id;
        try {
            id = Bytes.fromBase64(passkeyId);
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        CredentialRecord record = credentials.findByCredentialId(id);
        if (record == null || !record.getUserEntityUserId().equals(userEntityId(userId))) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        credentials.delete(id);
        audit.user(userId, "PASSKEY_REMOVED", "app_user", userId);
    }

    /** Options without allowCredentials: the browser offers the discoverable passkeys of this site. */
    public Challenge startLogin(HttpServletRequest request) {
        rateLimiter.acquireOrThrow(
                "passkey-ip",
                ClientIp.rateLimitKey(request),
                properties.rateLimits().passkeyPerIp());
        PublicKeyCredentialRequestOptions options = relyingParty.createCredentialRequestOptions(
                new ImmutablePublicKeyCredentialRequestOptionsRequest(null));
        return new Challenge(challenges.addLogin(options), json.toMap(options));
    }

    @Transactional
    public LoginFlow.Status login(
            UUID challengeId,
            Map<String, Object> credential,
            HttpServletRequest request,
            HttpServletResponse response) {
        rateLimiter.acquireOrThrow(
                "passkey-ip",
                ClientIp.rateLimitKey(request),
                properties.rateLimits().passkeyPerIp());
        PublicKeyCredentialRequestOptions options =
                challenges.takeLogin(challengeId).orElseThrow(this::failed);
        PublicKeyCredentialUserEntity entity;
        try {
            entity = relyingParty.authenticate(
                    new RelyingPartyAuthenticationRequest(options, json.assertion(credential)));
        } catch (RuntimeException ex) {
            throw failed();
        }
        UUID userId = userId(entity.getId());
        AppUser user = userId == null ? null : users.findById(userId).orElse(null);
        // INV-12 and disabled accounts: the same answer as a wrong signature
        if (user == null || user.status() != UserStatus.ACTIVE || user.role() == UserRole.MASTER_ADMIN) {
            throw failed();
        }
        users.touchLogin(user.id(), clock.instant());
        audit.user(user.id(), "PASSKEY_LOGIN", "app_user", user.id());
        return loginFlow.start(user, request, response);
    }

    private ApiException failed() {
        audit.system("PASSKEY_LOGIN_FAILED", "app_user", null, Map.of());
        return new ApiException(ErrorCode.LOGIN_FAILED);
    }

    private void requireRoomFor(Bytes entityId) {
        if (credentials.findByUserId(entityId).size() >= MAX_PASSKEYS) {
            throw new ApiException(ErrorCode.PASSKEY_LIMIT);
        }
    }

    private static Passkey passkey(CredentialRecord record) {
        return new Passkey(
                record.getCredentialId().toBase64UrlString(),
                record.getLabel(),
                record.getCreated(),
                record.getLastUsed());
    }
}
