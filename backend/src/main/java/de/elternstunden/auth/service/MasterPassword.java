package de.elternstunden.auth.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;

/**
 * Argon2id for the master password, the only password in the system (D-21, D-24, SEC-03). Minimum parameters after
 * OWASP: m = 19456 KiB, t = 2, p = 1. Weaker hashes from the configuration stop the start in production (7.9).
 */
public final class MasterPassword {

    static final int MIN_MEMORY_KIB = 19_456;
    static final int MIN_ITERATIONS = 2;
    static final int MIN_PARALLELISM = 1;

    private static final Pattern PARAMETERS = Pattern.compile("^\\$argon2id\\$v=19\\$m=(\\d+),t=(\\d+),p=(\\d+)\\$.+");

    private MasterPassword() {}

    public static Argon2PasswordEncoder encoder() {
        return new Argon2PasswordEncoder(16, 32, MIN_PARALLELISM, MIN_MEMORY_KIB, MIN_ITERATIONS);
    }

    /** True if the hash is Argon2id with at least the minimum parameters. */
    public static boolean meetsMinimum(String hash) {
        Matcher matcher = PARAMETERS.matcher(hash);
        return matcher.matches()
                && Integer.parseInt(matcher.group(1)) >= MIN_MEMORY_KIB
                && Integer.parseInt(matcher.group(2)) >= MIN_ITERATIONS
                && Integer.parseInt(matcher.group(3)) >= MIN_PARALLELISM;
    }
}
