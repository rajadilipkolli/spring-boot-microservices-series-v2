/***
<p>
    Licensed under MIT License Copyright (c) 2021-2025 Raja Kolli.
</p>
***/

package com.example.inventoryservice.repositories;

import com.example.inventoryservice.entities.ProcessedKafkaMessage;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedKafkaMessageRepository
        extends JpaRepository<ProcessedKafkaMessage, UUID> {

    boolean existsByMessageKeyAndTopicAndConsumerGroup(
            String messageKey, String topic, String consumerGroup);
}
