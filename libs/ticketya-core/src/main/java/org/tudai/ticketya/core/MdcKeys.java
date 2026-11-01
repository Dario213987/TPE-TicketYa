package org.tudai.ticketya.core;

public final class MdcKeys {
    public static final String CORRELATION_ID = "correlationId";
    public static final String USER_ID = "userId";
    public static final String HTTP_METHOD = "method";
    public static final String HTTP_PATH = "path";
    public static final String HTTP_STATUS = "status";
    public static final String DURATION_MS = "durationMs";

    private MdcKeys() {}
}