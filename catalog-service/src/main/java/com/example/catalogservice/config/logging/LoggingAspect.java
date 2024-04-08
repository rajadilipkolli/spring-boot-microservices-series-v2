/***
<p>
    Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli.
</p>
***/

package com.example.catalogservice.config.logging;

import com.example.catalogservice.utils.AppConstants;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
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
public class LoggingAspect {

    private final Environment env;

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    public LoggingAspect(Environment env) {
        this.env = env;
    }

    @Pointcut(
            "within(@org.springframework.stereotype.Repository *)"
                    + " || within(@org.springframework.stereotype.Service *)"
                    + " || within(@org.springframework.web.bind.annotation.RestController *)")
    public void springBeanPointcut() {
        // pointcut definition
    }

    @Pointcut(
            "@within(com.example.catalogservice.config.logging.Loggable) || "
                    + "@annotation(com.example.catalogservice.config.logging.Loggable)")
    public void applicationPackagePointcut() {
        // pointcut definition
    }

    @AfterThrowing(pointcut = "applicationPackagePointcut()", throwing = "e")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable e) {
        if (env.acceptsProfiles(Profiles.of(AppConstants.PROFILE_NOT_PROD))) {
            log.error(
                    "Exception in {}.{}() with cause = '{}' and exception = '{}'",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    e.getCause() == null ? "NULL" : e.getCause(),
                    e.getMessage(),
                    e);

        } else {
            log.error(
                    "Exception in {}.{}() with cause = {}",
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    e.getCause() == null ? "NULL" : e.getCause());
        }
    }

    @Around("applicationPackagePointcut()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        LogLevel logLevel = determineLogLevel(joinPoint);

        logMethodStart(joinPoint, methodName);
        logMethodParams(joinPoint, logLevel, methodName);

        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long end = System.currentTimeMillis();

        logMethodResult(joinPoint, result, logLevel, methodName);
        logMethodCompletion(joinPoint, methodName, end - start);

        return result;
    }

    private void logMethodStart(ProceedingJoinPoint joinPoint, String methodName) {
        logExecutionDetails(joinPoint, LogLevel.INFO, methodName + "() start execution");
    }

    private void logMethodParams(
            ProceedingJoinPoint joinPoint, LogLevel logLevel, String methodName) {
        logMethodParamsIfEnabled(joinPoint, logLevel, methodName);
    }

    private void logMethodResult(
            ProceedingJoinPoint joinPoint, Object result, LogLevel logLevel, String methodName) {
        logMethodResultIfEnabled(joinPoint, result, logLevel, methodName);
    }

    private void logMethodCompletion(
            ProceedingJoinPoint joinPoint, String methodName, long timeTaken) {
        logExecutionDetails(
                joinPoint,
                LogLevel.INFO,
                methodName + "() finished execution and took (" + timeTaken + ") mills to execute");
    }

    // Generic method to retrieve Loggable annotation
    private Optional<Loggable> getLoggableAnnotation(ProceedingJoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        Loggable methodAnnotation = method.getAnnotation(Loggable.class);
        Loggable classAnnotation = joinPoint.getTarget().getClass().getAnnotation(Loggable.class);
        return Optional.ofNullable(Optional.ofNullable(methodAnnotation).orElse(classAnnotation));
    }

    private LogLevel determineLogLevel(ProceedingJoinPoint joinPoint) {
        return getLoggableAnnotation(joinPoint)
                .map(Loggable::value)
                .orElse(LogLevel.DEBUG); // Default LogLevel if annotation is not present
    }

    private boolean shouldLog(
            ProceedingJoinPoint joinPoint, Function<Loggable, Boolean> loggableProperty) {
        return getLoggableAnnotation(joinPoint)
                .map(loggableProperty)
                .orElse(false); // Default to false if annotation is not present
    }

    private void logExecutionDetails(
            ProceedingJoinPoint joinPoint, LogLevel logLevel, String message) {
        LogWriter.write(joinPoint.getTarget().getClass(), logLevel, message);
    }

    private void logMethodParamsIfEnabled(
            ProceedingJoinPoint joinPoint, LogLevel logLevel, String methodName) {
        boolean printParams = shouldLog(joinPoint, Loggable::params);

        if (printParams && !ObjectUtils.isEmpty(joinPoint.getArgs())) {
            String[] parameterNames =
                    ((MethodSignature) joinPoint.getSignature()).getParameterNames();
            List<String> stringArrayList = new ArrayList<>();
            Object[] args = joinPoint.getArgs();

            for (int i = 0; i < args.length; i++) {
                String paramName = parameterNames[i];
                Object argValue = args[i];
                // Check if the parameter name suggests it might contain sensitive data
                if (paramName.matches("(?i).*(password|creditCard|ssn).*")) {
                    argValue = "REDACTED"; // Anonymize sensitive data
                }
                stringArrayList.add(paramName + " : " + argValue);
            }
            String argsString = String.join(", ", stringArrayList);
            logExecutionDetails(joinPoint, logLevel, methodName + "() args :: -> " + argsString);
        }
    }

    private void logMethodResultIfEnabled(
            ProceedingJoinPoint joinPoint, Object result, LogLevel logLevel, String methodName) {
        if (result != null) {
            boolean printResponse = shouldLog(joinPoint, Loggable::result);
            if (printResponse) {
                logExecutionDetails(joinPoint, logLevel, methodName + "() Returned : " + result);
            }
        }
    }
}
