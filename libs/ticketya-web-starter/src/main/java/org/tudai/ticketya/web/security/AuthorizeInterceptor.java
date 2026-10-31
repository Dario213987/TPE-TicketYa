package org.tudai.ticketya.web.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionException;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.tudai.ticketya.core.Authorize;
import org.tudai.ticketya.core.Role;
import org.tudai.ticketya.core.UserContext;
import org.tudai.ticketya.core.exception.ForbiddenException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthorizeInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(AuthorizeInterceptor.class);

    private final UserContext userContext;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    public AuthorizeInterceptor(UserContext userContext) {
        this.userContext = userContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        Authorize authorize = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), Authorize.class);
        if (authorize == null) {
            authorize = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), Authorize.class);
        }
        if (authorize == null) {
            return true;
        }

        String userId = userContext.userId();
        Role role = userContext.role();

        if (!evaluate(authorize.value(), role)) {
            log.warn("Acceso denegado: userId={} role={} {} {}", userId, role, request.getMethod(),
                    request.getRequestURI());
            throw new ForbiddenException("No tenés permisos para realizar esta operación.");
        }
        return true;
    }

    private boolean evaluate(String source, Role role) {
        try {
            Expression expression = cache.computeIfAbsent(source, parser::parseExpression);
            EvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding()
                    .withInstanceMethods()
                    .withRootObject(new AuthorizationRoot(role))
                    .build();
            return Boolean.TRUE.equals(expression.getValue(context, Boolean.class));
        } catch (ExpressionException e) {
            throw new IllegalStateException("Expresión de @Authorize inválida: " + source, e);
        }
    }

    int cacheSize() {
        return cache.size();
    }
}