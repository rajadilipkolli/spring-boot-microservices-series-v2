<!-- coderabbit-ai-generated:v1 -->

# Using the idempotency store

For JDBC, the consuming application owns provisioning. Include the library resource `schema-processed-messages.sql` as a SQL file in the application's Liquibase changelog (or apply it with your deployment migration tool). Run the migration against the store's data source **before starting consumers or accepting requests**. The runtime user needs SELECT, INSERT, UPDATE and DELETE access. While a row is PROCESSING its result column holds the owner token.

Annotated methods must return String (nullable) or void. A business exception releases the claim; if recording a successful result fails, only the completion write is retried. After retries are exhausted the caller receives an error and the claim remains subject to its original expiry. Reconcile the business outcome before retrying after expiry. Choose a TTL longer than the longest execution and completion retry window.

The default key format and Redis key layout have changed. Before upgrading, quiesce all old writers and wait for outstanding claims and cached results to expire. Do not run both versions against the same workload. Third-party store implementations must implement the owner-token and snapshot API.

In-memory cleanup runs every minute by default. Set `app.idempotency.cleanup-interval` to a positive duration to change it.
