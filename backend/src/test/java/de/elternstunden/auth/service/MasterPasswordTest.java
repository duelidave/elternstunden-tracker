package de.elternstunden.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MasterPasswordTest {

    @Test
    void encoderMeetsTheMinimumAndVerifies() {
        String hash = MasterPassword.encoder().encode("correct horse battery staple");

        assertThat(hash).startsWith("$argon2id$v=19$m=19456,t=2,p=1$");
        assertThat(MasterPassword.meetsMinimum(hash)).isTrue();
        assertThat(MasterPassword.encoder().matches("correct horse battery staple", hash))
                .isTrue();
        assertThat(MasterPassword.encoder().matches("wrong", hash)).isFalse();
    }

    @Test
    void weakerOrOtherHashesDoNotMeetTheMinimum() {
        assertThat(MasterPassword.meetsMinimum("$argon2id$v=19$m=16384,t=2,p=1$c2FsdA$aGFzaA"))
                .isFalse();
        assertThat(MasterPassword.meetsMinimum("$argon2id$v=19$m=19456,t=1,p=1$c2FsdA$aGFzaA"))
                .isFalse();
        assertThat(MasterPassword.meetsMinimum("$argon2i$v=19$m=65536,t=3,p=1$c2FsdA$aGFzaA"))
                .isFalse();
        assertThat(MasterPassword.meetsMinimum("$2a$10$abcdefghijklmnopqrstuv")).isFalse();
        assertThat(MasterPassword.meetsMinimum("$argon2id$v=19$m=65536,t=3,p=4$c2FsdA$aGFzaA"))
                .isTrue();
    }
}
