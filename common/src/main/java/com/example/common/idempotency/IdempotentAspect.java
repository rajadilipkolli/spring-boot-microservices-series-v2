/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

/**
 * Aspect to intercept methods annotated with {@link Idempotent}. Guarantees duplicate execution
 * bypass if already processed, returning cached result, or throws {@link
 * IdempotencyConflictException} if currently in-flight.
 */
@Aspect
public class IdempotentAspect {

    private final RetryTemplate completionRetry = new RetryTemplate();

    private final IdempotencyStore idempotencyStore;
    private final ExpressionParser expressionParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer =
            new DefaultParameterNameDiscoverer();

    public IdempotentAspect(IdempotencyStore idempotencyStore) {
        this.idempotencyStore = idempotencyStore;
    }

    @Around("@annotation(idempotent)")
    public Object handleIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent)
            throws Throwable {
        Class<?> returnType =
                ((MethodSignature) joinPoint.getSignature()).getMethod().getReturnType();
        if (returnType != String.class && returnType != void.class) {
            throw new IllegalArgumentException("@Idempotent requires a String or void return type");
        }
        String key = resolveKey(joinPoint, idempotent);
        Duration ttl = Duration.of(idempotent.ttl(), idempotent.ttlUnit());
        var cached = idempotencyStore.lookup(key);
        if (cached.state() == IdempotencyStore.State.COMPLETED) return cached.result();
        Optional<String> claim = idempotencyStore.acquireLock(key, ttl);
        if (claim.isEmpty()) {
            var snapshot = idempotencyStore.lookup(key);
            if (snapshot.state() == IdempotencyStore.State.COMPLETED) return snapshot.result();
            throw new IdempotencyConflictException(key);
        }

        String owner = claim.orElseThrow();
        String result;
        try {
            result = (String) joinPoint.proceed();
        } catch (Throwable failure) {
            idempotencyStore.releaseLock(key, owner);
            throw failure;
        }
        // Only retry the write; the business operation has already completed.
        if (!completionRetry.invoke(
                () -> idempotencyStore.markProcessed(key, owner, result, ttl))) {
            throw new IdempotencyConflictException(
                    key, "Execution completed but its claim was lost");
        }
        return result;
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        String keySpel = idempotent.key();
        if (keySpel != null && !keySpel.isBlank()) {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            Method method = signature.getMethod();
            Object target = joinPoint.getTarget();
            Object[] args = joinPoint.getArgs();

            MethodBasedEvaluationContext context =
                    new MethodBasedEvaluationContext(target, method, args, parameterNameDiscoverer);

            Expression expression = expressionParser.parseExpression(keySpel);
            Object evaluated = expression.getValue(context);
            if (evaluated == null) {
                throw new IllegalArgumentException(
                        "Evaluated idempotency key for expression '" + keySpel + "' was null");
            }
            if (evaluated instanceof IdempotencyKey ik) {
                return ik.value();
            }
            return evaluated.toString();
        }

        // Fallback: SHA-256 of class + method + argument array
        return generateDefaultKey(joinPoint);
    }

    private String generateDefaultKey(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        StringBuilder raw = new StringBuilder(signature.getMethod().toGenericString());
        appendArgument(raw, joinPoint.getArgs());
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(raw.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void appendArgument(StringBuilder out, Object arg) {
        if (arg == null) {
            out.append("N");
            return;
        }
        Class<?> type = arg.getClass();
        appendField(out, type.getName());
        if (type.isArray()) {
            int length = Array.getLength(arg);
            out.append(length).append(':');
            for (int i = 0; i < length; i++) appendArgument(out, Array.get(arg, i));
        } else {
            appendField(out, arg.toString());
        }
    }

    private static void appendField(StringBuilder out, String value) {
        out.append(value.length()).append(':').append(value);
    }
}
