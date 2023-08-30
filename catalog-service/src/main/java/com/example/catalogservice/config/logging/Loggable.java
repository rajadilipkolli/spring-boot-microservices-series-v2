/*** Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli. ***/

package com.example.catalogservice.config.logging;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Target({TYPE, METHOD})
@Retention(RUNTIME)
@Inherited
public @interface Loggable {}
