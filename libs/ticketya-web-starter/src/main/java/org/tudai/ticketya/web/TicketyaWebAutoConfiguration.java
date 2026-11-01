package org.tudai.ticketya.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Request;
import feign.codec.ErrorDecoder;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.Ordered;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.web.correlation.CorrelationIdFilter;
import org.tudai.ticketya.web.error.GlobalExceptionHandler;
import org.tudai.ticketya.web.feign.CorrelationIdFeignInterceptor;
import org.tudai.ticketya.web.feign.TicketyaErrorDecoder;
import org.tudai.ticketya.web.feign.UserHeadersFeignInterceptor;
import org.tudai.ticketya.web.security.AuthorizeInterceptor;
import org.tudai.ticketya.web.user.HeaderUserContext;

import java.util.concurrent.TimeUnit;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class TicketyaWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(GlobalExceptionHandler.class)
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

    @Bean
    @RequestScope(proxyMode = ScopedProxyMode.INTERFACES)
    @ConditionalOnMissingBean(UserContext.class)
    public UserContext userContext(HttpServletRequest request) {
        return new HeaderUserContext(request);
    }

    @Bean
    @ConditionalOnMissingBean(name = "correlationIdFilterRegistration")
    public FilterRegistrationBean<CorrelationIdFilter> correlationIdFilterRegistration() {
        FilterRegistrationBean<CorrelationIdFilter> registration =
                new FilterRegistrationBean<>(new CorrelationIdFilter());
        registration.setName("correlationIdFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
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

        @Bean
        public UserHeadersFeignInterceptor userHeadersFeignInterceptor() {
            return new UserHeadersFeignInterceptor();
        }

        @Bean
        @ConditionalOnMissingBean(AuthorizeInterceptor.class)
        public AuthorizeInterceptor authorizeInterceptor(UserContext userContext) {
            return new AuthorizeInterceptor(userContext);
        }

        @Bean
        public WebMvcConfigurer authorizeWebMvcConfigurer(AuthorizeInterceptor interceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(InterceptorRegistry registry) {
                    registry.addInterceptor(interceptor);
                }
            };
        }

        @Bean
        public CorrelationIdFeignInterceptor correlationIdFeignInterceptor() {
            return new CorrelationIdFeignInterceptor();
        }
    }
}