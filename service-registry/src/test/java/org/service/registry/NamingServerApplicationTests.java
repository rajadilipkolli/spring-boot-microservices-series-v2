package org.service.registry;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NamingServerApplicationTests {

  private static final int CONFIG_SERVER_INTERNAL_PORT = 8888;

  @Container
  static ConfigServerContainer configServerContainer =
      new ConfigServerContainer(
          DockerImageName.parse("dockertmt/mmv2-config-server:0.0.1-SNAPSHOT"));

  static {
    configServerContainer.start();
  }

  @Test
  void contextLoads() {
    assertThat(configServerContainer.isRunning()).isTrue();
  }

  private static class ConfigServerContainer extends GenericContainer<ConfigServerContainer> {
    public ConfigServerContainer(final DockerImageName dockerImageName) {
      super(dockerImageName);
      withExposedPorts(CONFIG_SERVER_INTERNAL_PORT);
    }
  }
}
