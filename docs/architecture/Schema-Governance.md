# Schema Governance Architecture

## 1. Overview
Schema governance for all Kafka events is the **target state**. It is not currently enforced on the application data path.

## 2. Component: Schema Registry
Apicurio Registry is deployed in the production overlay, but payment-service, inventory-service, and catalog-service do not use Apicurio serializers/deserializers or a registry URL. Their current paths use Spring `JsonSerializer`, `StringDeserializer` plus `JsonMapper`, or pre-serialized JSON with `StringSerializer`. Those paths bypass registry lookup and compatibility enforcement. Do not treat a successful Registry deployment as proof of governed events.

To reach the target state, each producer and consumer must use the corresponding Apicurio Avro or JSON Schema serde, configure the Registry endpoint and artifact strategy, register version-controlled schemas, and pass compatibility checks in CI before enforcement is enabled.

## 3. Compatibility Policy
- **Global Policy**: BACKWARD_TRANSITIVE.
- **Meaning**: A reader using the new schema can read data written with every previous schema version. This is reader-first compatibility; it does not promise that existing readers can consume data from a producer using the new schema.
- **Avro evolution**: Under backward compatibility, a new reader may add a field only when it has a default, may omit an old writer field, and may change types only where Avro's schema-resolution promotions permit it. Renames require aliases and still need compatibility testing.
- **JSON Schema evolution**: The new schema must continue to accept every instance allowed by all previous schemas. Do not newly require an optional property, narrow types/ranges/patterns, remove allowed enum values, or reject previously allowed properties. Adding an optional property is normally backward-compatible; changes involving `additionalProperties`, conditionals, or composition keywords require compatibility tests against every prior version.
- **Other rollout orders**: Use FORWARD_TRANSITIVE when new producers must remain readable by all previous readers (producer-first rollout). Use FULL_TRANSITIVE when both reader-first and producer-first compatibility are required across all versions.

## 4. Ownership & Lifecycles
- The **Producer** owns the schema.
- Schema definitions (e.g., Avro .avsc files) must be version-controlled in the producer's repository.
- Changes to schemas require a pull request review by at least one maintainer from a consuming service.

## 5. PII Classification
- Fields containing Personally Identifiable Information (PII) must be annotated with @PII (or pii: true in JSON schemas).
- The Kafka Connect or Sink processes dropping data into the Data Warehouse must automatically mask or drop these fields unless explicit consent flows are validated.

## 6. Orders event: migrating IDs from numbers to strings

The order-service `OrderDto.orderId`, `OrderDto.customerId`, and
`OrderItemDto.itemId` are Java `Long` values serialized as decimal JSON strings.
For example, `"orderId":9007199254740993` becomes
`"orderId":"9007199254740993"`. This preserves IDs beyond JavaScript's safe
integer range. Inventory and payment use the same string output convention in
their order payloads. Topic names, field names and Kafka string keys stay the
same; quantities and prices remain numbers.

### Compatibility gate

Before rollout, run `./mvnw test -Dtest=OrderDtoTest` in order-service,
inventory-service and payment-service. The compatibility cases exercise numeric
and string input for all three ID fields, including 9007199254740993, and assert
the exact resulting Long values. Order tests use the Kafka `JacksonJsonSerde`;
inventory/payment tests use Jackson `JsonMapper`, as their listeners do.
These tests cover current readers; they do not certify older deployed binaries
or external consumers. Verify those readers with both payload forms as well.
Keep IDs as decimal strings or 64-bit integers throughout processing; converting
through a floating-point number loses precision.

### Rolling deployment

1. Inventory all readers and writers of `orders`, `payment-orders` and
   `stock-orders`, including retry/DLT replay tools, Kafka Streams state stores,
   and external consumers. Capture deployed versions and retain numeric and
   string fixtures. Registry policy alone cannot validate this migration because
   the current JSON paths bypass it.
2. First deploy and verify readers that accept both numeric and string IDs while
   writers still emit numbers. Inventory/payment also publish replies, and
   order-service consumes those replies and republishes orders, so a simple
   service ordering is insufficient. If any deployed reader rejects strings,
   prepare a compatibility release that retains numeric output in **all three**
   services and upgrade every replica before enabling string output. The current
   annotated DTOs already emit strings; they are not that compatibility release.
3. Once every reader is verified, roll out string-writing versions of all three
   services. Mixed numeric/string events must remain readable during the rollout.
   Canary an order through reservation and completion; verify exact IDs in orders
   and replies, monitor consumer lag, deserialization failures, retries and DLTs,
   and stop the rollout if any reader fails.
4. Retain dual-format readers through topic retention, outstanding outbox
   publications, retries/DLT replay and state-store/changelog restoration. Check
   replay of retained numeric events and new string events before considering any
   future removal of numeric input support.

### Rollback

Roll writers back only to a version whose readers have passed both input forms.
String events can remain in topics, outboxes, DLTs and state stores after writer
rollback; never roll readers back to numeric-only code while those events can be
replayed. If no compatible rollback exists, pause affected processing and deploy a
reader fix before resuming. Preserve offsets and event IDs; do not blindly replay
orders or reset Kafka Streams state because that can repeat business operations.
