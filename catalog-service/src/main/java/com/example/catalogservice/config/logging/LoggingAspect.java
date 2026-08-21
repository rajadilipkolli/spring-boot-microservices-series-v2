/***
<p>
    Licensed under MIT License Copyright (c) 2021-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.config.logging;

import com.example.catalogservice.utils.AppConstants;
import com.example.catalogservice.utils.LogSanitizer;
import java.util.ArrayList;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.LogLevel;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Aspect
@Component
class LoggingAspect {

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    private final Environment env;

    LoggingAspect(Environment env) {
        this.env = env;
    }

    @Pointcut("@annotation(com.example.catalogservice.config.logging.Loggable)")
    private void methodLoggablePointcut() {
        // Pointcut definition
    }

    @Pointcut(
            "@within(com.example.catalogservice.config.logging.Loggable)"
                    + " && !@annotation(com.example.catalogservice.config.logging.Loggable)")
    private void classLoggablePointcut() {
        // Pointcut definition
    }

    @Around(
            value = "methodLoggablePointcut() && @annotation(loggable)",
            argNames = "joinPoint,loggable")
    public Object logMethod(ProceedingJoinPoint joinPoint, Loggable loggable) throws Throwable {

        return executeLogging(joinPoint, loggable);
    }

    @Around(value = "classLoggablePointcut() && @within(loggable)", argNames = "joinPoint,loggable")
    public Object logClass(ProceedingJoinPoint joinPoint, Loggable loggable) throws Throwable {

        return executeLogging(joinPoint, loggable);
    }

    private Object executeLogging(ProceedingJoinPoint joinPoint, Loggable loggable)
            throws Throwable {

        String methodName = joinPoint.getSignature().getName();

        LogLevel logLevel = loggable.value();

        logMethodStart(joinPoint, methodName);
        logMethodParams(joinPoint, logLevel, methodName, loggable);

        long start = System.currentTimeMillis();

        Object result;

        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            logException(joinPoint, ex);
            throw ex;
        }

        long end = System.currentTimeMillis();

        logMethodResult(joinPoint, result, logLevel, methodName, loggable);
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
                    parameterNames != null && i < parameterNames.length
                            ? parameterNames[i]
                            : "arg" + i;

            Object argValue = args[i];

            if (paramName.matches("(?i).*(password|creditCard|ssn).*")) {
                argValue = "REDACTED";
            }

            values.add(paramName + " : " + argValue);
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
                        + LogSanitizer.sanitizeForLog(String.valueOf(result), 1024));
    }

    private void logMethodCompletion(
            ProceedingJoinPoint joinPoint, String methodName, long timeTaken) {

        logExecutionDetails(
                joinPoint,
                LogLevel.INFO,
                methodName + "() finished execution and took (" + timeTaken + ") ms to execute");
    }

    private void logException(ProceedingJoinPoint joinPoint, Throwable e) {

        if (env.acceptsProfiles(Profiles.of(AppConstants.PROFILE_NOT_PROD))) {

            log.error(
                    "Exception in {}.{}() with cause = '{}' and exception = '{}'",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    e.getCause() == null ? "NULL" : e.getCause(),
                    LogSanitizer.sanitizeException(e),
                    e);

        } else {

            log.error(
                    "Exception in {}.{}() with cause = {}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    e.getCause() == null ? "NULL" : e.getCause());
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
