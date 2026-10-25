/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.inventoryservice.inventory.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

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
                                        admin.describeConsumerGroups(Set.of("stock", "product"))
                                                .all()
                                                .get();
                                assertThat(descriptions).containsKeys("stock", "product");

                                ConsumerGroupDescription stockGroup = descriptions.get("stock");
                                assertThat(stockGroup.type()).isEqualTo(GroupType.CONSUMER);
                                assertThat(stockGroup.groupState())
                                        .isIn(GroupState.STABLE, GroupState.COMPLETING_REBALANCE);

                                ConsumerGroupDescription productGroup = descriptions.get("product");
                                assertThat(productGroup.type()).isEqualTo(GroupType.CONSUMER);
                                assertThat(productGroup.groupState())
                                        .isIn(GroupState.STABLE, GroupState.COMPLETING_REBALANCE);

                                Collection<GroupListing> listings =
                                        admin.listGroups(
                                                        new ListGroupsOptions()
                                                                .withTypes(
                                                                        Set.of(GroupType.CONSUMER)))
                                                .all()
                                                .get();

                                assertThat(listings)
                                        .filteredOn(group -> group.groupId().startsWith("stock-"))
                                        .isNotEmpty()
                                        .allSatisfy(
                                                group ->
                                                        assertThat(group.type())
                                                                .hasValue(GroupType.CONSUMER));

                                assertThat(listings)
                                        .filteredOn(group -> group.groupId().startsWith("product-"))
                                        .isNotEmpty()
                                        .allSatisfy(
                                                group ->
                                                        assertThat(group.type())
                                                                .hasValue(GroupType.CONSUMER));
                            });
        }
    }
}
