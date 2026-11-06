# API Contracts Specification

- **Service**: `<service-name>` (`<SVC>`)
- **Document Status**: `Draft` | `Approved`
- **Context Path**: `/<service-name>`
- **API Base Route**: `/<service-name>/api/...`

---

## 1. API Route Index *(Mandatory)*

| Method | Path | Summary | Success Status | Auth / Security | Compatibility |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/resource` | List resources paginated | `200 OK` | Public / None | `[Compatible]` |
| `POST` | `/api/resource` | Create a new resource | `201 Created` | Public / None | `[Compatible]` |

---

## 2. Endpoint Specifications *(Mandatory)*

### 2.1 Route: `GET /api/resource`
- **Operation ID / Swagger Method**: `getAllResources`
- **Interface Reference**: `<repo-relative-path>#<SwaggerApiInterface>#<methodName>`
- **Controller Reference**: `<repo-relative-path>#<ControllerClass>#<methodName>`

#### Request Parameters
| Name | In | Type | Required | Default | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `pageNo` | Query | `int` | No | `0` | Zero-based page index. |
| `pageSize` | Query | `int` | No | `10` | Records per page (positive integer). |
| `sortBy` | Query | `string` | No | `id` | Field name for sorting. |
| `sortDir` | Query | `string` | No | `asc` | Case-insensitive sort direction (`asc`/`desc`). |

#### Responses
| Status | Content-Type | Schema / DTO | Description |
| :--- | :--- | :--- | :--- |
| `200 OK` | `application/json` | `PagedResult<ResourceResponse>` | Paginated list of resources. |
| `500 Internal Server Error` | `application/problem+json` | `ProblemDetail` | Inferred on invalid sort field. |

---

## 3. Global Error Handling & ProblemDetail Enrichment *(Mandatory)*

### 3.1 RFC 7807 Error Response Format
All application errors emitted by `GlobalExceptionHandler` conform to RFC 7807 `application/problem+json`:
```json
{
  "type": "https://api.microservices.com/errors/validation-error",
  "title": "Constraint Violation",
  "status": 400,
  "detail": "Invalid request content.",
  "instance": "/api/resource",
  "timestamp": "2026-11-05T14:30:00Z",
  "traceId": "0af7651916cd43dd8448eb211c80319c",
  "violations": []
}
```

### 3.2 ProblemDetailTraceIdEnricher Integration
- Outbound responses containing a `ProblemDetail` body are intercepted by `ProblemDetailTraceIdEnricher`.
- Injects `traceId` property and `X-Trace-Id` response header.
- Empty-body responses (e.g., `404 Not Found`, `400 Bad Request`, `410 Gone` returned without a body) do **not** trigger enrichment.

---

## 4. Swagger / OpenAPI Contract Differences *(Mandatory)*
[Document any discrepancies between the Swagger annotations on the API interface and actual runtime controller behavior, such as missing response codes, incorrect error models, or parameter defaults.]

Trace enrichment requires a non-blank trace ID: preserve an existing non-blank string `traceId` property; otherwise resolve the request `X-Trace-Id`, valid OpenTelemetry span, then MDC `traceId`/`trace_id`. Preserve other properties and existing response headers, adding `X-Trace-Id` only if absent. Without a resolved ID, add neither property nor header. Empty bodies are not enriched.
