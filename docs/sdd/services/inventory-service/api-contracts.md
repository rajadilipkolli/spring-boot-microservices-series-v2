# Synchronous API Contracts: Inventory Service

- **Service**: `inventory-service` (`INV`)
- **Document Status**: `Approved`
- **Context Path**: `/inventory-service`
- **Base Route**: `/inventory-service/api/inventory`
- **Port**: `18181`

---

## 1. Route Summary Table

| Method | Sub-Path | Swagger Operation | Success Status | Request Body | Response Body |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/` | `getAllInventories` | `200 OK` | None | `PagedResult<InventoryResponse>` |
| `GET` | `/{productCode}` | `getInventoryByProductCode` | `200 OK` / `404` | None | `InventoryResponse` |
| `GET` | `/product` | `getInventoryByProductCodes` | `200 OK` | None | `ResponseEntity<PagedResult<InventoryResponse>>` |
| `POST` | `/generate` | `updateInventoryWithRandomValue`| `200 OK` | None | `boolean` (`true`) |
| `POST` | `/` | `createInventory` | `201 Created` | `InventoryRequest` | `InventoryResponse` |
| `PUT` | `/{id}` | `updateInventory` | `200 OK` / `404` | `InventoryRequest` | `ResponseEntity<InventoryResponse>` |
| `PUT` | `/product/{productCode}`| `updateInventoryByProductCode` | `200 OK` / `400` / `404`| `InventoryRequest` | `ResponseEntity<InventoryResponse>` |
| `DELETE` | `/{id}` | `deleteInventory` | `200 OK` / `410` | None | `ResponseEntity<Void>` |

---

## 2. Detailed Route Contracts

### 2.1 Route: `GET /api/inventory`
- **Description**: Returns active inventory paginated using zero-based query parameters.
- **Parameters**:
  - `pageNo` (Query, `int`, optional, default: `"0"`): Zero-based page index.
  - `pageSize` (Query, `int`, optional, default: `"10"`): Maximum records per page.
  - `sortBy` (Query, `string`, optional, default: `"id"`): Entity field to sort by.
  - `sortDir` (Query, `string`, optional, default: `"asc"`): Case-insensitive. Value `"asc"` sorts ascending; **any other value sorts descending**.
- **Response `200 OK`**:
  ```json
  {
    "data": [
      {
        "id": "1",
        "productCode": "P001",
        "availableQuantity": 50000,
        "reservedItems": 5
      }
    ],
    "totalElements": 5,
    "pageNumber": 1,
    "totalPages": 1,
    "isFirst": true,
    "isLast": true,
    "hasNext": false,
    "hasPrevious": false
  }
  ```
- **Error Response `500 Internal Server Error`** (*Inferred / Untested*): If `sortBy` specifies an invalid property, jOOQ reflection throws `InvalidDataAccessApiUsageException`, resulting in a generic 500 `ProblemDetail`.

---

### 2.2 Route: `GET /api/inventory/{productCode}`
- **Description**: Returns active inventory for a specific product code.
- **Parameters**:
  - `productCode` (Path, `String`, required): Product code.
  - `delay` (Query, `Integer`, optional): Pause in seconds before lookup (`TimeUnit.SECONDS.sleep(delay)`). Unbounded.
- **Responses**:
  - `200 OK`: `InventoryResponse` JSON.
  - `404 Not Found`: Empty body.

---

### 2.3 Route: `GET /api/inventory/product`
- **Description**: Bulk lookup matching a list of product codes. Missing or soft-deleted codes are omitted.
- **Parameters**:
  - `codes` (Query, `List<String>`, required): Array/list of product codes.
  - `pageNo`, `pageSize`, `sortBy`, `sortDir` (Query, optional): Standard pagination defaults.
- **Responses**:
  - `200 OK`: `PagedResult<InventoryResponse>`.

---

### 2.4 Route: `POST /api/inventory/generate`
- **Description**: Triggers batch generation of random stock levels (1–10,000) for `ProductCode_<idempotencyKey>_<index>`.
- **Headers**:
  - `Idempotency-Key` (Header, `String`, required): Non-null idempotency key.
- **Parameters**:
  - `batchSize` (Query, `Integer`, optional, `@Min(1) @Max(10000)`): Number of records to visit. Default: `101`.
- **Responses**:
  - `200 OK`: Boolean `true`.
  - `400 Bad Request`: If `batchSize` is out of bounds or missing `Idempotency-Key` header.

---

### 2.5 Route: `POST /api/inventory`
- **Description**: Creates a new inventory record or restores a soft-deleted record.
- **Request Body**:
  ```json
  {
    "productCode": "P100",
    "availableQuantity": 150
  }
  ```
- **Responses**:
  - `201 Created`: `InventoryResponse` JSON.
  - `400 Bad Request`: Validation failure (blank code or negative quantity) returning RFC 7807 `ProblemDetail`.
  - `409 Conflict`: If product code is already active in database, returning RFC 7807 `ProblemDetail`.

---

### 2.6 Route: `PUT /api/inventory/{id}`
- **Description**: Updates available quantity of active inventory by database TSID ID.
- **Parameters**: `id` (Path, `Long`, required).
- **Request Body**: `InventoryRequest` JSON.
- **Responses**:
  - `200 OK`: `InventoryResponse` JSON.
  - `400 Bad Request`: Invalid request JSON.
  - `404 Not Found`: Empty body if ID is absent or soft-deleted.

---

### 2.7 Route: `PUT /api/inventory/product/{productCode}`
- **Description**: Updates available quantity of active inventory by product code.
- **Parameters**: `productCode` (Path, `String`, required).
- **Request Body**: `InventoryRequest` JSON.
- **Responses**:
  - `200 OK`: `InventoryResponse` JSON.
  - `400 Bad Request`: Empty body if `inventoryRequest.productCode() != null` and does not equal `{productCode}`.
  - `404 Not Found`: Empty body if product code is absent or soft-deleted.

---

### 2.8 Route: `DELETE /api/inventory/{id}`
- **Description**: Soft-deletes inventory by ID (`deleted = true`).
- **Parameters**: `id` (Path, `Long`, required).
- **Responses**:
  - `200 OK`: Empty body if active record was found and soft-deleted.
  - `410 Gone`: Empty body if record was absent or already soft-deleted.

---

## 3. Global Exception Handling and ProblemDetail Enrichment

`GlobalExceptionHandler` translates exceptions into RFC 7807 `ProblemDetail` payloads:
- `MethodArgumentNotValidException`: HTTP 400, title `"Constraint Violation"`, type `https://api.microservices.com/errors/validation-error`, `violations` list.
- `ConstraintViolationException`: HTTP 400, title `"Constraint Violation"`.
- `HttpMessageNotReadableException`: HTTP 400, title `"Bad Request"`, property `"error": "Invalid JSON format"`.
- `MissingServletRequestParameterException`: HTTP 400, title `"Bad Request"`.
- `ProductAlreadyExistsException`: HTTP 409, type `https://api.microservices.com/errors/already-exists`.
- `Exception`: HTTP 500, title `"Internal Server Error"`.

### Enrichment Rules:
- `ProblemDetailTraceIdEnricher` automatically inspects responses containing a `ProblemDetail` body, adding `traceId` to properties and `X-Trace-Id` to response headers.
- **Empty-Body Responses Excluded**: Controller endpoints returning empty-body `ResponseEntity.notFound().build()` (`404`), `ResponseEntity.badRequest().build()` (`400`), or `ResponseEntity.status(HttpStatus.GONE).build()` (`410`) return **no body** and therefore bypass `ProblemDetailTraceIdEnricher`.

---

## 4. Swagger Annotations vs Implementation Differences

| Route / Method | Swagger Annotation Claim | Observed Controller Reality |
| :--- | :--- | :--- |
| `POST /api/inventory` | Swagger defines response type on method | Actual controller uses `@ResponseStatus(HttpStatus.CREATED)` and returns `InventoryResponse` directly. |
| `PUT /api/inventory/product/{code}` | Swagger documents 400 with `ProblemDetail` schema | Controller returns empty `ResponseEntity.badRequest().build()` on path mismatch without `ProblemDetail`. |
| `DELETE /api/inventory/{id}` | Swagger documents 200 with empty body, 410 empty body | Accurately matches controller `ResponseEntity.ok().build()` and `ResponseEntity.status(HttpStatus.GONE).build()`. |
| `GET /api/inventory` | Swagger specifies `pageNo` (0-based) | Controller reads 0-based `pageNo`, but `PagedResult` returns 1-based `pageNumber`. |

Trace enrichment requires a non-blank trace ID: preserve an existing non-blank string `traceId` property; otherwise resolve the request `X-Trace-Id`, valid OpenTelemetry span, then MDC `traceId`/`trace_id`. Preserve other properties and existing response headers, adding `X-Trace-Id` only if absent. Without a resolved ID, add neither property nor header. Empty bodies are not enriched.
