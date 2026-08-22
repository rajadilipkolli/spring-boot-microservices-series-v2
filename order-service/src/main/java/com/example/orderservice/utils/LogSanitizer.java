/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.orderservice.utils;

import java.util.Collection;
import java.util.Objects;
import java.util.regex.Pattern;

/** Utility to sanitize strings before writing them to logs to prevent log injection. */
public final class LogSanitizer {

    private static final Pattern CRLF = Pattern.compile("[\r\n]+");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}&&[^\r\n]]+");
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\u001B\\[[;\\d]*[ -/]*[@-~]");

    private LogSanitizer() {}

    public static String sanitizeForLog(String input) {
        return sanitizeForLog(input, Integer.MAX_VALUE);
    }

    public static String sanitizeForLog(String input, int maxLength) {
        if (input == null) return null;
        String s = ANSI_ESCAPE.matcher(input).replaceAll("");
        s = CRLF.matcher(s).replaceAll("_");
        s = CONTROL.matcher(s).replaceAll("_");
        if (s.length() > maxLength) return s.substring(0, maxLength) + "...";
        return s;
    }

    public static String sanitizeException(Throwable ex) {
        if (ex == null) return null;
        String msg = ex.getMessage();
        if (msg == null || msg.isBlank()) msg = ex.getClass().getSimpleName();
        return sanitizeForLog(msg, 1024);
    }

    public static String sanitizeCollection(Collection<?> items) {
        if (items == null) return "[]";
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Object o : items) {
            if (!first) sb.append(',').append(' ');
            first = false;
            sb.append(sanitizeForLog(Objects.toString(o, "null"), 256));
        }
        sb.append(']');
        return sb.toString();
    }
}
