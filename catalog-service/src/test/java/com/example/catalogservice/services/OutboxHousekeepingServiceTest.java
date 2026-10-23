/***
<p>
    Licensed under MIT License Copyright (c) 2026 Raja Kolli.
</p>
***/

package com.example.catalogservice.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.example.catalogservice.entities.OutboxEventStatus;
import com.example.catalogservice.repositories.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;

class OutboxHousekeepingServiceTest {
    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(OutboxEventRepository.class, () -> repository)
                    .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                    .withUserConfiguration(OutboxHousekeepingService.class);

    @ParameterizedTest
    @ValueSource(
            strings = {
                "application.outbox.retention-days",
                "application.outbox.failed-retention-days"
            })
    void negativeRetentionPeriodFailsStartupBeforeCleanup(String property) {
        contextRunner
                .withPropertyValues(property + "=-1")
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(context.getStartupFailure())
                                    .hasRootCauseInstanceOf(IllegalArgumentException.class)
                                    .hasStackTraceContaining(
                                            property + " must be zero or positive");
                            verifyNoInteractions(repository);
                        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 30})
    void nonNegativeFailedRetentionStartsAndUsesFailureCutoff(int days) {
        contextRunner
                .withPropertyValues("application.outbox.failed-retention-days=" + days)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            when(repository.deleteAllByStatusAndProcessedAtBefore(any(), any()))
                                    .thenReturn(Mono.just(1));
                            var before = OffsetDateTime.now().minusDays(days);
                            context.getBean(OutboxHousekeepingService.class).cleanupFailedEvents();
                            var after = OffsetDateTime.now().minusDays(days);
                            var cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
                            verify(repository)
                                    .deleteAllByStatusAndProcessedAtBefore(
                                            eq(OutboxEventStatus.FAILED), cutoff.capture());
                            assertThat(cutoff.getValue()).isBetween(before, after);
                        });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void nonNegativeRetentionStartsAndUsesPastCutoff(int days) {
        contextRunner
                .withPropertyValues("application.outbox.retention-days=" + days)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            when(repository.deleteAllByStatusAndCreatedAtBefore(any(), any()))
                                    .thenReturn(Mono.just(1));
                            var before = OffsetDateTime.now().minusDays(days);
                            context.getBean(OutboxHousekeepingService.class)
                                    .cleanupPublishedEvents();
                            var after = OffsetDateTime.now().minusDays(days);
                            var cutoff = ArgumentCaptor.forClass(OffsetDateTime.class);
                            verify(repository)
                                    .deleteAllByStatusAndCreatedAtBefore(
                                            eq(OutboxEventStatus.PUBLISHED), cutoff.capture());
                            assertThat(cutoff.getValue()).isBetween(before, after);
                        });
    }
}
