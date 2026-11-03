package org.tudai.ticketya.web.feign;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.tudai.ticketya.core.HeaderNames;
import org.tudai.ticketya.core.MdcKeys;

import java.util.Objects;

public class CorrelationIdFeignInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        String id = MDC.get(MdcKeys.CORRELATION_ID);
        if (Objects.nonNull(id) && !id.isBlank()) {
            template.header(HeaderNames.CORRELATION_ID, id);
        }
    }
}