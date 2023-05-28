/* Licensed under Apache-2.0 2023 */
package com.example.paymentservice.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;

@TestConfiguration(proxyBeanMethods = false)
@ImportTestcontainers(MyTestContainers.class)
public class MyContainersConfiguration {}
