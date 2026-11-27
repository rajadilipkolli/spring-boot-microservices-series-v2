# Domain Model Specification

- **Service**: `<service-name>` (`<SVC>`)
- **Document Status**: `Draft` | `Approved`

---

## 1. Ubiquitous Language Glossary *(Mandatory)*

| Term | Definition | Context / Scope |
| :--- | :--- | :--- |
| **Example Aggregate** | The primary domain entity governing lifecycle and invariants. | Core Domain |
| **Example Value Object** | An immutable attribute representing a domain concept without identity. | Domain Model |

---

## 2. Domain Entities & Aggregate Roots *(Mandatory)*

### 2.1 Entity: `[EntityName]`
- **Class Reference**: `<repo-relative-path>#<EntityName>`
- **Database Table**: `[table_name]`
- **Primary Key**: `[id_column]` (Generation Strategy: TSID / Sequence / Identity)
- **Concurrency Control**: Optimistic locking via `@Version`
- **Soft Deletion**: Supported via `deleted` boolean flag (or Physical Deletion)

#### Attributes and Field Definitions
| Field | Type | Nullable | Mutable | Description |
| :--- | :--- | :--- | :--- | :--- |
| `id` | `Long` | No | No | Unique entity identifier (e.g., TSID). |
| `code` | `String` | No | No | Unique natural business identifier. |
| `quantity` | `Integer` | Yes | Yes | Current available stock/quantity. |
| `version` | `Short` / `Long` | No | Yes | Optimistic lock version counter. |

---

## 3. Data Transfer Objects (DTOs) *(Mandatory)*

### 3.1 DTO: `[RequestDtoName]`
- **Class Reference**: `<repo-relative-path>#<RequestDtoName>`
- **Usage**: Inbound REST request payload.
- **Validation Constraints**: Jakarta Validation (`@NotBlank`, `@PositiveOrZero`, etc.)

| Field | Type | Constraints | Description |
| :--- | :--- | :--- | :--- |
| `productCode` | `String` | `@NotBlank` | Unique code identifying the item. |
| `quantity` | `Integer` | `@PositiveOrZero` | Requested quantity value. |

### 3.2 DTO: `[ResponseDtoName]`
- **Class Reference**: `<repo-relative-path>#<ResponseDtoName>`
- **Usage**: Outbound REST response payload.

| Field | Type | Serialization Details | Description |
| :--- | :--- | :--- | :--- |
| `id` | `Long` | Formatted as String (`@JsonFormat`) | Unique record identifier. |
| `productCode` | `String` | Default string | Item business code. |

---

## 4. Domain Invariants and Integrity Rules *(Mandatory)*
- [State invariant 1: e.g., Quantities cannot be reduced below zero via normal business operations]
- [State invariant 2: e.g., Soft-deleted items must be excluded from public query results]
- [State invariant 3: e.g., Concurrency conflicts must throw optimistic locking exceptions]
