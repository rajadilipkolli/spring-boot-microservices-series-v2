/***
<p>
    Licensed under MIT License Copyright (c) 2024-2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.kafka;

import com.example.catalogservice.config.logging.Loggable;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Kafka producer for catalog events.
 *
 * <p>Note: StringSerializer + use-native-encoding=true is deliberately chosen on the
 * inventory-out-0 binding to pre-serialize the JSON payload and avoid Kafka type-header
 * (__TypeId__) leakage. The contentType=application/json binding property is decorative under
 * native encoding.
 */
@Service
@Loggable
public class CatalogKafkaProducer {

    private final StreamBridge streamBridge;

    public CatalogKafkaProducer(StreamBridge streamBridge) {
        this.streamBridge = streamBridge;
    }

    public Mono<Boolean> send(String key, String payload) {
        return Mono.fromCallable(
                        () -> {
                            Message<String> message =
                                    MessageBuilder.withPayload(payload)
                                            .setHeader(KafkaHeaders.KEY, key)
                                            .build();
                            return streamBridge.send("inventory-out-0", message);
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
