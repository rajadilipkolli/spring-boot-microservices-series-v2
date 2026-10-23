/***
 * <p>
 * Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.logging;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.ObjectUtils;

@Aspect
public class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    private static final Pattern SENSITIVE_NAME =
            Pattern.compile(
                    "(?i)password|credit[_-]?card|"
                            + "(?:^|[^a-z])(?:passwd|pwd|passphrase|secrets?|tokens?|credentials?|"
                            + "auth(?:entication|orization)?|api[_-]?key|private[_-]?key|"
                            + "access[_-]?key|ssn)(?:$|[^a-z])");

    private final Environment env;

    public LoggingAspect(Environment env) {
        this.env = env;
    }

    @Pointcut("@annotation(com.example.common.logging.Loggable)")
    private void methodLoggablePointcut() {
        // Pointcut definition
    }

    @Pointcut(
            "@within(com.example.common.logging.Loggable)"
                    + " && !@annotation(com.example.common.logging.Loggable)")
    private void classLoggablePointcut() {
        // Pointcut definition
    }

    @Around("methodLoggablePointcut()")
    public Object logMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Loggable loggable = AnnotationUtils.findAnnotation(signature.getMethod(), Loggable.class);
        if (loggable == null) {
            loggable =
                    AnnotationUtils.findAnnotation(
                            joinPoint.getTarget().getClass(), Loggable.class);
        }
        return executeLogging(joinPoint, loggable);
    }

    @Around("classLoggablePointcut()")
    public Object logClass(ProceedingJoinPoint joinPoint) throws Throwable {
        Loggable loggable =
                AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), Loggable.class);
        return executeLogging(joinPoint, loggable);
    }

    private Object executeLogging(ProceedingJoinPoint joinPoint, Loggable loggable)
            throws Throwable {

        String methodName = joinPoint.getSignature().getName();

        LogLevel logLevel = loggable != null ? loggable.value() : LogLevel.DEBUG;

        logMethodStart(joinPoint, methodName);
        if (loggable != null) {
            logMethodParams(joinPoint, logLevel, methodName, loggable);
        }

        long start = System.currentTimeMillis();

        Object result;

        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            logException(joinPoint, ex);
            throw ex;
        }

        long end = System.currentTimeMillis();

        if (loggable != null) {
            logMethodResult(joinPoint, result, logLevel, methodName, loggable);
        }
        logMethodCompletion(joinPoint, methodName, end - start);

        return result;
    }

    private void logMethodStart(ProceedingJoinPoint joinPoint, String methodName) {
        logExecutionDetails(joinPoint, LogLevel.INFO, methodName + "() start execution");
    }

    private void logMethodParams(
            ProceedingJoinPoint joinPoint,
            LogLevel logLevel,
            String methodName,
            Loggable loggable) {

        if (!loggable.params() || ObjectUtils.isEmpty(joinPoint.getArgs())) {
            return;
        }

        MethodSignature signature = (MethodSignature) joinPoint.getSignature();

        String[] parameterNames = signature.getParameterNames();

        Object[] args = joinPoint.getArgs();

        List<String> values = new ArrayList<>(args.length);

        for (int i = 0; i < args.length; i++) {

            String paramName =
                    parameterNames != null && i < parameterNames.length ? parameterNames[i] : null;

            String displayName = paramName == null || paramName.isBlank() ? "arg" + i : paramName;
            values.add(displayName + " : " + redactValue(paramName, args[i]));
        }

        logExecutionDetails(
                joinPoint,
                logLevel,
                methodName
                        + "() args :: -> "
                        + LogSanitizer.sanitizeForLog(String.join(", ", values), 1024));
    }

    private void logMethodResult(
            ProceedingJoinPoint joinPoint,
            Object result,
            LogLevel logLevel,
            String methodName,
            Loggable loggable) {

        if (!loggable.result() || result == null) {
            return;
        }

        logExecutionDetails(
                joinPoint,
                logLevel,
                methodName
                        + "() Returned : "
                        + LogSanitizer.sanitizeForLog(redactValue(methodName, result), 1024));
    }

    private static String redactValue(String name, Object value) {
        if (name == null || name.isBlank()) {
            return "REDACTED";
        }

        // Split camel case and acronyms so SSN matches without matching className/businessName.
        String words =
                name.replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                        .replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        if (SENSITIVE_NAME.matcher(words).find()) {
            return "REDACTED";
        }

        // Only render known scalar types; arbitrary toString() methods may expose credentials.
        return switch (value) {
            case null -> "null";
            case String s -> s;
            case Boolean b -> b.toString();
            case Character c -> c.toString();
            case Byte b -> b.toString();
            case Short s -> s.toString();
            case Integer i -> i.toString();
            case Long l -> l.toString();
            case Float f -> f.toString();
            case Double d -> d.toString();
            default -> "REDACTED";
        };
    }

    private void logMethodCompletion(
            ProceedingJoinPoint joinPoint, String methodName, long timeTaken) {

        logExecutionDetails(
                joinPoint,
                LogLevel.INFO,
                methodName + "() finished execution and took (" + timeTaken + ") ms to execute");
    }

    private void logException(ProceedingJoinPoint joinPoint, Throwable e) {

        if (env != null && env.acceptsProfiles(Profiles.of("!prod"))) {

            log.error(
                    "Exception in {}.{}() with cause = '{}' and exception = '{}'",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    e.getCause() == null ? "NULL" : LogSanitizer.sanitizeException(e.getCause()),
                    LogSanitizer.sanitizeException(e),
                    e);

        } else {

            log.error(
                    "Exception in {}.{}() with cause = {}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    e.getCause() == null ? "NULL" : LogSanitizer.sanitizeException(e.getCause()));
        }
    }

    private void logExecutionDetails(
            ProceedingJoinPoint joinPoint, LogLevel logLevel, String message) {

        Object target = joinPoint.getTarget();

        if (target == null) {
            log.error(message);
            return;
        }

        LogWriter.write(target.getClass(), logLevel, message);
    }
}
