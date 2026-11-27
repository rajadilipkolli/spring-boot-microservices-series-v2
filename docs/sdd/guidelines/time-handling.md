# Time and Timezone Handling Guidelines

## Overview
This guideline defines the repository-wide time and timezone policy for the Spring Boot Microservices architecture. It ensures consistency across database persistence, backend processing, inter-service API exchange, asynchronous eventing, and browser presentation.

---

## Architecture Principles

### 1. Database Persistence (`timestamptz`)
- All database timestamp columns must use `timestamptz` (PostgreSQL `TIMESTAMP WITH TIME ZONE`).
- Hibernate is configured with `hibernate.jdbc.time_zone: UTC` across all services, ensuring all JDBC timestamp writes and reads are consistently mapped against UTC.
- Liquibase changesets define timestamp columns as `timestamptz`.

### 2. Application Backend Clocks & Types
- Entities and domain models use `java.time.Instant` for absolute points in time (such as `createdDate` and `lastModifiedDate`).
- JPA Auditing uses a registered `DateTimeProvider` bean that emits `Instant.now()`.
- Services operating with `OffsetDateTime` (e.g., outbox events, reconciliation leasers) must explicitly specify `ZoneOffset.UTC` (e.g. `OffsetDateTime.now(ZoneOffset.UTC)`) rather than relying on the JVM default timezone.

### 3. API Wire Format
- REST API responses emit ISO-8601 strings in UTC with a `Z` suffix (e.g., `"2026-08-09T11:01:00.157888Z"`).
- Spring Boot's default serialization (`WRITE_DATES_AS_TIMESTAMPS=false`) ensures ISO string output rather than numeric timestamp arrays or epochs.

### 4. Consumer Compatibility & Legacy Toleration
- Downstream consumers (`payment-service`, `retail-store-webapp`) implement lenient deserialization via `LenientInstantDeserializer`:
  - **UTC with 'Z'**: `"2026-08-09T11:01:00.157888Z"` $\to$ parsed as `Instant`.
  - **Numeric Offsets**: `"2026-08-09T16:31:00.157888+05:30"` $\to$ parsed to the exact corresponding UTC `Instant`.
  - **Offset-Free Legacy ISO**: `"2026-08-09T11:01:00.157888"` $\to$ interpreted as UTC without modification (`toInstant(ZoneOffset.UTC)`).
  - **Null/Blank**: deserializes to `null`.
  - **Malformed Input**: fails fast with clear deserialization exceptions (`DatabindException`).

### 5. Rollout Strategy: Consumer-First
- Consumers (`payment-service` and `retail-store-webapp`) are deployed with lenient deserializers *before* the producing backend (`order-service`) switches to emitting UTC `Z` strings. This zero-downtime rollout sequence prevents parsing failures during canary or blue-green deployments.

### 6. Browser-Local Presentation
- The web frontend receives UTC ISO instants from backend APIs.
- A global JavaScript helper (`formatDateTime(isoString)` in `/static/js/dateTime.js`) formats timestamps using the browser's local timezone via `Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'short' })`.
- The raw UTC ISO string is retained in the `title` attribute for tooltip inspection.

### 7. Schedulers and Cron Semantics
- Scheduled jobs (`@Scheduled(cron = "...")`) and ShedLock locks execute according to the local scheduler configuration or application clock.
- ShedLock database tables and Spring Modulith internal event tables operate independently under their native configurations and remain unchanged.

---

## Verification & Historical Data

### Read-Only Epoch Verification Query
Existing `timestamptz` values stored in PostgreSQL can be verified without conversion using:
```sql
SELECT id, created_date, EXTRACT(EPOCH FROM created_date)::bigint AS epoch_seconds
FROM orders
WHERE id = :orderId;
```
Because PostgreSQL stores `timestamptz` internally as UTC microseconds from epoch, no historical table rewrite or migration is required.

### No-Conversion Assumption
Historical data in existing `timestamptz` columns already represents UTC points in time; no data conversion scripts or table updates are needed for historical rows.
