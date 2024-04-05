/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.inventoryservice.config.logging;

import com.example.inventoryservice.utils.AppConstants;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
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
                @within(com.example.inventoryservice.config.logging.Loggable)
                || @annotation(com.example.inventoryservice.config.logging.Loggable)
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
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        Loggable methodAnnotation = method.getAnnotation(Loggable.class);

        Class<?> originClass = joinPoint.getTarget().getClass();
        Loggable classAnnotation = originClass.getAnnotation(Loggable.class);

        // get current log level

        LogLevel logLevel =
                methodAnnotation != null ? methodAnnotation.value() : classAnnotation.value();

        // before
        String methodName = method.getName();
        LogWriter.write(originClass, LogLevel.INFO, methodName + "() start execution");

        printParamsIfEnabled(
                joinPoint,
                methodSignature.getParameterNames(),
                methodAnnotation,
                originClass,
                classAnnotation,
                logLevel,
                methodName);

        long start = System.currentTimeMillis();
        // Start method execution
        Object result = joinPoint.proceed();
        long end = System.currentTimeMillis();

        // show results
        if (result != null) {
            boolean printResponse =
                    methodAnnotation != null ? methodAnnotation.result() : classAnnotation.result();
            if (printResponse) {
                LogWriter.write(originClass, logLevel, methodName + "() Returned : " + result);
            }
        }

        // print results
        LogWriter.write(
                originClass,
                LogLevel.INFO,
                methodName
                        + "() finished execution and took ("
                        + (end - start)
                        + ") mills to execute");
        return result;
    }

    private void printParamsIfEnabled(
            ProceedingJoinPoint joinPoint,
            String[] parameterNames,
            Loggable methodAnnotation,
            Class<?> originClass,
            Loggable classAnnotation,
            LogLevel logLevel,
            String methodName) {
        boolean printParams =
                methodAnnotation != null ? methodAnnotation.params() : classAnnotation.params();

        if (printParams && !ObjectUtils.isEmpty(joinPoint.getArgs())) {
            List<String> stringArrayList = new ArrayList<>();
            Object[] args = joinPoint.getArgs();

            for (int i = 0; i < args.length; i++) {
                stringArrayList.add(parameterNames[i] + " : " + args[i]);
            }
            String argsString = String.join(", ", stringArrayList);
            LogWriter.write(originClass, logLevel, methodName + "() args :: -> " + argsString);
        }
    }
}
