/***
 * <p>
 * Licensed under MIT License Copyright (c) 2026 Raja Kolli.
 * </p>
 ***/

package com.example.common.kafka.dlt;

import java.util.concurrent.CompletableFuture;

/** Contract interface for replaying or re-submitting DLT records back to primary topics. */
@FunctionalInterface
public interface DltReprocessor {

    /**
     * Resubmits or replays the given DLT record payload back to its original or designated topic.
     *
     * @param payload the dead-letter payload to reprocess
     * @return CompletableFuture completing when reprocessing/resubmission is finished
     */
    CompletableFuture<Void> reprocess(DltRecordPayload payload);
}
