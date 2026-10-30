package com.bharat.stockline.util;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Format {
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    private Format() {}

    /** 129900 paise -> "₹1,299.00" */
    public static String money(long paise) {
        return NumberFormat.getCurrencyInstance(Locale.of("en", "IN")).format(paise / 100.0);
    }

    public static String number(long n) {
        return NumberFormat.getIntegerInstance(Locale.of("en", "IN")).format(n);
    }

    public static String dateTime(Instant at) {
        return at == null ? "" : DATE_TIME.format(at.atZone(ZONE));
    }

    public static String ago(Instant at) {
        if (at == null) return "";
        long s = Math.max(0, Duration.between(at, Instant.now()).getSeconds());
        if (s < 45) return "just now";
        if (s < 3600) return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        long d = s / 86400;
        return d == 1 ? "yesterday" : d + " days ago";
    }

    public static String stars(int rating) {
        int r = Math.max(0, Math.min(5, rating));
        return "★".repeat(r) + "☆".repeat(5 - r);
    }
}
