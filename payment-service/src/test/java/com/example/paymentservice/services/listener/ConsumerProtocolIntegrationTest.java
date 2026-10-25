/*** Licensed under MIT License Copyright (c) 2026 Raja Kolli. ***/
package com.example.paymentservice.services.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.paymentservice.common.AbstractIntegrationTest;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.GroupListing;
import org.apache.kafka.clients.admin.ListGroupsOptions;
import org.apache.kafka.common.GroupState;
import org.apache.kafka.common.GroupType;
import org.junit.jupiter.api.Test;

class ConsumerProtocolIntegrationTest extends AbstractIntegrationTest {

    @Test
    void verifiesConsumerProtocolGroupType() {
        String bootstrapServers = String.join(",", kafkaConnectionDetails.getBootstrapServers());
        try (Admin admin =
                     Admin.create(
                             Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers))) {
            await().atMost(Duration.ofSeconds(60))
                    .pollInterval(Duration.ofMillis(500))
                    .untilAsserted(
                            () -> {
                                Map<String, ConsumerGroupDescription> descriptions =
                                        admin.describeConsumerGroups(Set.of("payment")).all().get();
                                assertThat(descriptions).containsKey("payment");
                                ConsumerGroupDescription paymentGroup = descriptions.get("payment");
                                assertThat(paymentGroup.type()).isEqualTo(GroupType.CONSUMER);
                                assertThat(paymentGroup.groupState())
                                        .isIn(GroupState.STABLE, GroupState.COMPLETING_REBALANCE);

                                Collection<GroupListing> listings =
                                        admin.listGroups(
                                                        new ListGroupsOptions()
                                                                .withTypes(
                                                                        Set.of(GroupType.CONSUMER)))
                                                .all()
                                                .get();
                                assertThat(listings)
                                        .filteredOn(group -> group.groupId().startsWith("payment-"))
                                        .isNotEmpty()
                                        .allSatisfy(
                                                group -> {
                                                    assertThat(group.type())
                                                            .hasValue(GroupType.CONSUMER);
                                                });
                            });
        }
    }
}
