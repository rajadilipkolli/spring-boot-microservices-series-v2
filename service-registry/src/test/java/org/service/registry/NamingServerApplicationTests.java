/* Licensed under Apache-2.0 2022 */
package org.service.registry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(MyContainersConfiguration.class)
class NamingServerApplicationTests {

    @Test
    void contextLoads() {}
}
