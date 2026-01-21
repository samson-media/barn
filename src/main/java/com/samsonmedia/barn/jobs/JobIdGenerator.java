package com.samsonmedia.barn.jobs;

import java.security.SecureRandom;

/**
 * Generates unique job identifiers.
 *
 * <p>Job IDs follow the format: "job-" followed by 8 random hexadecimal characters.
 * For example: "job-9f83c012"
 */
public final class JobIdGenerator {

    private static final String PREFIX = "job-";
    private static final int HEX_LENGTH = 8;
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private JobIdGenerator() {
        // Utility class - prevent instantiation
    }

    /**
     * Generates a new unique job ID.
     *
     * @return a new job ID in the format "job-XXXXX"
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder(PREFIX.length() + HEX_LENGTH);
        sb.append(PREFIX);
        for (int i = 0; i < HEX_LENGTH; i++) {
            sb.append(HEX_CHARS[RANDOM.nextInt(HEX_CHARS.length)]);
        }
        return sb.toString();
    }

    /**
     * Validates that a string is a valid job ID format.
     *
     * @param id the ID to validate
     * @return true if the ID matches the expected format
     */
    public static boolean isValid(String id) {
        if (id == null || id.length() != PREFIX.length() + HEX_LENGTH) {
            return false;
        }
        if (!id.startsWith(PREFIX)) {
            return false;
        }
        String hex = id.substring(PREFIX.length());
        for (char c : hex.toCharArray()) {
            if (!isHexChar(c)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isHexChar(char c) {
        return c >= '0' && c <= '9' || c >= 'a' && c <= 'f';
    }
}
