/*** Licensed under MIT License Copyright (c) 2021-2023 Raja Kolli. ***/
package com.example.paymentservice.common;

import static com.example.paymentservice.utils.AppConstants.PROFILE_TEST;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.zalando.problem.spring.web.advice.ProblemHandling;

@Profile(PROFILE_TEST)
@ControllerAdvice
public final class ExceptionHandling implements ProblemHandling {}
