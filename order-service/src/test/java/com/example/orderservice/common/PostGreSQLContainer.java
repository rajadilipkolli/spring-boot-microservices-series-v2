/***
<p>
    Licensed under MIT License Copyright (c) 2023 Raja Kolli.
</p>
***/

package com.example.orderservice.common;

import java.util.Collections;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

public interface PostGreSQLContainer {

    @ServiceConnection
    PostgreSQLContainer<?> postgreSQLContainer =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres").withTag("17-alpine"))
                    .withReuse(true)
                    .withTmpFs(Collections.singletonMap("/var/lib/postgresql/data", "rw"));
}
