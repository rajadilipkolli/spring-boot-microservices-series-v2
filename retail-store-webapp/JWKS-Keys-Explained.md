# 🔑 JWKS Keys Deep Dive

## 📖 Overview

JWKS (JSON Web Key Set) are cryptographic keys used to verify JWT token signatures. This document explains how they operate within the RetailStore architecture built on **Java 25** and **Spring Boot 4.1.2**, using **Keycloak 26.x** as the OAuth2 / OpenID Connect Identity Provider.

---

## 🎯 What are JWKS Keys?

### 🔍 **Definition**
**JWKS** = **JSON Web Key Set** - A set of public keys used to verify JWT signatures.

```mermaid
%%{init: {'theme':'base', 'themeVariables': {'primaryColor': '#ffffff', 'primaryTextColor': '#000000', 'primaryBorderColor': '#000000', 'lineColor': '#000000', 'secondaryColor': '#f8f9fa', 'tertiaryColor': '#ffffff', 'background': '#ffffff', 'mainBkg': '#ffffff', 'secondBkg': '#f8f9fa', 'tertiaryBkg': '#ffffff'}}}%%
flowchart TD
    A["🔐 Keycloak (Port 9191)"] --> B["🔑 Private Key (RS256)"]
    A --> C["🔓 Public Key"]
    
    B --> D["📝 Signs JWT Tokens (Access & ID Tokens)"]
    C --> E["📋 JWKS Endpoint (/certs)"]
    
    E --> F["🛒 retailstore-webapp (Port 8080)"]
    E --> G["📚 catalog-service (Port 18080)"]
    E --> H["🛍️ order-service (Port 18282)"]
    E --> I["💳 payment-service (Port 18085)"]
    E --> J["🌐 api-gateway (Port 8765)"]
    
    F --> K["✅ Verify JWT Signatures & Map Roles"]
    G --> K
    H --> K
    I --> K
    J --> K
```

### 🔍 **Key Concepts**

1. **Asymmetric Cryptography**: 
   - Keycloak uses its **private key** to sign JWT tokens.
   - Applications and resource services use Keycloak's **public key** to verify token authenticity and integrity.

2. **JWKS Endpoint**: 
   - Public keys are exposed at: `http://localhost:9191/realms/retailstore/protocol/openid-connect/certs`

3. **Automatic Key Rotation & Discovery**: 
   - OpenID configuration is auto-discovered via: `http://localhost:9191/realms/retailstore/.well-known/openid-configuration`
   - Services cache public keys locally and auto-refresh them upon rotation.

---

## 🎯 Are JWKS Keys Used in RetailStore Project?

### ✅ **YES - Extensively Used**

```mermaid
%%{init: {'theme':'dark', 'themeVariables': {'primaryColor': '#333333', 'primaryTextColor': '#ffffff', 'primaryBorderColor': '#ffffff', 'lineColor': '#ffffff', 'secondaryColor': '#444444', 'tertiaryColor': '#555555', 'background': '#000000', 'mainBkg': '#333333', 'secondBkg': '#444444', 'tertiaryBkg': '#555555', 'noteBkgColor': '#444444', 'noteTextColor': '#ffffff'}}}%%
sequenceDiagram
    participant KC as 🔐 Keycloak (Port 9191)
    participant WebApp as 🛒 retailstore-webapp (Port 8080)
    participant Service as 🔧 Resource Server
    participant JWKS as 🔑 JWKS Endpoint (/certs)

    Note over KC, Service: JWT Signature Verification Flow (Java 25 / Spring Boot 4.1.2)

    KC->>KC: Sign Token with Private Key (RS256)
    KC-->>WebApp: Returns Access Token + ID Token
    WebApp->>JWKS: GET /realms/retailstore/protocol/openid-connect/certs
    JWKS-->>WebApp: Returns Public Keys (Cached)
    WebApp->>WebApp: Verify Signature, Extract azp & Map Roles (ADMIN, OPERATOR)
    
    WebApp->>Service: Forward Bearer JWT via RestClient
    Service->>JWKS: GET /certs (discovers via issuer-uri)
    JWKS-->>Service: Returns Public Keys (Cached)
    Service->>Service: Validate Signature & Claims
    alt Signature Valid
        Service-->>WebApp: 200 OK (Process Request)
    else Signature Invalid / Expired
        Service-->>WebApp: 401 Unauthorized
    end
```

---

## 🔍 **JWKS Implementation in RetailStore**

### 1. **Keycloak JWKS Endpoint**

**URL**: `http://localhost:9191/realms/retailstore/protocol/openid-connect/certs`

**Response Example**:
```json
{
  "keys": [
    {
      "kid": "rsa-generated-key-id",
      "kty": "RSA",
      "alg": "RS256",
      "use": "sig",
      "n": "l5AvYbXZoa-dt3HKr_wa2QQaV1lraVEbrhKcKaD4mFz3Kau47XLKLBXBIkMhD9LKCx0Jyu7tZy1gLzXKx7m5ygwtD_jvOrnFZ3PYiDCrgHO83NhxYTzKdBSiupWt7X7Ss0keclonGE7L01ZS5ytU15IuofqxrYEO2nbp1mlpUAzjy2ZIX-IEAA8VCHK5dvlmZgiZYic6N5-WkHGqu5DgKkVPyT-h3jhzYpshwmqXW-KtgKrQ2J6PrS_K2apBBq3HHM4s5X__QgEP8ehcTr2eEkqwuaRuVPgjaAdqkAw6VTInE9hiZRhHPHDKvQsA2ze3JTucm-fA9Ng4yvPTxLPoww",
      "e": "AQAB",
      "x5c": ["MIICoTCCAYkCBgGPDlWStjANBgkqhkiG9w0BAQsFADAUMRIwEAYDVQQDDAlib29rc3RvcmUwHhcNMjQwNDI0MDQxOTAwWhcNMzQwNDI0MDQyMDQwWjAUMRIwEAYDVQQDDAlib29rc3RvcmUwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQCXkC9htdmhr523ccqv_BrZBBpXWWtpURuuEpwpoPiYXPcpq7jtcsosFcEiQyEP0soLHQnK7u1nLWAvNcrHubnKDC0P-O86ucVnc9iIMKuAc7zc2HFhPMp0FKK6la3tftKzSR5yWicYTsvTVlLnK1TXki6h-rGtgQ7adunWaWlQDOPLZkhf4gQADxUIcrl2-WZmCJliJzo3n5aQcaq7kOAqRU_JP6HeOHNimyHCapdb4q2AqtDYno-tL8rZqkEGrccczizlf_9CAQ_x6FxOvZ4SSrC5pG5U-CNoB2qQDDpVMicT2GJlGEc8cMq9CwDbN7clO5yb58D02DjK89PEs-jDAgMBAAEwDQYJKoZIhvcNAQELBQADggEBAImZccBZzONa1A8X_2yK7KBZH_R3yFOjnDDjb3w2ewF_R63UFiqg--E0NLXNZ8RRSLUVmP81eaoxB6jbyOEvOUDb2CERQSHZ_CMA_DMahPSURKfbG90FlGqH_wuDUyExI-gKoCql30-4pZGx9f0ltAjzv7s8kd8ED_zXmQQ-cRJ7lSx3EP-kHXwfN0_5uvn-8z6JMz2uukaZ3bgwWKEhB3Fq3qAchNzdsg_QWh-H4RCFAuNOqZ_w5osuQwMnNv6JAhfs9kq-fOFQfet-kbSS2eD9KnSwj_M7xkzTCPTCcKtENU52Sa8ss10BNn4EdDfFcI4Pp4LEtdSzbFeq0JVqhUQ"]
    }
  ]
}
```

### 2. **Spring Security Auto-Configuration (Spring Boot 4.1.2 & Java 25)**

**In `retailstore-webapp` (OAuth2 Client)**:
```properties
# retail-store-webapp/src/main/resources/application.properties
OAUTH2_SERVER_URL=http://localhost:9191
REALM_URL=${OAUTH2_SERVER_URL}/realms/retailstore

spring.security.oauth2.client.registration.retailstore-webapp.client-id=retailstore-webapp
spring.security.oauth2.client.registration.retailstore-webapp.client-secret=${OAUTH2_CLIENT_SECRET:demo-throwaway-oauth-secret}
spring.security.oauth2.client.registration.retailstore-webapp.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.retailstore-webapp.scope[0]=openid
spring.security.oauth2.client.registration.retailstore-webapp.scope[1]=profile
spring.security.oauth2.client.registration.retailstore-webapp.scope[2]=roles

spring.security.oauth2.client.provider.retailstore-webapp.issuer-uri=${REALM_URL}
```

**In backend services (Resource Servers)**:
```properties
# backend services (order-service, catalog-service, etc.)
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:9191/realms/retailstore
```

**What happens automatically**:
1. Spring Security calls `/.well-known/openid-configuration` on `http://localhost:9191/realms/retailstore`
2. Discovers JWKS endpoint: `/protocol/openid-connect/certs`
3. Fetches public keys and caches them
4. Uses public keys to verify JWT signatures on incoming/outgoing tokens

### 3. **JWT Header with Key ID (`kid`)**

**JWT Header includes `kid` (Key ID)**:
```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "rsa-generated-key-id"
}
```

**Verification Process**:
1. Service extracts `kid` from JWT header
2. Finds matching key in cached JWKS
3. Uses public key to verify signature

### 4. **Roles Claim in Verified JWT**

When the `roles` scope is requested, Keycloak includes both realm roles and client roles:
```json
{
  "iss": "http://localhost:9191/realms/retailstore",
  "aud": "retailstore-webapp",
  "sub": "user-uuid",
  "preferred_username": "retail",
  "realm_access": {
    "roles": ["default-roles-retailstore", "user"]
  },
  "resource_access": {
    "retailstore-webapp": {
      "roles": ["ADMIN", "OPERATOR"]
    }
  }
}
```

Spring Security in `retail-store-webapp` maps these claims into `GrantedAuthority`:
- `ADMIN` -> `ROLE_ADMIN` (guards `/inventory` and `/api/products` creation)
- `OPERATOR` -> `ROLE_OPERATOR` (guards `/actuator/prometheus` and `/actuator/metrics`)

---

## 🔍 **Code Implementation**

### 1. **Automatic JWKS Configuration**

```java
// Spring Security internally creates NimbusJwtDecoder from issuer-uri:
@Bean
public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder
        .withJwkSetUri("http://localhost:9191/realms/retailstore/protocol/openid-connect/certs")
        .cache(Duration.ofMinutes(5)) // Cache JWKS for 5 minutes
        .build();
}
```

### 2. **Role Mapping in `retailstore-webapp` (SecurityConfig.java)**

```java
@Bean
GrantedAuthoritiesMapper userAuthoritiesMapper() {
    return authorities -> {
        Set<GrantedAuthority> mappedAuthorities = new HashSet<>();
        authorities.forEach(authority -> {
            if (authority instanceof OidcUserAuthority oidcAuth) {
                Map<String, Object> resourceAccess = oidcAuth.getIdToken().getClaimAsMap("resource_access");
                if (resourceAccess == null && oidcAuth.getUserInfo() != null) {
                    resourceAccess = oidcAuth.getUserInfo().getClaimAsMap("resource_access");
                }
                if (resourceAccess != null) {
                    String clientId = oidcAuth.getIdToken().getClaimAsString("azp");
                    if (clientId == null) {
                        clientId = "retailstore-webapp";
                    }
                    Object clientAccess = resourceAccess.get(clientId);
                    if (clientAccess instanceof Map<?, ?> client) {
                        Object roles = client.get("roles");
                        if (roles instanceof Collection<?> roleCollection) {
                            roleCollection.stream()
                                    .filter(String.class::isInstance)
                                    .map(String.class::cast)
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                    .forEach(mappedAuthorities::add);
                        }
                    }
                }
            }
            mappedAuthorities.add(authority);
        });
        return mappedAuthorities;
    };
}
```

### 3. **JWT Validation in Services**

```java
// Example: order-service JWT validation
@RestController
public class OrderController {
    
    @GetMapping("/api/orders")
    public List<Order> getUserOrders(JwtAuthenticationToken jwt) {
        // JWT signature already verified by Spring Security using JWKS
        String username = jwt.getToken().getClaimAsString("preferred_username");
        return orderService.findByUsername(username);
    }
}
```

### 4. **Manual JWKS Validation (if needed)**

```java
@Service
public class JwtValidationService {
    
    private final JwtDecoder jwtDecoder;
    
    public boolean validateToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            
            // Signature automatically verified using JWKS
            // Additional validation
            return jwt.getExpiresAt().isAfter(Instant.now()) &&
                   "http://localhost:9191/realms/retailstore".equals(jwt.getIssuer().toString());
                   
        } catch (JwtException e) {
            return false; // Invalid signature or expired
        }
    }
}
```

---

## 🔍 **JWKS in Keycloak Realm Configuration**

### **From `retailstore-realm.json`**:

```json
{
  "components": {
    "org.keycloak.keys.KeyProvider": [
      {
        "name": "rsa-generated",
        "providerId": "rsa-generated",
        "config": {
          "privateKey": ["MIIEogIBAAKCAQEAl5AvYbXZoa..."], // Hidden in production
          "certificate": ["MIICoTCCAYkCBgGPDlWStjANBg..."],
          "keyUse": ["SIG"],
          "priority": ["100"],
          "algorithm": ["RS256"]
        }
      }
    ]
  }
}
```

**Key Points**:
- **Private Key**: Stored securely in Keycloak (never exposed)
- **Public Key**: Exposed via JWKS endpoint
- **Algorithm**: RS256 (RSA with SHA-256)
- **Key Rotation**: Keycloak can generate new keys automatically

---

## 🔍 **JWKS Caching & Performance**

### **Caching Strategy**:

```java
// Spring Security default JWKS caching
@Bean
public JwtDecoder jwtDecoder() {
    return NimbusJwtDecoder
        .withJwkSetUri("http://localhost:9191/realms/retailstore/protocol/openid-connect/certs")
        .cache(Duration.ofMinutes(5))     // Cache keys for 5 minutes
        .livenessTimeout(Duration.ofMinutes(5))  // Refresh timeout
        .build();
}
```

### **Performance Benefits**:
1. **Reduced Network Calls**: Keys cached locally
2. **Fast Verification**: No need to call Keycloak for each request
3. **Automatic Refresh**: Keys updated when cache expires
4. **Fault Tolerance**: Cached keys work even if Keycloak is temporarily down

---

## 🔍 **Security Benefits**

### **Why JWKS is Secure**:

1. **Asymmetric Cryptography**: 
   - Private key never leaves Keycloak
   - Public key can be safely distributed

2. **Signature Verification**:
   - Ensures JWT wasn't tampered with
   - Validates token came from trusted issuer

3. **Key Rotation**:
   - Keycloak can rotate keys for security
   - Old signatures become invalid automatically

4. **Stateless Verification**:
   - No need to store tokens in database
   - Services verify independently

---

## 📋 **Summary**

### **JWKS Usage in RetailStore**:

✅ **Used Everywhere**: All services use JWKS for JWT verification
✅ **Automatic Configuration**: Spring Security handles JWKS automatically  
✅ **Cached Performance**: Keys cached for 5 minutes
✅ **Secure**: Private keys never exposed, only public keys
✅ **Scalable**: Stateless verification across all microservices

### **Key Endpoints**:
- **JWKS**: `http://localhost:9191/realms/retailstore/protocol/openid-connect/certs`
- **Discovery**: `http://localhost:9191/realms/retailstore/.well-known/openid-configuration`

### **Architecture Flow**:
```
Keycloak (Private Key) → Signs JWT → Services fetch JWKS (Public Keys) → Verify JWT Signatures
```

JWKS is a critical security component that enables stateless, scalable JWT verification across the entire RetailStore microservices architecture.