package com.footballmanager.domain.service;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class PasswordContractTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void usesUtf8BytesForTheMaximumBoundary() {
        assertThat(PasswordContract.utf8ByteLength("a".repeat(72))).isEqualTo(72);
        assertThat(PasswordContract.utf8ByteLength("a".repeat(73))).isEqualTo(73);
        assertThat(PasswordContract.utf8ByteLength("á".repeat(36))).isEqualTo(72);
        assertThat(PasswordContract.utf8ByteLength("😀".repeat(18))).isEqualTo(72);
    }

    @Test
    void rejectsInputThatExceedsTheBcryptByteBoundary() {
        assertThat(PasswordContract.isRegistrationPasswordValid("a".repeat(72))).isTrue();
        assertThat(PasswordContract.isRegistrationPasswordValid("a".repeat(73))).isFalse();
        assertThat(PasswordContract.isRegistrationPasswordValid("á".repeat(37))).isFalse();
        assertThat(PasswordContract.isRegistrationPasswordValid("😀".repeat(19))).isFalse();
    }

    @Test
    void preservesMinimumAndWhitespaceRules() {
        assertThat(PasswordContract.isRegistrationPasswordValid("abcdefgh")).isTrue();
        assertThat(PasswordContract.isRegistrationPasswordValid("short")).isFalse();
        assertThat(PasswordContract.isRegistrationPasswordValid("        ")).isFalse();
        assertThat(PasswordContract.isRegistrationPasswordValid("  abcdefgh  ")).isTrue();
    }

    @Test
    void everyAcceptedRegistrationCandidateIsHashableByTheProductiveEncoder() {
        String[] accepted = {
            "abcdefgh",
            "Passw0rd!",
            "a".repeat(72),
            "á".repeat(36),
            "😀".repeat(18)
        };

        for (String password : accepted) {
            assertThat(PasswordContract.isRegistrationPasswordValid(password)).isTrue();
            assertThatCode(() -> encoder.encode(password)).doesNotThrowAnyException();
        }
    }

    @Test
    void existingBcryptHashesRemainCompatible() {
        String existingHash = "$2a$04$012345678901234567890uMvmubphsJ8Q1qwTFNdkcxxmiX0fmA0W";

        assertThat(encoder.matches("password", existingHash)).isTrue();
    }
}
