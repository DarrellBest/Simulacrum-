package com.simulacrum.data;

/** NMEA 0183 parser for GGA and RMC sentences. */
public final class NmeaParser {
    private NmeaParser() {
    }

    /**
     * Parse a single NMEA sentence into a {@link DataSource.Fix}. Returns {@code null} if the
     * sentence is blank, the checksum is wrong, or the sentence type is not supported.
     */
    public static DataSource.Fix parse(String raw) {
        if (raw == null) return null;
        String line = raw.trim();
        if (line.isEmpty() || line.charAt(0) != '$') return null;
        int star = line.indexOf('*');
        String body = star < 0 ? line.substring(1) : line.substring(1, star);
        if (star > 0 && line.length() >= star + 3) {
            String expected = line.substring(star + 1, star + 3).toUpperCase();
            int sum = 0;
            for (int i = 0; i < body.length(); i++) sum ^= body.charAt(i);
            String actual = String.format("%02X", sum & 0xFF);
            if (!expected.equals(actual)) return null;
        }
        String[] parts = body.split(",", -1);
        if (parts.length == 0) return null;
        String type = parts[0];
        if (type.length() < 5) return null;
        String kind = type.substring(2);
        return switch (kind) {
            case "GGA" -> parseGga(parts);
            case "RMC" -> parseRmc(parts);
            default -> null;
        };
    }

    private static DataSource.Fix parseGga(String[] p) {
        if (p.length < 10) return null;
        Double lat = latitude(p[2], p[3]);
        Double lon = longitude(p[4], p[5]);
        if (lat == null || lon == null) return null;
        double alt = parseDouble(p[9], 0.0);
        return new DataSource.Fix(lat, lon, alt, Double.NaN, Double.NaN, System.currentTimeMillis());
    }

    private static DataSource.Fix parseRmc(String[] p) {
        if (p.length < 9) return null;
        Double lat = latitude(p[3], p[4]);
        Double lon = longitude(p[5], p[6]);
        if (lat == null || lon == null) return null;
        double knots = parseDouble(p[7], Double.NaN);
        double speedMps = Double.isNaN(knots) ? Double.NaN : knots * 0.514444;
        double heading = parseDouble(p[8], Double.NaN);
        return new DataSource.Fix(lat, lon, 0.0, speedMps, heading, System.currentTimeMillis());
    }

    private static Double latitude(String ddmm, String hemi) {
        Double v = dmmToDegrees(ddmm, 2);
        if (v == null) return null;
        return "S".equalsIgnoreCase(hemi) ? -v : v;
    }

    private static Double longitude(String dddmm, String hemi) {
        Double v = dmmToDegrees(dddmm, 3);
        if (v == null) return null;
        return "W".equalsIgnoreCase(hemi) ? -v : v;
    }

    private static Double dmmToDegrees(String token, int degDigits) {
        if (token == null || token.isBlank()) return null;
        try {
            double degrees = Double.parseDouble(token.substring(0, degDigits));
            double minutes = Double.parseDouble(token.substring(degDigits));
            return degrees + (minutes / 60.0);
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static double parseDouble(String token, double fallback) {
        if (token == null || token.isBlank()) return fallback;
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
