# Dead Letter Queue (DLQ) & Retry Runbook

## 1. Retry Strategy
- **Framework**: Spring Kafka @RetryableTopic.
- **Attempts**: 3 attempts total (1 initial + 2 retries).
- **Backoff**: Exponential backoff (e.g., 1000ms, 2000ms).

## 2. DLQ Routing
- The payment order listener routes exhausted retries and excluded permanent failures from `orders` to `orders-dlt-payment`; the inventory order listener routes exhausted retries from `orders` to `orders-dlt-inventory`. These names come from each listener's `dltTopicSuffix` and are not a generic `.DLT` convention.
- `FAIL_ON_ERROR` is not configured by these listeners. If selected as a `DltStrategy`, it controls what happens when processing a record already on a DLT fails; it does not classify an original-record deserialization failure or choose its DLT suffix.
- Both order listeners receive strings and parse JSON inside the listener with `JsonMapper`, so malformed JSON is a listener-processing failure and follows that listener's retry/DLT path. Inventory wraps its key and value `StringDeserializer` delegates in `ErrorHandlingDeserializer`, while payment uses `StringDeserializer` directly. The wrapper handles Kafka deserializer failures before listener invocation; it does not parse or validate the JSON text. Inventory's separate `productTopic` listener has no `@RetryableTopic` DLT configuration and must not be assumed to route malformed product JSON to either order DLT.

## 3. Poison Message Handling
- Alerts trigger in Grafana when the DLT message rate > 0 for 5 minutes.
- **Action**: 
  1. Inspect the message payload and exception stack trace in the log via Loki.
  2. If the issue is a bug in the code, fix the code and deploy.
  3. If the payload is fundamentally malformed and cannot be processed, discard the message manually or log a defect for the producing system.

## 4. Replay Procedure
To replay messages from the DLQ back to the main topic:
1. Select the consumer-specific source: replay `orders-dlt-payment` or `orders-dlt-inventory` to the main `orders` topic. Do not combine them; preserve the original key and headers and replay one listener's failures at a time with a dedicated consumer or an approved Kafka administration tool.
2. Ensure the consumers are ready for the re-injected payload.
3. Pause the replay if the corresponding DLT grows, consumer lag does not recover, or the same exception recurs; retain offsets and evidence so replay can resume without duplicating the entire batch.
