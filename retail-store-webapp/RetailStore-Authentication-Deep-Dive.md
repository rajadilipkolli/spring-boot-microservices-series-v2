# 🔐 RetailStore WebApp - Authentication & Registration Deep Dive

## 📖 Overview

This document provides an in-depth analysis of the authentication and registration flows in the RetailStore WebApp built on **Java 25** and **Spring Boot 4.1.2**, mapping each sequence step to specific code components, configurations, and Keycloak integration points.

## 🏗️ Architecture Components

### Core Security Stack
- **Java 25**
- **Spring Boot 4.1.2** with **Spring Security** (OAuth2 Client & OIDC support)
- **Keycloak 26.x** as Identity Provider (Port `9191`)
- **RetailStore WebApp** (Port `8080`)
- **Keycloak Admin Client** (`keycloak-admin-client:26.0.12`) for user management
- **JWT Tokens** for stateless authorization
- **CSRF Protection** with cookie-based tokens (`CookieCsrfTokenRepository.withHttpOnlyFalse()`)
- **Role-based Access Control** (RBAC):
  - Client Roles on `retailstore-webapp`: `ADMIN` and `OPERATOR`
  - Realm Role: `user` (assigned on registration)

---

## 🔑 User Registration Flow - Recovery Design

**Current source limitation:** `RegistrationController.register()` creates the Keycloak user before calling the payment service. If customer creation fails, the user remains in Keycloak and a repeated `/api/register` request fails with `UserAlreadyExistsException` before reaching the customer call. Despite its name, the current `CustomerServiceClient.getOrCreateCustomer()` performs a create-only POST; an existing email returns HTTP 409.

The flow below is a proposed recovery design. New users retain the create-user, assign-role, and create-customer path. An existing user must sign in before resuming customer creation; a public duplicate-user response alone is not proof of ownership. The authenticated continuation route and idempotent customer helper described below still need implementation.

```mermaid
sequenceDiagram
    participant User as 👤 User
    participant Browser as 🌐 Browser
    participant WebApp as 🛒 RetailStore WebApp (Port 8080)
    participant KC as 🔐 Keycloak (Port 9191)
    participant Gateway as 🌐 API Gateway (Port 8765)
    participant Payment as 💳 Payment Service (Port 18085)

    Note over User, Payment: 📝 Registration Flow with Code References

    User->>Browser: Navigate to /registration
    Note right of Browser: URL: http://localhost:8080/registration
    
    Browser->>WebApp: GET /registration
    Note right of WebApp: RegistrationController.showRegistrationPage()<br/>File: RegistrationController.java<br/>Returns: "registration" template
    
    WebApp-->>Browser: Registration Form (Thymeleaf)
    Note right of Browser: Template: registration.html<br/>Form fields: username, email, firstName,<br/>lastName, password, phone, address
    
    User->>Browser: Fill form & click Register
    Note right of Browser: JavaScript validation:<br/>- Email format<br/>- Password complexity<br/>- Password confirmation match
    
    Browser->>WebApp: POST /api/register (JSON)
    Note right of WebApp: RegistrationController.register()<br/>File: RegistrationController.java<br/>@Valid @RequestBody RegistrationRequest
    
    WebApp->>WebApp: Validate Request (Bean Validation)
    
    WebApp->>KC: Create user via keycloak-admin-client
    alt New Keycloak user (201 Created)
        KC-->>WebApp: User ID
        WebApp->>KC: Assign "user" realm role
        KC-->>WebApp: Role assigned
    else Existing user (409 Conflict)
        KC-->>WebApp: Duplicate username or email
        WebApp-->>Browser: Sign in to resume customer setup
        Browser->>KC: Authenticate existing account (OIDC)
        KC-->>WebApp: Verified identity through OIDC login
        Browser->>WebApp: POST /api/registration/customer (authenticated, CSRF protected)
        WebApp->>WebApp: Derive identity from principal and require verified email
    end

    WebApp->>Gateway: Ensure customer by normalized email
    Note right of WebApp: Proposed idempotent helper:<br/>GET by email, POST only if missing,<br/>on duplicate-email conflict, GET again
    Gateway->>Payment: Lookup or create customer record
    Payment-->>Gateway: Existing or newly created CustomerResponse
    Gateway-->>WebApp: Customer ready

    WebApp-->>Browser: Registration success JSON (200 OK)
    Note right of Browser: JavaScript redirects to:<br/>window.location.href = '/login?registrationSuccess=true'
```

### 🔍 Registration Code Components

#### 1. **Controller and Customer Recovery (Proposed Pseudocode)**

```text
POST /api/register(request):
    try:
        registrationService.registerUser(request)
    catch UserAlreadyExistsException:
        return "Sign in to resume customer setup"  // Do not provision anonymously
    return ensureCustomer(request)                // Preserve the new-user path

POST /api/registration/customer(addressAndPhone):
    require authenticated OIDC session and valid CSRF token
    require email_verified == true
    derive username and normalized email from the authenticated principal
    build CustomerRequest using that identity and the validated contact details
    return ensureCustomer(customerRequest)

ensureCustomer(customerRequest):
    try GET /payment-service/api/customers/by-email?email=<normalized email>
    if found: return existing customer without changing its balance or profile
    if error other than 404: propagate failure
    try CustomerServiceClient.getOrCreateCustomer(customerRequest)  // POST create
    if duplicate-email 409: GET by normalized email and return the existing customer
    if another error: propagate it so authenticated completion can be retried
```

Do not add the continuation endpoint to `SecurityConstants.PUBLIC_URLS`. Use the verified session identity for lookup and creation; never trust a submitted email to identify an existing account. Preserve the structured `CustomerRequest` address fields and apply the initial balance only on first creation. Outgoing calls from authenticated completion use the user's authorized client; the public new-user path also needs a separately authorized provisioning call, which the current authorization-code-only client configuration does not supply.

A database uniqueness rule on normalized email is required to handle concurrent creation across different names; see the [customer schema migration gap](../payment-service/PaymentService-Sequence-Diagram.md#database-schema). Lookup followed by POST alone is not atomic. Retry a transient failure through the authenticated continuation, including when the original POST committed but its response was lost.

#### 2. **KeycloakRegistrationService.java (Current New-user Path)**
```java
// Location: src/main/java/com/example/retailstore/webapp/services/KeycloakRegistrationService.java

public void registerUser(RegistrationRequest request) {
    UserRepresentation user = getUserRepresentation(request);
    RealmResource realmResource = keycloakClient.realm(realm);
    try (Response response = realmResource.users().create(user)) {
        if (response.getStatus() == 409) {
            throw new UserAlreadyExistsException("Username or email is already taken.");
        } else if (response.getStatus() >= 400) {
            throw new KeyCloakException("Failed to register user. Status: " + response.getStatus());
        }
        String userId = CreatedResponseUtil.getCreatedId(response);
        try {
            realmResource
                    .users()
                    .get(userId)
                    .roles()
                    .realmLevel()
                    .add(List.of(realmResource.roles().get("user").toRepresentation()));
        } catch (Exception e) {
            try {
                realmResource.users().get(userId).remove();
            } catch (Exception cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
    }
}
```

#### 3. **Security Configuration**
```java
// Location: src/main/java/com/example/retailstore/webapp/config/SecurityConstants.java

public static final String[] PUBLIC_URLS = {
    "/js/**",
    "/css/**",
    "/images/**",
    "/error",
    "/webjars/**",
    "/",
    "/actuator/health/**",
    "/actuator/info",
    "/products/**",
    "/api/products/**",
    "/api/register",  // Registration endpoint is public
    "/registration",  // Registration page is public
    "/login"         // Login page is public
};
```

---

## 🔐 Authentication Flow - Code Mapping

```mermaid
sequenceDiagram
    participant User as 👤 User
    participant Browser as 🌐 Browser
    participant WebApp as 🛒 RetailStore WebApp
    participant KC as 🔐 Keycloak
    participant Spring as 🔧 Spring Security

    Note over User, Spring: 🔑 OAuth2/OIDC Authentication Flow

    User->>Browser: Navigate to /login
    Browser->>WebApp: GET /login
    
    WebApp->>Spring: Check authentication
    Note right of Spring: LoginController.login()<br/>File: LoginController.java:18<br/>Checks if user already authenticated
    
    alt User not authenticated
        WebApp-->>Browser: Login page (login.html)
        Note right of Browser: Template shows:<br/>"Login with Keycloak" button<br/>href="/oauth2/authorization/retailstore-webapp"
        
        User->>Browser: Click "Login with Keycloak"
        Browser->>WebApp: GET /oauth2/authorization/retailstore-webapp
        
        WebApp->>Spring: OAuth2 Authorization Request
        Note right of Spring: Spring Security OAuth2 Client<br/>Configuration from application.properties<br/>Client ID: retailstore-webapp
        
        Spring->>KC: Authorization Code Request
        Note right of KC: GET /realms/retailstore/protocol/openid-connect/auth<br/>Parameters:<br/>- client_id=retailstore-webapp<br/>- response_type=code<br/>- scope=openid profile<br/>- redirect_uri=http://localhost:8080/login/oauth2/code/retailstore-webapp
        
        KC-->>Browser: Redirect to Keycloak login
        Note right of Browser: Keycloak login form<br/>URL: http://localhost:9191/realms/retailstore/...
        
        User->>Browser: Enter credentials
        Browser->>KC: POST credentials
        
        KC->>KC: Validate credentials
        Note right of KC: Check against user database<br/>Password hash validation<br/>User: raja/retail (pre-configured)
        
        KC-->>Browser: Authorization code redirect
        Note right of Browser: Redirect to:<br/>http://localhost:8080/login/oauth2/code/retailstore-webapp?code=...
        
        Browser->>WebApp: GET /login/oauth2/code/retailstore-webapp?code=...
        
        WebApp->>Spring: Process authorization code
        Note right of Spring: OAuth2LoginAuthenticationFilter<br/>Handles the callback
        
        Spring->>KC: Exchange code for tokens
        Note right of KC: POST /realms/retailstore/protocol/openid-connect/token<br/>Parameters:<br/>- grant_type=authorization_code<br/>- code={authorization_code}<br/>- client_id=retailstore-webapp<br/>- client_secret=${OAUTH2_CLIENT_SECRET}
        
        KC-->>Spring: Access Token + ID Token
        Note right of Spring: JWT tokens containing:<br/>- User info (preferred_username, email)<br/>- Roles and permissions<br/>- Token expiry (300 seconds)
        
        Spring->>Spring: Create Authentication
        Note right of Spring: OAuth2AuthenticationToken<br/>Principal: DefaultOidcUser<br/>Authorities from token claims
        
        Spring-->>WebApp: Authentication successful
        WebApp-->>Browser: Redirect to / (home page)
        
    else User already authenticated
        WebApp-->>Browser: Redirect to / (home page)
    end
```

### 🔍 Authentication Code Components

#### 1. **SecurityConfig.java**
```java
// Location: src/main/java/com/example/retailstore/webapp/config/SecurityConfig.java

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {
    private final ClientRegistrationRepository clientRegistrationRepository;

    SecurityConfig(ClientRegistrationRepository clientRegistrationRepository) {
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, GrantedAuthoritiesMapper userAuthoritiesMapper) {
        http.authorizeHttpRequests(c -> c.requestMatchers(SecurityConstants.PUBLIC_URLS)
                        .permitAll()
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**")
                        .hasRole("OPERATOR")
                        .anyRequest()
                        .authenticated())
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/register")
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
                .oauth2Login(oauth2 -> oauth2.loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .userInfoEndpoint(userInfo -> userInfo.userAuthoritiesMapper(userAuthoritiesMapper)))
                .logout(logout -> logout.clearAuthentication(true)
                        .invalidateHttpSession(true)
                        .logoutSuccessHandler(oidcLogoutSuccessHandler()))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://example.com; connect-src 'self'")));

        return http.build();
    }

    private LogoutSuccessHandler oidcLogoutSuccessHandler() {
        OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
                new OidcClientInitiatedLogoutSuccessHandler(this.clientRegistrationRepository);
        oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/");
        return oidcLogoutSuccessHandler;
    }

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
}
```

#### 2. **OAuth2 Configuration (application.properties)**
```properties
OAUTH2_SERVER_URL=http://localhost:9191
REALM_URL=${OAUTH2_SERVER_URL}/realms/retailstore

# OAuth2 Client Registration
spring.security.oauth2.client.registration.retailstore-webapp.client-id=retailstore-webapp
spring.security.oauth2.client.registration.retailstore-webapp.client-secret=${OAUTH2_CLIENT_SECRET}
spring.security.oauth2.client.registration.retailstore-webapp.authorization-grant-type=authorization_code
spring.security.oauth2.client.registration.retailstore-webapp.scope[0]=openid
spring.security.oauth2.client.registration.retailstore-webapp.scope[1]=profile
spring.security.oauth2.client.registration.retailstore-webapp.scope[2]=roles
spring.security.oauth2.client.registration.retailstore-webapp.redirect-uri={baseUrl}/login/oauth2/code/retailstore-webapp

# OAuth2 Provider Configuration
spring.security.oauth2.client.provider.retailstore-webapp.issuer-uri=${REALM_URL}
```

#### 3. **SecurityHelper.java**
```java
// Location: src/main/java/com/example/retailstore/webapp/services/SecurityHelper.java

public String getUsername() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof DefaultOidcUser principal) {
        return principal.getAttribute("preferred_username");
    }
    return null;
}

public String getLoggedInUserEmail() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof DefaultOidcUser principal) {
        return principal.getAttribute("email");
    }
    return null;
}

public String getAccessToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof OAuth2AuthenticationToken oauthToken) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(), oauthToken.getName());
        if (client != null && client.getAccessToken() != null) {
            return client.getAccessToken().getTokenValue();
        }
    }
    return null;
}
```

---

## 🔒 Token Management & API Calls

```mermaid
sequenceDiagram
    participant User as 👤 User
    participant Browser as 🌐 Browser
    participant WebApp as 🛒 RetailStore WebApp
    participant Gateway as 🌐 API Gateway
    participant Service as 🔧 Backend Service

    Note over User, Service: 🎫 Authenticated API Call Flow

    User->>Browser: Navigate to protected page (/orders)
    Browser->>WebApp: GET /orders (with session cookie)
    
    WebApp->>WebApp: Check authentication
    Note right of WebApp: Spring Security checks:<br/>- Session exists<br/>- OAuth2AuthenticationToken valid<br/>- Access token not expired
    
    WebApp-->>Browser: Orders page (Thymeleaf)
    
    Browser->>WebApp: AJAX GET /api/orders
    Note right of Browser: JavaScript makes API call<br/>CSRF token included in headers
    
    WebApp->>WebApp: Extract access token
    Note right of WebApp: SecurityHelper.getAccessToken()<br/>Gets JWT from OAuth2AuthorizedClient
    
    WebApp->>Gateway: GET /order-service/api/orders
    Note right of WebApp: HTTP Headers:<br/>Authorization: Bearer {jwt_token}<br/>Content-Type: application/json
    
    Gateway->>Gateway: Validate JWT token
    Note right of Gateway: Spring Cloud Gateway<br/>JWT validation filter<br/>Checks signature & expiry
    
    Gateway->>Service: Forward request with token
    Service->>Service: Extract user context
    Note right of Service: JWT claims contain:<br/>- preferred_username<br/>- email<br/>- roles<br/>- client_id
    
    Service-->>Gateway: Response data
    Gateway-->>WebApp: Response data
    WebApp-->>Browser: JSON response
```

### 🔍 Token Management Code

#### 1. **API Client Configuration (Spring Boot 4.1.2 RestClient & Interceptor)**
```java
// Location: src/main/java/com/example/retailstore/webapp/clients/ClientsConfig.java

import org.springframework.security.oauth2.client.web.client.support.OAuth2RestClientHttpServiceGroupConfigurer;

@Configuration(proxyBeanMethods = false)
@ImportHttpServices({
    CatalogServiceClient.class,
    OrderServiceClient.class,
    CustomerServiceClient.class,
    InventoryServiceClient.class
})
class ClientsConfig {

    @Bean
    RestClientHttpServiceGroupConfigurer groupConfigurer(
            ObservationRegistry observationRegistry,
            ApplicationProperties applicationProperties) {
        return groups -> groups.forEachClient((group, builder) -> builder
                .baseUrl(applicationProperties.apiGatewayUrl())
                .defaultHeaders(httpHeaders -> {
                    httpHeaders.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
                    httpHeaders.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
                })
                .observationRegistry(observationRegistry));
    }

    @Bean
    OAuth2RestClientHttpServiceGroupConfigurer oauth2Configurer(
            OAuth2AuthorizedClientManager authorizedClientManager) {
        // Installs both the token interceptor and ClientRegistrationIdProcessor.
        return OAuth2RestClientHttpServiceGroupConfigurer.from(authorizedClientManager);
    }
}
```

Annotate each imported interface so every request carries the registration ID (existing endpoint methods are omitted here):

```java
import org.springframework.security.oauth2.client.annotation.ClientRegistrationId;

@ClientRegistrationId("retailstore-webapp")
@HttpExchange("/catalog-service")
public interface CatalogServiceClient { /* existing methods */ }

@ClientRegistrationId("retailstore-webapp")
@HttpExchange("/order-service")
public interface OrderServiceClient { /* existing methods */ }

@ClientRegistrationId("retailstore-webapp")
@HttpExchange("/payment-service")
public interface CustomerServiceClient { /* existing methods */ }

@ClientRegistrationId("retailstore-webapp")
@HttpExchange("/inventory-service")
public interface InventoryServiceClient { /* existing methods */ }
```

`ClientRegistrationIdProcessor` copies the annotation value into request attributes, which the interceptor uses to resolve the authorized client. For an authenticated user with an authorized `retailstore-webapp` client, it attaches the bearer token without a manually supplied `Authorization` header. See [Spring Security's HTTP service group configurer](https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/oauth2/client/web/client/support/OAuth2RestClientHttpServiceGroupConfigurer.html).

**Source gap:** The current `ClientsConfig.java` installs only the interceptor, and its imported interfaces lack these annotations. The configuration above is the corrected example; applying it to runtime code is still required for this registration-resolution path.

---

## 🚪 Logout Flow

```mermaid
sequenceDiagram
    participant User as 👤 User
    participant Browser as 🌐 Browser
    participant WebApp as 🛒 RetailStore WebApp (Port 8080)
    participant KC as 🔐 Keycloak (Port 9191)
    participant Spring as 🔧 Spring Security

    Note over User, Spring: 🚪 OIDC Logout Flow

    User->>Browser: Click logout button
    Browser->>WebApp: POST /logout
    
    WebApp->>Spring: Process logout request
    Note right of Spring: LogoutFilter intercepts<br/>Configured in SecurityConfig
    
    Spring->>Spring: Clear local session
    Note right of Spring: - Invalidate HTTP session<br/>- Clear SecurityContext<br/>- Remove authentication
    
    Spring->>KC: OIDC logout request
    Note right of KC: OidcClientInitiatedLogoutSuccessHandler<br/>POST /realms/retailstore/protocol/openid-connect/logout<br/>Parameters:<br/>- id_token_hint={id_token}<br/>- post_logout_redirect_uri=http://localhost:8080/
    
    KC->>KC: Invalidate SSO session
    Note right of KC: - Remove Keycloak session<br/>- Invalidate refresh tokens<br/>- Clear user session
    
    KC-->>Browser: Redirect to post-logout URI (http://localhost:8080/)
    Browser->>WebApp: GET /
    WebApp-->>Browser: Render public home / products page
```

### 🔍 Logout Configuration
```java
// Location: src/main/java/com/example/retailstore/webapp/config/SecurityConfig.java

private LogoutSuccessHandler oidcLogoutSuccessHandler() {
    OidcClientInitiatedLogoutSuccessHandler oidcLogoutSuccessHandler =
        new OidcClientInitiatedLogoutSuccessHandler(this.clientRegistrationRepository);
    oidcLogoutSuccessHandler.setPostLogoutRedirectUri("{baseUrl}/");
    return oidcLogoutSuccessHandler;
}
```

---

## 🛡️ Role-Based Access Control

```mermaid
flowchart TD
    A["🔐 User Authentication (Keycloak OIDC)"] --> B{"Check User Roles"}
    
    B -->|"Client Role: ADMIN"| C["🔧 Admin Features"]
    B -->|"Client Role: OPERATOR"| D["📊 Observability & Metrics"]
    B -->|"Realm Role: user"| E["👤 Shopper Access"]
    
    C --> F["📦 Inventory Management (/inventory)"]
    C --> G["➕ Product Creation (/api/products)"]
    D --> H["📈 Prometheus (/actuator/prometheus)"]
    D --> I["📊 Metrics (/actuator/metrics)"]
    E --> J["🛒 Shopping Cart (/cart)"]
    E --> K["📋 Order Processing & History (/orders)"]
    E --> L["📚 Browse Products (/products)"]
    
    F --> M["@PreAuthorize('hasRole(\'ADMIN\')')"]
    G --> N["@PreAuthorize('hasRole(\'ADMIN\')')"]
    H --> O["hasRole('OPERATOR') in SecurityConfig"]
    I --> O
    J --> P["Authenticated Session"]
    K --> P
```

### 🔍 Role-Based Security Code

#### 1. **Method-Level Security (ADMIN Role)**
```java
// Location: src/main/java/com/example/retailstore/webapp/web/controller/InventoryController.java

@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/inventory")
String showInventoriesPage(@RequestParam(defaultValue = "0") int page, Model model) {
    model.addAttribute("pageNo", page);
    return "inventory";
}

@PutMapping(value = "/inventory", consumes = MediaType.APPLICATION_JSON_VALUE)
@ResponseBody
@PreAuthorize("hasRole('ADMIN')")
InventoryResponse updateInventory(@Valid @RequestBody InventoryResponse inventoryResponse) {
    return inventoryServiceClient.updateInventory(
            inventoryResponse.id(), inventoryResponse.createInventoryUpdateRequest());
}

// Location: src/main/java/com/example/retailstore/webapp/web/controller/ProductController.java

@PreAuthorize("hasRole('ADMIN')")
@PostMapping(value = "/api/products", consumes = MediaType.APPLICATION_JSON_VALUE)
@ResponseBody
ProductResponse createProduct(@Valid @RequestBody ProductRequest productRequest) {
    return catalogServiceClient.createProduct(productRequest);
}
```

#### 2. **Filter Chain Authorization (OPERATOR Role)**
```java
// Location: src/main/java/com/example/retailstore/webapp/config/SecurityConfig.java

http.authorizeHttpRequests(c -> c
    .requestMatchers(SecurityConstants.PUBLIC_URLS).permitAll()
    .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/metrics/**").hasRole("OPERATOR")
    .anyRequest().authenticated()
);
```

#### 3. **Keycloak Role Configuration (Terraform / JSON)**
```hcl
# Location: deployment/terraform/keycloak/roles.tf

resource "keycloak_role" "webapp_admin_role" {
  realm_id    = keycloak_realm.retailstore.id
  client_id   = keycloak_openid_client.retailstore_webapp.id
  name        = "ADMIN"
  description = "retail store Administrator"
}

resource "keycloak_role" "webapp_operator_role" {
  realm_id    = keycloak_realm.retailstore.id
  client_id   = keycloak_openid_client.retailstore_webapp.id
  name        = "OPERATOR"
  description = "retail store Operator"
}

resource "keycloak_role" "user_realm_role" {
  realm_id    = keycloak_realm.retailstore.id
  name        = "user"
  description = "User role"
}
```

---

## 🔧 Configuration Deep Dive

### 1. **Keycloak Client Configuration**
```json
{
  "clientId": "retailstore-webapp",
  "secret": "${OAUTH2_CLIENT_SECRET}",
  "redirectUris": [
    "http://localhost:8080/login/oauth2/code/retailstore-webapp"
  ],
  "webOrigins": ["http://localhost:8080"],
  "standardFlowEnabled": true,
  "directAccessGrantsEnabled": true,
  "frontchannelLogout": true,
  "attributes": {
    "post.logout.redirect.uris": "http://localhost:8080/"
  }
}
```

### 2. **JWT Token Structure**
```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT",
    "kid": "rsa-generated-key-id"
  },
  "payload": {
    "exp": 1640995200,
    "iat": 1640991600,
    "iss": "http://localhost:9191/realms/retailstore",
    "aud": "retailstore-webapp",
    "azp": "retailstore-webapp",
    "sub": "362b5bb3-0e0a-4ee6-9b59-53e2ca1adeac",
    "preferred_username": "retail",
    "email": "retailstore@gmail.com",
    "realm_access": {
      "roles": ["default-roles-retailstore", "user"]
    },
    "resource_access": {
      "retailstore-webapp": {
        "roles": ["ADMIN", "OPERATOR"]
      }
    },
    "scope": "openid profile roles"
  }
}
```

### 3. **CSRF Protection**
```java
// Location: src/main/java/com/example/retailstore/webapp/config/SecurityConfig.java

.csrf(csrf -> csrf
    .ignoringRequestMatchers("/api/register") // Registration endpoint exempt
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
)
```

---

## 🚨 Error Handling & Security

### 1. **Exception Handling**
```java
// Location: src/main/java/com/example/retailstore/webapp/exception/GlobalExceptionHandler.java

@ExceptionHandler(KeyCloakException.class)
public ResponseEntity<Map<String, String>> handleKeycloakException(KeyCloakException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("message", "Authentication service error"));
}
```

### 2. **Security Headers**
```json
// Keycloak Browser Security Headers
{
  "contentSecurityPolicy": "frame-src 'self'; frame-ancestors 'self'; object-src 'none';",
  "xFrameOptions": "SAMEORIGIN",
  "xContentTypeOptions": "nosniff",
  "xXSSProtection": "1; mode=block",
  "strictTransportSecurity": "max-age=31536000; includeSubDomains"
}
```

---

## 📊 Key Metrics & Monitoring

### Authentication Events
- **Login Success/Failure** rates
- **Token Refresh** frequency  
- **Session Duration** analytics
- **Role-based Access** patterns

### Security Monitoring
- **Failed Authentication** attempts
- **Token Expiry** handling
- **CSRF Attack** prevention
- **Session Hijacking** detection

---

## 🎯 Best Practices Implemented

1. **🔐 Secure Token Storage**: Tokens stored in OAuth2AuthorizedClient, not in browser
2. **🛡️ CSRF Protection**: Cookie-based CSRF tokens for form submissions
3. **⏰ Token Expiry**: Short-lived access tokens (5 minutes) with refresh capability
4. **🚪 Proper Logout**: OIDC-compliant logout with SSO session termination
5. **🔒 Role Separation**: Clear distinction between `user` (shopper), `ADMIN` (inventory/products), and `OPERATOR` (actuator metrics/Prometheus) roles
6. **📝 Audit Trail**: Comprehensive logging of authentication events
7. **🌐 CORS Configuration**: Proper cross-origin resource sharing setup
8. **🔧 Configuration Security**: Sensitive configs externalized via environment variables

---

## 🔍 Troubleshooting Guide

### Common Issues & Solutions

| Issue | Symptoms | Solution |
|-------|----------|----------|
| **Token Expired** | 401 Unauthorized on API calls | Implement token refresh logic |
| **CSRF Token Missing** | 403 Forbidden on form submission | Ensure CSRF token in request headers |
| **Role Access Denied** | 403 Forbidden on admin endpoints | Check user roles in Keycloak |
| **Redirect Loop** | Infinite redirects between login/home | Verify OAuth2 redirect URIs |
| **Session Timeout** | Unexpected logouts | Configure session timeout settings |

### Debug Configuration
```properties
# Enable OAuth2 debug logging
logging.level.org.springframework.security.oauth2=DEBUG
logging.level.org.springframework.security.web=DEBUG
```

This comprehensive documentation maps every authentication and registration flow step to specific code components, providing developers with a complete understanding of the security implementation in the RetailStore WebApp.