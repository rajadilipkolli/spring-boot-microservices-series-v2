# Dead Letter Queue (DLQ) & Retry Runbook

## 1. Retry Strategy
- **Framework**: Spring Kafka @RetryableTopic.
- **Attempts**: 3 attempts total (1 initial + 2 retries).
- **Backoff**: Exponential backoff (e.g., 1000ms, 2000ms).

## 2. DLQ Routing
- If an event fails processing after all retries (or hits a FAIL_ON_ERROR exception like deserialization failures), it is routed to the .DLT suffix topic.

## 3. Poison Message Handling
- Alerts trigger in Grafana when the DLT message rate > 0 for 5 minutes.
- **Action**: 
  1. Inspect the message payload and exception stack trace in the log via Loki.
  2. If the issue is a bug in the code, fix the code and deploy.
  3. If the payload is fundamentally malformed and cannot be processed, discard the message manually or log a defect for the producing system.

## 4. Replay Procedure
To replay messages from the DLQ back to the main topic:
1. Temporarily spin up a dedicated replay consumer script or use Kafdrop/AKHQ to move messages from 	opic.DLT to 	opic.
2. Ensure the consumers are ready for the re-injected payload.
