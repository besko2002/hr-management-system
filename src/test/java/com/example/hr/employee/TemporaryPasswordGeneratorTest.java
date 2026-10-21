package com.example.hr.employee;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests for the one-time password generator. */
class TemporaryPasswordGeneratorTest {

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @Test
    void generatesPasswordsOfTheConfiguredLength() {
        assertThat(generator.generate()).hasSize(TemporaryPasswordGenerator.LENGTH);
    }

    @Test
    void alwaysSatisfiesTheMinimumLengthOfTheChangePasswordRule() {
        IntStream.range(0, 50).forEach(i -> assertThat(generator.generate().length()).isGreaterThanOrEqualTo(8));
    }

    @Test
    void containsLowerUpperDigitAndSymbol() {
        for (int i = 0; i < 50; i++) {
            String password = generator.generate();
            assertThat(password).matches(".*[a-z].*").matches(".*[A-Z].*").matches(".*[0-9].*");
            assertThat(password.chars().anyMatch(c -> "!@#$%*?-_".indexOf(c) >= 0))
                    .as("password %s contains a symbol", password).isTrue();
        }
    }

    @Test
    void doesNotUseEasilyConfusedCharacters() {
        for (int i = 0; i < 50; i++) {
            assertThat(generator.generate()).doesNotContain("l").doesNotContain("I")
                    .doesNotContain("O").doesNotContain("0").doesNotContain("1");
        }
    }

    @Test
    void producesDistinctPasswords() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            seen.add(generator.generate());
        }
        assertThat(seen).hasSize(200);
    }
}
