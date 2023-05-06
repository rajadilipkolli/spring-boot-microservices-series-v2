/* Licensed under Apache-2.0 2023 */
package org.service.registry;

import org.springframework.boot.SpringApplication;

public class TestNamingServerApplication {
    public static void main(String[] args) {
        SpringApplication.from(NamingServerApplication::main)
                .with(MyContainersConfiguration.class)
                .run(args);
    }
}
