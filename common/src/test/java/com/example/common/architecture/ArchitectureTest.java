package com.example.common.architecture;

import com.enofex.taikai.Taikai;
import com.enofex.taikai.java.ImportsConfigurer;
import com.enofex.taikai.spring.RepositoriesConfigurer;
import com.enofex.taikai.spring.ServicesConfigurer;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    @Test
    void shouldFulfillArchitectureConstraints() {
        Taikai.builder()
                .namespace("com.example.common")
                .java(java ->
                        java.noUsageOfDeprecatedAPIs()
                                .classesShouldImplementHashCodeAndEquals()
                                .utilityClassesShouldBeFinalAndHavePrivateConstructor()
                                .imports(ImportsConfigurer::shouldHaveNoCycles))
                .spring(spring ->
                        spring.noAutowiredFields()
                                .controllers(controllers ->
                                        controllers
                                                .namesShouldEndWithController()
                                                .shouldBeAnnotatedWithRestController())
                                .services(ServicesConfigurer::namesShouldEndWithService)
                                .repositories(RepositoriesConfigurer::namesShouldEndWithRepository))
                .build()
                .checkAll();
    }
}
