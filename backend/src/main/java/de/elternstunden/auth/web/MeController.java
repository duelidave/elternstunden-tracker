package de.elternstunden.auth.web;

import de.elternstunden.api.MeApi;
import de.elternstunden.api.model.ChildClaimDto;
import de.elternstunden.api.model.IdentityLinkStartDto;
import de.elternstunden.api.model.MeDto;
import de.elternstunden.api.model.MeUpdateDto;
import de.elternstunden.api.model.MfaVerificationDto;
import de.elternstunden.api.model.PasskeyChallengeDto;
import de.elternstunden.api.model.PasskeyDto;
import de.elternstunden.api.model.PasskeyRegistrationDto;
import de.elternstunden.api.model.TotpConfirmationDto;
import de.elternstunden.api.model.TotpSetupDto;
import de.elternstunden.auth.passkey.PasskeyService;
import de.elternstunden.auth.service.AdminTotpService;
import de.elternstunden.auth.service.AuthService;
import de.elternstunden.auth.service.MeService;
import de.elternstunden.common.security.CurrentUser;
import de.elternstunden.common.web.CurrentRequest;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in account: profile, providers, admin TOTP and, for unassigned members, their child data. */
@RestController
public class MeController implements MeApi {

    private final MeService me;
    private final AuthService auth;
    private final PasskeyService passkeys;

    public MeController(MeService me, AuthService auth, PasskeyService passkeys) {
        this.me = me;
        this.auth = auth;
        this.passkeys = passkeys;
    }

    @Override
    public ResponseEntity<List<PasskeyDto>> listPasskeys() {
        return ResponseEntity.ok(passkeys.list(CurrentUser.principal().userId()).stream()
                .map(MeController::passkey)
                .toList());
    }

    @Override
    public ResponseEntity<PasskeyChallengeDto> startPasskeyRegistration() {
        PasskeyService.Challenge challenge = passkeys.startRegistration(CurrentUser.principal());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new PasskeyChallengeDto(challenge.challengeId(), challenge.options()));
    }

    @Override
    public ResponseEntity<PasskeyDto> registerPasskey(PasskeyRegistrationDto registration) {
        PasskeyService.Passkey created = passkeys.register(
                CurrentUser.principal().userId(),
                registration.getChallengeId(),
                registration.getCredential(),
                registration.getLabel());
        return ResponseEntity.status(HttpStatus.CREATED).body(passkey(created));
    }

    @Override
    public ResponseEntity<Void> deletePasskey(String passkeyId) {
        passkeys.delete(CurrentUser.principal().userId(), passkeyId);
        return ResponseEntity.noContent().build();
    }

    private static PasskeyDto passkey(PasskeyService.Passkey passkey) {
        PasskeyDto dto = new PasskeyDto(
                passkey.id(),
                passkey.label(),
                OffsetDateTime.ofInstant(
                        passkey.createdAt() == null ? Instant.EPOCH : passkey.createdAt(), ZoneOffset.UTC));
        dto.setLastUsedAt(
                passkey.lastUsedAt() == null ? null : OffsetDateTime.ofInstant(passkey.lastUsedAt(), ZoneOffset.UTC));
        return dto;
    }

    @Override
    public ResponseEntity<MeDto> getMe() {
        return ResponseEntity.ok(me.current());
    }

    @Override
    public ResponseEntity<MeDto> updateMe(MeUpdateDto body) {
        return ResponseEntity.ok(me.updateDisplayName(body.getDisplayName()));
    }

    @Override
    public ResponseEntity<MeDto> updateMyClaim(ChildClaimDto childClaimDto) {
        return ResponseEntity.ok(me.updateClaim(childClaimDto));
    }

    @Override
    public ResponseEntity<IdentityLinkStartDto> startIdentityLink(String provider) {
        return ResponseEntity.ok(new IdentityLinkStartDto(me.startLink(provider, CurrentRequest.request())));
    }

    @Override
    public ResponseEntity<Void> deleteIdentity(UUID identityId) {
        me.deleteIdentity(identityId);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<TotpSetupDto> setupTotp() {
        AdminTotpService.Setup setup = me.setupTotp();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TotpSetupDto(setup.secret(), setup.otpauthUri()));
    }

    @Override
    public ResponseEntity<TotpConfirmationDto> confirmTotp(MfaVerificationDto body) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new TotpConfirmationDto(
                        auth.confirmTotp(body.getCode(), CurrentRequest.request(), CurrentRequest.response())));
    }

    @Override
    public ResponseEntity<Void> disableTotp() {
        me.disableTotp();
        return ResponseEntity.noContent().build();
    }
}
