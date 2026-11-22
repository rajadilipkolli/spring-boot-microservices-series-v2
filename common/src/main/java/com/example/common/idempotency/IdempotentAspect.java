/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.idempotency;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
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

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

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
        String key = resolveKey(joinPoint, idempotent);
        Duration ttl = Duration.of(idempotent.ttl(), idempotent.ttlUnit());

        // Check if already processed
        Optional<Object> existingResult = idempotencyStore.getProcessedResult(key);
        if (existingResult.isPresent()) {
            log.info(
                    "Idempotent key '{}' has already been processed. Returning cached result.",
                    key);
            return existingResult.get();
        }

        // Attempt to acquire in-flight lock
        boolean acquired = idempotencyStore.acquireLock(key, ttl);
        if (!acquired) {
            // Lock could not be acquired: either another thread just completed it or it's currently
            // in flight
            Optional<Object> checkAgain = idempotencyStore.getProcessedResult(key);
            if (checkAgain.isPresent()) {
                log.info(
                        "Idempotent key '{}' processed concurrently. Returning cached result.",
                        key);
                return checkAgain.get();
            }
            log.warn("Conflict detected for idempotency key '{}'. Operation in flight.", key);
            throw new IdempotencyConflictException(key);
        }

        boolean processed = false;
        try {
            Object result = joinPoint.proceed();
            idempotencyStore.markProcessed(key, result, ttl);
            processed = true;
            return result;
        } finally {
            if (!processed) {
                // Release lock on failure/exception so it can be safely retried
                idempotencyStore.releaseLock(key);
            }
        }
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
        String className = signature.getDeclaringTypeName();
        String methodName = signature.getName();
        String argsString = Arrays.deepToString(joinPoint.getArgs());

        String raw = className + "#" + methodName + "(" + argsString + ")";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return raw;
        }
    }
}
