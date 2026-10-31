package org.tudai.ticketya.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.codec.ErrorDecoder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.feign.TicketyaErrorDecoder;

import java.util.concurrent.TimeUnit;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TicketyaWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(ErrorDecoder.class)
    static class FeignDefaultsConfiguration {

        @Bean
        @ConditionalOnMissingBean(ErrorDecoder.class)
        public ErrorDecoder ticketyaErrorDecoder(ObjectProvider<ObjectMapper> mapper) {
            return new TicketyaErrorDecoder(mapper.getIfAvailable(ObjectMapper::new));
        }

        @Bean
        @ConditionalOnMissingBean(Request.Options.class)
        public Request.Options ticketyaFeignOptions(
                @Value("${ticketya.feign.connect-timeout-ms:2000}") int connectTimeoutMs,
                @Value("${ticketya.feign.read-timeout-ms:5000}") int readTimeoutMs) {
            return new Request.Options(connectTimeoutMs, TimeUnit.MILLISECONDS,
                    readTimeoutMs, TimeUnit.MILLISECONDS, true);
        }
    }
}