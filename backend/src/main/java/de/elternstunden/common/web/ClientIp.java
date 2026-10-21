package de.elternstunden.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Client address for rate limits (9.1). It comes from {@code getRemoteAddr()}: behind Caddy the container
 * configuration makes that the proxied client address (M7), client-sent {@code X-Forwarded-For} headers are never
 * read here. IPv6 is keyed by its /64 prefix, because a single client usually controls a whole /64.
 */
public final class ClientIp {

    private ClientIp() {}

    public static String rateLimitKey(HttpServletRequest request) {
        return prefix(request.getRemoteAddr(), 32, 64);
    }

    /** Shortened form for logs (SEC-12): IPv4 /24, IPv6 /48. */
    public static String forLogs(HttpServletRequest request) {
        return prefix(request.getRemoteAddr(), 24, 48);
    }

    static String prefix(String address, int ipv4Bits, int ipv6Bits) {
        try {
            byte[] bytes = InetAddress.ofLiteral(address).getAddress();
            int bits = bytes.length == 4 ? ipv4Bits : ipv6Bits;
            for (int i = 0; i < bytes.length; i++) {
                int keep = Math.max(0, Math.min(8, bits - i * 8));
                bytes[i] = (byte) (bytes[i] & (0xFF << (8 - keep)));
            }
            return InetAddress.getByAddress(bytes).getHostAddress() + "/" + bits;
        } catch (IllegalArgumentException | UnknownHostException ex) {
            return "unknown";
        }
    }
}
