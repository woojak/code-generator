package com.labeltools.palletlabel;

import java.math.BigInteger;

public final class Gs1Utils {
    private Gs1Utils() {}

    public static String digitsOnly(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static int checkDigit(String body) {
        int sum = 0;
        int weight = 3;
        for (int i = body.length() - 1; i >= 0; i--) {
            char c = body.charAt(i);
            if (!Character.isDigit(c)) throw new IllegalArgumentException("Digits required");
            sum += (c - '0') * weight;
            weight = (weight == 3) ? 1 : 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    public static boolean isValidGtin13(String value) {
        String d = digitsOnly(value);
        if (d.length() != 13) return false;
        return checkDigit(d.substring(0, 12)) == (d.charAt(12) - '0');
    }

    public static boolean isValidGtin14(String value) {
        String d = digitsOnly(value);
        if (d.length() != 14) return false;
        return checkDigit(d.substring(0, 13)) == (d.charAt(13) - '0');
    }

    public static String normalizeGtin14(String value) {
        String d = digitsOnly(value);
        if (d.length() == 13 && isValidGtin13(d)) d = "0" + d;
        return isValidGtin14(d) ? d : "";
    }

    public static boolean isValidSscc(String value) {
        String d = digitsOnly(value);
        if (d.length() != 18) return false;
        return checkDigit(d.substring(0, 17)) == (d.charAt(17) - '0');
    }

    public static String nextSscc(String previous) {
        String d = digitsOnly(previous);
        if (d.length() != 18 || !isValidSscc(d)) {
            throw new IllegalArgumentException("Previous SSCC must be a valid 18-digit SSCC");
        }
        String body = d.substring(0, 17);
        BigInteger next = new BigInteger(body).add(BigInteger.ONE);
        String nextBody = String.format("%017d", next);
        if (nextBody.length() != 17) throw new IllegalArgumentException("SSCC sequence overflow");
        return nextBody + checkDigit(nextBody);
    }

    public static String spacedSscc(String value) {
        String d = digitsOnly(value);
        if (d.length() != 18) return value;
        return d.substring(0, 1) + " " + d.substring(1, 10) + " " + d.substring(10, 17) + " " + d.substring(17);
    }
}
