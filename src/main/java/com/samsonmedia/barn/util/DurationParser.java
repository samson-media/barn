package com.samsonmedia.barn.util;

import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for parsing human-readable duration strings.
 *
 * <p>Supports formats like:
 * <ul>
 *   <li>30m - 30 minutes</li>
 *   <li>24h - 24 hours</li>
 *   <li>7d - 7 days</li>
 *   <li>2w - 2 weeks</li>
 * </ul>
 */
public final class DurationParser {

    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+)([mhdwMHDW])$");

    private DurationParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses a duration string into a Duration.
     *
     * @param input the duration string (e.g., "24h", "7d")
     * @return the parsed Duration
     * @throws IllegalArgumentException if the input is invalid
     */
    public static Duration parse(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Duration cannot be null or empty");
        }

        String trimmed = input.trim();
        Matcher matcher = DURATION_PATTERN.matcher(trimmed);

        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                "Invalid duration format: '" + input + "'. Expected format: <number><unit> "
                + "(e.g., 30m, 24h, 7d, 2w)");
        }

        long value = Long.parseLong(matcher.group(1));
        String unit = matcher.group(2).toLowerCase();

        if (value <= 0) {
            throw new IllegalArgumentException("Duration value must be positive: " + value);
        }

        return switch (unit) {
            case "m" -> Duration.ofMinutes(value);
            case "h" -> Duration.ofHours(value);
            case "d" -> Duration.ofDays(value);
            case "w" -> Duration.ofDays(value * 7);
            default -> throw new IllegalArgumentException("Invalid duration unit: " + unit);
        };
    }
}
