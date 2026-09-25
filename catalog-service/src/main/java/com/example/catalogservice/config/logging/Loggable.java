/***
<p>
    Licensed under MIT License Copyright (c) 2021-2024 Raja Kolli.
</p>
***/

package com.example.catalogservice.config.logging;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.logging.LogLevel;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Loggable {

    boolean params() default true;

    boolean result() default true;

    LogLevel value() default LogLevel.DEBUG;
}
