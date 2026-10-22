package de.elternstunden.auth.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * TOTP after RFC 6238 (HMAC-SHA1, 30 s steps, 6 digits) with ±1 step tolerance (SEC-05). Callers keep the last used
 * step to reject replays; see {@link #matchingStep}.
 */
public final class Totp {

    public static final long STEP_SECONDS = 30;
    private static final int DIGITS = 6;
    private static final int MODULO = 1_000_000;

    private Totp() {}

    /** The step that matches the code within ±1 step of {@code now}, or -1. Compared in constant time. */
    public static long matchingStep(byte[] secret, String code, Instant now) {
        if (code == null || code.length() != DIGITS || !code.chars().allMatch(Character::isDigit)) {
            return -1;
        }
        long current = now.getEpochSecond() / STEP_SECONDS;
        long match = -1;
        for (long step = current - 1; step <= current + 1; step++) {
            if (MessageDigest.isEqual(
                    codeAt(secret, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) {
                match = step;
            }
        }
        return match;
    }

    /** The code for a time step ({@code epochSeconds / 30}). */
    public static String codeAt(byte[] secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(secret, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format(Locale.ROOT, "%06d", binary % MODULO);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA1 not available", ex);
        }
    }

    /** RFC 4648 base32 without padding, as authenticator apps expect it. */
    public static String encodeBase32(byte[] data) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte value : data) {
            buffer = (buffer << 8) | (value & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(alphabet.charAt((buffer >> (bits - 5)) & 0x1F));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(alphabet.charAt((buffer << (5 - bits)) & 0x1F));
        }
        return out.toString();
    }

    /** RFC 4648 base32 (as shown by authenticator apps); spaces, padding and case are ignored. */
    public static byte[] decodeBase32(String value) {
        String clean = value.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < clean.length(); i++) {
            int index = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(clean.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("Invalid base32 character");
            }
            buffer = (buffer << 5) | index;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) (buffer >> (bits - 8)));
                bits -= 8;
            }
        }
        byte[] result = new byte[out.position()];
        out.flip().get(result);
        return result;
    }
}
