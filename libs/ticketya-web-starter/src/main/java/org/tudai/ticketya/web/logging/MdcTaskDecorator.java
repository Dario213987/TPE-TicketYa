package org.tudai.ticketya.web.logging;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contexto = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> anterior = MDC.getCopyOfContextMap();
            try {
                if (contexto == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(contexto);
                }
                runnable.run();
            } finally {
                if (anterior == null) {
                    MDC.clear();
                } else {
                    MDC.setContextMap(anterior);
                }
            }
        };
    }
}
