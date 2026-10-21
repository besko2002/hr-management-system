package com.example.hr.employee;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generates the one-time password handed to a newly created employee. Every password
 * contains at least one lower-case letter, one upper-case letter, one digit and one
 * symbol, so it always satisfies the change-password rules too.
 */
@Component
public class TemporaryPasswordGenerator {

    static final int LENGTH = 14;

    private static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%*?-_";
    private static final String ALL = LOWER + UPPER + DIGITS + SYMBOLS;

    private final SecureRandom random = new SecureRandom();

    public String generate() {
        List<Character> chars = new ArrayList<>(LENGTH);
        chars.add(pick(LOWER));
        chars.add(pick(UPPER));
        chars.add(pick(DIGITS));
        chars.add(pick(SYMBOLS));
        while (chars.size() < LENGTH) {
            chars.add(pick(ALL));
        }
        Collections.shuffle(chars, random);
        StringBuilder password = new StringBuilder(LENGTH);
        chars.forEach(password::append);
        return password.toString();
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }
}
