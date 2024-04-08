/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.config.logging;

import com.example.orderservice.utils.AppConstants;
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

    private static final Logger log = LoggerFactory.getLogger(LoggingAspect.class);

    private final Environment env;

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
            """
                @within(com.example.orderservice.config.logging.Loggable)
                || @annotation(com.example.orderservice.config.logging.Loggable)
            """)
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
        LogLevel logLevel = determineLogLevel(joinPoint);
        String methodName = joinPoint.getSignature().getName();

        logExecutionDetails(joinPoint, LogLevel.INFO, methodName + "() start execution");
        logMethodParamsIfEnabled(joinPoint, logLevel, methodName);

        long start = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long end = System.currentTimeMillis();

        logMethodResultIfEnabled(joinPoint, result, logLevel, methodName);

        logExecutionDetails(
                joinPoint,
                LogLevel.INFO,
                methodName
                        + "() finished execution and took ("
                        + +(end - start)
                        + ") mills to execute");

        return result;
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
                stringArrayList.add(parameterNames[i] + " : " + args[i]);
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
