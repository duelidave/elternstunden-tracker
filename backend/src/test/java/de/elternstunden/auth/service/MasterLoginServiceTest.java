package de.elternstunden.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.elternstunden.audit.AuditLog;
import de.elternstunden.auth.domain.AppUser;
import de.elternstunden.auth.domain.UserRole;
import de.elternstunden.auth.domain.UserStatus;
import de.elternstunden.auth.repo.AppUserRepository;
import de.elternstunden.common.config.AppProperties;
import de.elternstunden.common.config.TestProperties;
import de.elternstunden.common.error.ApiException;
import de.elternstunden.common.error.ErrorCode;
import de.elternstunden.common.mail.MailMessage;
import de.elternstunden.common.mail.MailService;
import de.elternstunden.common.ratelimit.RateLimiter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** Master login rules that are easier to see without HTTP (7.2, AC-MASTER-04, AC-MASTER-06). */
class MasterLoginServiceTest {

    private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";
    private static final Instant NOW = Instant.parse("2026-10-21T10:00:00Z");

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final MailService mailService = mock(MailService.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void manyFailuresSendOneAlarmButNeverLockTheAccount() {
        MasterLoginService service = service(MasterPassword.encoder().encode("right"));
        AppUser master = master();
        when(users.findMaster()).thenReturn(Optional.of(master));
        for (int i = 0; i < 12; i++) {
            String ip = "ip-" + i;
            assertThatThrownBy(() -> service.login("master@example.test", "wrong", code(), ip))
                    .isInstanceOfSatisfying(
                            ApiException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.LOGIN_FAILED));
        }

        ArgumentCaptor<MailMessage> alarm = ArgumentCaptor.forClass(MailMessage.class);
        verify(mailService, times(1)).send(alarm.capture());
        assertThat(alarm.getValue().to()).isEqualTo("master@example.test");
        assertThat(alarm.getValue().subject()).isEqualTo("Fehlgeschlagene Master-Anmeldungen");
        assertThat(service.login("master@example.test", "right", code(), "other-ip")
                        .id())
                .isEqualTo(master.id());
    }

    /** AC-MASTER-04: the configured hash counts; after a change the old password no longer works. */
    @Test
    void theConfiguredHashIsTheSourceOfTruth() {
        when(users.findMaster()).thenReturn(Optional.of(master()));
        MasterLoginService changed = service(MasterPassword.encoder().encode("new-password"));

        assertThatThrownBy(() -> changed.login("master@example.test", "old-password", code(), "ip"))
                .isInstanceOf(ApiException.class);
        assertThat(changed.login("master@example.test", "new-password", code(), "ip2")
                        .role())
                .isEqualTo(UserRole.MASTER_ADMIN);
        verify(mailService, never()).send(any());
    }

    private MasterLoginService service(String hash) {
        AppProperties properties =
                TestProperties.withMaster(new AppProperties.Master("master@example.test", hash, SECRET, "Master"));
        return new MasterLoginService(
                properties,
                users,
                new RateLimiter(clock),
                mock(AuditLog.class),
                mailService,
                new AuthMails(properties),
                clock);
    }

    private static String code() {
        return Totp.codeAt(Totp.decodeBase32(SECRET), NOW.getEpochSecond() / Totp.STEP_SECONDS);
    }

    private static AppUser master() {
        return new AppUser(
                UUID.randomUUID(),
                "master@example.test",
                "Master",
                UserRole.MASTER_ADMIN,
                UserStatus.ACTIVE,
                null,
                true);
    }
}
