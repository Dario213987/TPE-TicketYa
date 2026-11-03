package org.tudai.ticketya.core;

import org.slf4j.MDC;

import java.util.Objects;

public final class CorrelationScope implements AutoCloseable {

    private final String id;
    private final String previous;

    private CorrelationScope(String id) {
        this.id = id;
        this.previous = MDC.get(MdcKeys.CORRELATION_ID);
        MDC.put(MdcKeys.CORRELATION_ID, id);
    }

    public static CorrelationScope open(String id) {
        return new CorrelationScope(CorrelationId.resolve(id));
    }

    public static CorrelationScope openNew() {
        return new CorrelationScope(CorrelationId.generate());
    }

    public String id() {
        return id;
    }

    @Override
    public void close() {
        if (Objects.isNull(previous)) {
            MDC.remove(MdcKeys.CORRELATION_ID);
        } else {
            MDC.put(MdcKeys.CORRELATION_ID, previous);
        }
    }
}