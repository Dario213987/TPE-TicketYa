package org.tudai.ticketya.core;

import java.util.UUID;
import java.util.regex.Pattern;

public final class CorrelationId {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private CorrelationId() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String value) {
        return value != null && VALID.matcher(value).matches();
    }

    public static String resolve(String incoming) {
        return isValid(incoming) ? incoming : generate();
    }
}