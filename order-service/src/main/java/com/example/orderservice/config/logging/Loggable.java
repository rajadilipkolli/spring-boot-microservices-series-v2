/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.orderservice.config.logging;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.boot.logging.LogLevel;

@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Inherited
public @interface Loggable {

    boolean params() default true;

    boolean result() default true;

    LogLevel value() default LogLevel.DEBUG;
}
