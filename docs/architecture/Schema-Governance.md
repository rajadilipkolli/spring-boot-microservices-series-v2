# Schema Governance Architecture

## 1. Overview
To decouple microservices and ensure robust data contracts, we mandate **Schema Governance** for all events published to Kafka.

## 2. Component: Schema Registry
We use **Apicurio Registry** deployed in the prod cluster to store, version, and serve Avro/JSON schemas.

## 3. Compatibility Policy
- **Global Policy**: BACKWARD_TRANSITIVE.
- **Meaning**: Consumers can read events produced by older versions of the schema. Producers cannot remove mandatory fields or change field types. This allows consumers to upgrade independently of producers.

## 4. Ownership & Lifecycles
- The **Producer** owns the schema.
- Schema definitions (e.g., Avro .avsc files) must be version-controlled in the producer's repository.
- Changes to schemas require a pull request review by at least one maintainer from a consuming service.

## 5. PII Classification
- Fields containing Personally Identifiable Information (PII) must be annotated with @PII (or pii: true in JSON schemas).
- The Kafka Connect or Sink processes dropping data into the Data Warehouse must automatically mask or drop these fields unless explicit consent flows are validated.
