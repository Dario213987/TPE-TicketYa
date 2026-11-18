package org.tudai.ticketya.web.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.TimeZone;

public class TimeZoneEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String LEGACY_TIME_ZONE = "America/Buenos_Aires";
    private static final String CANONICAL_TIME_ZONE = "America/Argentina/Buenos_Aires";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (LEGACY_TIME_ZONE.equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone(CANONICAL_TIME_ZONE));
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
