package org.tudai.ticketya.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class RequestLogger {

    public static final String LOGGER_NAME = "ticketya.http";
    public static final String ANONYMOUS = "anonymous";

    private static final int MAX_USER_LENGTH = 64;
    private static final String MESSAGE = "{} {} -> {} ({} ms) user={}";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    private RequestLogger() {}

    public static void log(String method, String path, String userId, int status, long durationMs) {
        String user = sanitizeUser(userId);
        String safePath = path == null ? "" : path;

        MDC.put(MdcKeys.HTTP_METHOD, method);
        MDC.put(MdcKeys.HTTP_PATH, safePath);
        MDC.put(MdcKeys.USER_ID, user);
        MDC.put(MdcKeys.HTTP_STATUS, String.valueOf(status));
        MDC.put(MdcKeys.DURATION_MS, String.valueOf(durationMs));
        try {
            if (status >= 500) {
                log.warn(MESSAGE, method, safePath, status, durationMs, user);
            } else {
                log.info(MESSAGE, method, safePath, status, durationMs, user);
            }
        } finally {
            MDC.remove(MdcKeys.HTTP_METHOD);
            MDC.remove(MdcKeys.HTTP_PATH);
            MDC.remove(MdcKeys.USER_ID);
            MDC.remove(MdcKeys.HTTP_STATUS);
            MDC.remove(MdcKeys.DURATION_MS);
        }
    }

    private static String sanitizeUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return ANONYMOUS;
        }
        String clean = userId.trim().replaceAll("\\p{Cntrl}", "_");
        return clean.length() > MAX_USER_LENGTH ? clean.substring(0, MAX_USER_LENGTH) : clean;
    }
}