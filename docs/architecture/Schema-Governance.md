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
