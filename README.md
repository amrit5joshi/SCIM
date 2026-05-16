# SCIM 2.0 User Provisioning Service

A production-grade SCIM 2.0 user provisioning API built with Java 17 and Spring Boot 3, backed by MySQL 8. Designed for direct integration with enterprise identity providers such as Okta, Microsoft Entra ID (formerly Azure AD), and OneLogin.

---

## Table of Contents

- [What Problem This Solves](#what-problem-this-solves)
- [How It Works](#how-it-works)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Architecture Overview](#architecture-overview)
- [API Endpoints](#api-endpoints)
- [SCIM Wire Format](#scim-wire-format)
- [Security Model](#security-model)
- [Database Schema](#database-schema)
- [Filter Parsing](#filter-parsing)
- [Error Handling](#error-handling)
- [How to Run Locally](#how-to-run-locally)
- [Running Tests](#running-tests)
- [Sample curl Commands](#sample-curl-commands)
- [Configuration Reference](#configuration-reference)
- [SCIM Compliance Matrix](#scim-compliance-matrix)
- [Design Decisions](#design-decisions)
- [Future Work](#future-work)

---

## What Problem This Solves

Every B2B SaaS product eventually faces the same operational problem: enterprise customers manage their employees in a central Identity Provider (IdP) — typically Okta, Microsoft Entra ID, or OneLogin — and expect those accounts to be automatically provisioned and deprovisioned in every SaaS tool their company uses. Without automation, this means manual account creation for every new hire and manual deletion for every departure. At 500 employees, that is unmanageable. At 5,000, it is a security liability.

The **System for Cross-domain Identity Management** (SCIM) 2.0 protocol, defined in [RFC 7643](https://datatracker.ietf.org/doc/html/rfc7643) and [RFC 7644](https://datatracker.ietf.org/doc/html/rfc7644), is the industry standard that solves this. It defines:

- A **JSON schema** for identity resources (users, groups) with well-known field names
- A **REST protocol** for create, read, update, and delete operations
- A **filter grammar** so the IdP can query for specific users
- A **pagination envelope** so the IdP can page through large user lists
- A **standard error shape** so both sides can parse failures programmatically

This service is the **SCIM server** (in SCIM terminology, the "service provider"). The IdP is the client. When an IT administrator adds a new employee in Okta, Okta calls `POST /scim/v2/Users` on this service. When the employee leaves and their account is deactivated in Okta, Okta calls `DELETE /scim/v2/Users/{id}`. The service handles persistence, validation, and returning the correct SCIM-shaped JSON — so any standards-compliant IdP can integrate without custom code.

---

## How It Works

```
IdP (Okta / Entra ID)           SCIM Service                    MySQL 8
        |                              |                              |
        |-- POST /scim/v2/Users ------>|                              |
        |   Authorization: Bearer ...  |-- validate token             |
        |   { "userName": "alice" }    |-- check duplicate userName   |
        |                              |-- INSERT into users          |-->|
        |                              |<-- entity with UUID         |<--|
        |<-- 201 { "id": "uuid" } -----|                              |
        |                              |                              |
        |-- DELETE /scim/v2/Users/{id} |                              |
        |                              |-- look up by id              |-->|
        |                              |-- DELETE (cascades emails)   |-->|
        |<-- 204 No Content ---------- |                              |
```

Every request carries a `Bearer` token in the `Authorization` header. The filter chain validates it before any controller logic runs. Errors at any layer — validation, business rules, missing resources — are caught by the `@RestControllerAdvice` and translated to the SCIM error schema before the response leaves the server.

---

## Tech Stack

| Layer | Technology | Version | Why |
|---|---|---|---|
| Language | Java | 17 | LTS release; records, text blocks, sealed classes |
| Framework | Spring Boot | 3.2.5 | Auto-configuration, embedded Tomcat, production-ready |
| Web | Spring MVC | — | Servlet-based, mature, good MockMvc test support |
| Persistence | Spring Data JPA + Hibernate | — | Repository pattern, JPQL, entity lifecycle management |
| Database | MySQL | 8.0 | Industry-standard RDBMS; `utf8mb4` for full Unicode |
| Security | Spring Security | — | Filter chain, security context, stateless session management |
| Schema migration | Flyway | — | Versioned SQL migrations; repeatable, auditable, CI-safe |
| Validation | Jakarta Bean Validation (Hibernate Validator) | — | Declarative constraints on DTO fields |
| Boilerplate | Lombok | — | `@Data`, `@Builder`, `@RequiredArgsConstructor` |
| API docs | springdoc-openapi (Swagger UI) | 2.5.0 | Auto-generated from annotations; interactive try-it-out |
| Build | Maven | 3.x | Standard Java build; dependency management via `pom.xml` |
| Unit tests | JUnit 5 + Mockito | — | Pure service-layer tests with no Spring context |
| Integration tests | MockMvc + H2 | — | Full Spring context, real filter chain, in-memory DB |
| Containerisation | Docker / Docker Compose | — | One-command MySQL setup for local development |

---

## Project Structure

```
scim/
├── src/
│   ├── main/
│   │   ├── java/com/amrit/scim/
│   │   │   ├── ScimApplication.java          # Entry point — @SpringBootApplication
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java       # Bearer token filter chain
│   │   │   │   └── OpenApiConfig.java        # Swagger bearerAuth scheme
│   │   │   ├── controller/
│   │   │   │   └── UserController.java       # REST endpoints (POST/GET/PUT/DELETE)
│   │   │   ├── service/
│   │   │   │   └── UserService.java          # Business logic, transaction boundaries
│   │   │   ├── repository/
│   │   │   │   └── UserRepository.java       # Spring Data JPA interface + @EntityGraph
│   │   │   ├── entity/
│   │   │   │   ├── UserEntity.java           # JPA entity → users table
│   │   │   │   └── EmailEntity.java          # JPA entity → user_emails table
│   │   │   ├── dto/
│   │   │   │   ├── ScimUser.java             # API contract — full SCIM User resource
│   │   │   │   ├── ScimName.java             # Nested name object (givenName, familyName)
│   │   │   │   ├── ScimEmail.java            # Nested email object (value, type, primary)
│   │   │   │   ├── ScimMeta.java             # Server-managed metadata (created, location)
│   │   │   │   ├── ScimListResponse.java     # ListResponse envelope for GET /Users
│   │   │   │   └── ScimError.java            # Error response per RFC 7644 §3.12
│   │   │   ├── mapper/
│   │   │   │   └── UserMapper.java           # Entity ↔ DTO conversion; assigns UUID + timestamps
│   │   │   ├── filter/
│   │   │   │   ├── ScimFilterParser.java     # Parses SCIM filter query parameter
│   │   │   │   └── FilterCriteria.java       # Record: attribute + value from parsed filter
│   │   │   └── exception/
│   │   │       ├── ScimExceptionHandler.java     # @RestControllerAdvice — all error mapping
│   │   │       ├── UserNotFoundException.java     # → 404
│   │   │       ├── DuplicateUserNameException.java # → 409
│   │   │       ├── ScimValidationException.java   # → 400 invalidValue
│   │   │       └── InvalidFilterException.java    # → 400 invalidFilter
│   │   └── resources/
│   │       ├── application.properties        # MySQL, JPA, auth token, logging config
│   │       └── db/migration/
│   │           ├── V1__create_users_table.sql     # Flyway migration: users table
│   │           └── V2__create_user_emails_table.sql # Flyway migration: user_emails table
│   └── test/
│       ├── java/com/amrit/scim/
│       │   ├── controller/
│       │   │   └── UserControllerTest.java   # 13 MockMvc integration tests
│       │   └── service/
│       │       └── UserServiceTest.java      # 6 Mockito unit tests
│       └── resources/
│           └── application-test.properties  # H2 in-memory DB; overrides MySQL config
├── Dockerfile                               # Multi-stage build → lean runtime image
├── docker-compose.yml                       # MySQL 8 container for local dev
└── pom.xml                                  # Maven build descriptor
```

---

## Architecture Overview

The service follows a strict **layered architecture**. Each layer has one responsibility, and dependencies only flow downward — controller → service → repository. Nothing in a lower layer imports from a higher one.

```
┌─────────────────────────────────────────────────────┐
│  HTTP / Spring Security Filter Chain                │  SecurityConfig
│  (token validation before any controller runs)      │
└───────────────────────┬─────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│  Controller Layer                                   │  UserController
│  HTTP translation only: parse request, call         │
│  service, return ResponseEntity with correct status │
└───────────────────────┬─────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────────┐
│  Service Layer                                      │  UserService
│  Business rules: uniqueness check, email validation,│
│  pagination math, filter routing, transactions      │
└──────────┬────────────────────────┬─────────────────┘
           ↓                        ↓
┌──────────────────┐     ┌──────────────────────────────┐
│  Repository      │     │  Mapper                      │
│  UserRepository  │     │  UserMapper                  │
│  Spring Data JPA │     │  Entity ↔ DTO conversion;    │
│  + @EntityGraph  │     │  assigns UUID + timestamps   │
└──────────┬───────┘     └──────────────────────────────┘
           ↓
┌─────────────────────────────────────────────────────┐
│  Persistence Layer                                  │
│  UserEntity + EmailEntity (JPA / Hibernate)         │
│  MySQL 8 via Flyway-managed schema                  │
└─────────────────────────────────────────────────────┘
           ↑ (cross-cutting concern)
┌─────────────────────────────────────────────────────┐
│  Exception Handler                                  │  ScimExceptionHandler
│  @RestControllerAdvice — intercepts all exceptions  │
│  and converts them to RFC 7644 §3.12 error JSON    │
└─────────────────────────────────────────────────────┘
```

**Key principle:** The `ScimUser` DTO and `UserEntity` are completely separate classes connected only through `UserMapper`. This means the API contract (what JSON looks like) and the database schema (column names, table structure) can evolve independently without breaking each other.

---

## API Endpoints

All endpoints are under `/scim/v2/Users` and require `Authorization: Bearer <token>`.  
Both `application/json` and `application/scim+json` are accepted and returned.

### POST /scim/v2/Users — Create a user

Creates a new user. The server assigns the `id` (UUID) and `meta` fields — the client must not send them.

**Request body:**
```json
{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "userName": "alice@example.com",
  "externalId": "okta-user-001",
  "name": {
    "givenName": "Alice",
    "familyName": "Smith",
    "formatted": "Alice Smith"
  },
  "emails": [
    { "value": "alice@example.com", "type": "work", "primary": true }
  ],
  "active": true
}
```

**Success response — 201 Created:**
```json
{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userName": "alice@example.com",
  "externalId": "okta-user-001",
  "name": {
    "givenName": "Alice",
    "familyName": "Smith",
    "formatted": "Alice Smith"
  },
  "emails": [
    { "value": "alice@example.com", "type": "work", "primary": true }
  ],
  "active": true,
  "meta": {
    "resourceType": "User",
    "created": "2024-01-15T09:00:00.000Z",
    "lastModified": "2024-01-15T09:00:00.000Z",
    "location": "http://localhost:8080/scim/v2/Users/550e8400-e29b-41d4-a716-446655440000"
  }
}
```

**Error cases:**
| Condition | Status | `scimType` |
|---|---|---|
| Missing `Authorization` header | 401 | — |
| Wrong bearer token | 401 | — |
| `userName` is blank or missing | 400 | `invalidValue` |
| More than one email with `primary: true` | 400 | `invalidValue` |
| `userName` already exists | 409 | `uniqueness` |

---

### GET /scim/v2/Users/{id} — Retrieve a user

Returns a single user resource by its server-assigned UUID.

**Success response — 200 OK:** Same shape as the create response above.

**Error cases:**
| Condition | Status |
|---|---|
| No user with that UUID | 404 |

---

### GET /scim/v2/Users — List users (with filter and pagination)

Returns a `ListResponse` envelope containing a page of users. Supports filtering by `userName` or `externalId`, and SCIM-standard pagination via `startIndex` and `count`.

**Query parameters:**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `filter` | string | — | SCIM filter expression (see [Filter Parsing](#filter-parsing)) |
| `startIndex` | int | 1 | 1-based index of the first result to return |
| `count` | int | 10 | Maximum results per page; capped at 100 |

**Success response — 200 OK:**
```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:ListResponse"],
  "totalResults": 42,
  "startIndex": 1,
  "itemsPerPage": 10,
  "Resources": [
    { "id": "...", "userName": "alice@example.com", ... },
    { "id": "...", "userName": "bob@example.com", ... }
  ]
}
```

**Filter examples:**
```
GET /scim/v2/Users?filter=userName eq "alice@example.com"
GET /scim/v2/Users?filter=externalId eq "okta-user-001"
GET /scim/v2/Users?startIndex=11&count=10
```

**Error cases:**
| Condition | Status | `scimType` |
|---|---|---|
| Filter expression is not supported | 400 | `invalidFilter` |

---

### PUT /scim/v2/Users/{id} — Full replacement

Replaces all mutable fields of an existing user. This is a **full replacement** per RFC 7644 §3.5.1 — any field absent from the body is cleared. The `id` and `meta.created` are always preserved regardless of what the client sends. `meta.lastModified` is updated to the current timestamp.

**Request body:** Same shape as create.

**Success response — 200 OK:** Full updated resource.

**Error cases:**
| Condition | Status | `scimType` |
|---|---|---|
| User UUID does not exist | 404 | — |
| New `userName` conflicts with another user | 409 | `uniqueness` |
| More than one email with `primary: true` | 400 | `invalidValue` |

---

### DELETE /scim/v2/Users/{id} — Delete a user

Hard-deletes the user and all associated email records (via `ON DELETE CASCADE` in the database). Returns no body.

**Success response — 204 No Content**

**Error cases:**
| Condition | Status |
|---|---|
| User UUID does not exist | 404 |

---

## SCIM Wire Format

Every response conforms to RFC 7643 / RFC 7644. Key details:

### Schemas array
Every SCIM resource must include a `schemas` array identifying its type. This service always returns:
- User resources: `["urn:ietf:params:scim:schemas:core:2.0:User"]`
- List responses: `["urn:ietf:params:scim:api:messages:2.0:ListResponse"]`
- Error responses: `["urn:ietf:params:scim:api:messages:2.0:Error"]`

The `schemas` array is defaulted in the `ScimUser`, `ScimListResponse`, and `ScimError` DTOs so it is always present without the controller or service needing to set it.

### Server-managed fields
The following fields are always assigned by the server and ignored if sent by the client:
- `id` — random UUID assigned on creation, never changes
- `meta.created` — timestamp of first creation, never changes
- `meta.lastModified` — updated on every PUT
- `meta.location` — absolute URL to this resource, built from the request scheme+host+port

### The `externalId` field
This is an identifier supplied by the upstream IdP — Okta uses its own internal user ID, Entra ID uses the Entra object ID. It is stored as-is and returned unchanged. It is not guaranteed unique at the DB level (different IdPs could theoretically send the same value), but the filter supports looking users up by it.

### Email addresses
The `emails` array follows RFC 7643 §2.4 (multi-valued attributes). Each entry has:
- `value` — the email address string
- `type` — free-form label (typically `"work"` or `"home"`)
- `primary` — boolean; at most one entry in the array may be `true`

The service enforces the single-primary constraint: sending two emails both with `primary: true` returns a 400 error.

---

## Security Model

Every request to `/scim/v2/**` must include:

```
Authorization: Bearer <token>
```

The token is compared against `scim.auth.token` in `application.properties` using a simple string equality check. Missing or wrong tokens return:

```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:Error"],
  "status": "401",
  "detail": "Missing or malformed Authorization header."
}
```

### Why not OAuth2 / JWT?

Static bearer tokens are sufficient for machine-to-machine provisioning integrations: the IdP is configured once with a token that never changes. For production deployments with multiple tenants or short-lived credentials, replace the static token check with `spring-boot-starter-oauth2-resource-server` pointing at the IdP's JWKS endpoint.

### How the filter is wired

`SecurityConfig` builds the bearer token filter as a private factory method (`buildBearerTokenFilter()`) rather than a Spring `@Bean`. This is intentional: declaring an `OncePerRequestFilter` as a `@Bean` causes Spring Boot's auto-configuration to register it both in the security chain and as a standalone servlet filter, resulting in double invocation. The private factory method avoids this.

The filter explicitly short-circuits for Swagger UI paths (`/swagger-ui/**`, `/v3/api-docs/**`) so the interactive documentation remains accessible without a token.

### Session management

The service is fully stateless. `SessionCreationPolicy.STATELESS` prevents Spring Security from creating or using `HttpSession`. Each request must carry its own `Authorization` header.

---

## Database Schema

The schema is managed by Flyway. Two migrations run on startup and are never re-applied once executed. `spring.jpa.hibernate.ddl-auto=validate` ensures Hibernate confirms the live schema matches the entity model but never mutates it.

### V1 — `users` table

```sql
CREATE TABLE users (
    id            VARCHAR(36)  NOT NULL,
    user_name     VARCHAR(255) NOT NULL,
    external_id   VARCHAR(255),
    name_given    VARCHAR(255),
    name_family   VARCHAR(255),
    name_formatted VARCHAR(255),
    active        TINYINT(1)   NOT NULL DEFAULT 1,
    created       DATETIME(6)  NOT NULL,
    last_modified DATETIME(6)  NOT NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_user_name UNIQUE (user_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- `id` is `VARCHAR(36)` to store UUID strings portably — no auto-increment, UUID is assigned in Java.
- `uq_users_user_name` is the DB-level uniqueness guard. The service also checks at the application level (via `findByUserName()`) before attempting the insert, so it can return a clean 409 SCIM error rather than catching a raw constraint violation.
- `DATETIME(6)` stores microsecond-precision timestamps, matching Java's `Instant` → Hibernate → MySQL mapping.
- `utf8mb4` supports the full Unicode range including emoji in display names.

### V2 — `user_emails` table

```sql
CREATE TABLE user_emails (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       VARCHAR(36)  NOT NULL,
    email_value   VARCHAR(255) NOT NULL,
    type          VARCHAR(50),
    primary_email TINYINT(1)   NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_emails PRIMARY KEY (id),
    CONSTRAINT fk_user_emails_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

- `email_value` (not `value`) avoids an H2 reserved-keyword conflict in tests, while still mapping cleanly via `@Column(name = "email_value")` on the entity.
- `ON DELETE CASCADE` means deleting a user automatically removes all their email rows — no orphan cleanup needed in application code.
- `primary_email` (not `primary`) avoids a MySQL reserved keyword.

### Entity relationships

```
users (1) ────────── (N) user_emails
          @OneToMany        @ManyToOne
          mappedBy="user"
          cascade=ALL
          orphanRemoval=true
          fetch=LAZY
          @BatchSize(25)
```

Emails are **lazy-loaded** to avoid N+1 on list queries. The `@BatchSize(25)` annotation tells Hibernate to load emails for up to 25 users in a single batched `IN (...)` query when iterating a result list. For single-user reads (GET by id, GET by userName), the repository uses `@EntityGraph(attributePaths = "emails")` to issue a single `JOIN FETCH` query instead.

---

## Filter Parsing

The `ScimFilterParser` component parses the SCIM `filter` query parameter. It supports exactly two filter expressions:

```
userName eq "value"
externalId eq "value"
```

Both attribute names are case-insensitive (the regex uses `Pattern.CASE_INSENSITIVE`). Matching is exact equality — not a LIKE/contains search — per RFC 7644 §3.4.2.2.

The implementation uses a single compiled `Pattern`:

```
^(userName|externalId)\s+eq\s+"([^"]+)"$
```

Group 1 captures the attribute name; group 2 captures the value between quotes. The attribute is lowercased before being stored in the `FilterCriteria` record, so routing logic in `UserService` can use a simple `"username".equals(...)` comparison.

Any other filter expression — `emails.value eq`, `name.givenName co`, compound `AND`/`OR` — is rejected with a 400 `invalidFilter` error. A production implementation would use an ANTLR grammar or the `scim2-sdk` library.

The parsed result is returned as a `FilterCriteria` Java record:

```java
public record FilterCriteria(String attribute, String value) {}
```

Using a record (rather than returning a bare `Optional<String>`) means the service layer knows both *which* attribute was matched and *what value* to query for, without ambiguity.

---

## Error Handling

All exceptions are intercepted by `ScimExceptionHandler` (`@RestControllerAdvice`). Controllers have no try/catch blocks. Every error response follows RFC 7644 §3.12:

```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:Error"],
  "status": "400",
  "scimType": "invalidValue",
  "detail": "At most one email address may have primary=true; found 2."
}
```

| Exception | HTTP Status | `scimType` | When |
|---|---|---|---|
| `UserNotFoundException` | 404 | — | GET/PUT/DELETE with unknown UUID |
| `DuplicateUserNameException` | 409 | `uniqueness` | POST or PUT with a taken `userName` |
| `ScimValidationException` | 400 | `invalidValue` | Multiple primary emails |
| `InvalidFilterException` | 400 | `invalidFilter` | Unsupported filter expression |
| `MethodArgumentNotValidException` | 400 | `invalidValue` | Bean Validation failure (blank `userName`, etc.) |
| Auth failure (in filter) | 401 | — | Missing or invalid bearer token |
| Anything else | 500 | — | Unexpected server error (detail is generic — no internals leaked) |

The `status` field in the error body is a string (`"404"`, `"409"`) as required by the SCIM spec, even though HTTP status codes are integers.

---

## How to Run Locally

### Prerequisites
- Java 17+ (set `JAVA_HOME` if your IDE ships a different JDK)
- Maven 3.8+ or the included Maven wrapper
- Docker Desktop (for MySQL)

### Step 1 — Clone the repository

```bash
git clone https://github.com/amrit5joshi/scim-provisioning-service.git
cd scim-provisioning-service
```

### Step 2 — Start MySQL with Docker Compose

```bash
docker-compose up -d
```

This starts a MySQL 8 container at `localhost:3306` with:
- Database: `scimdb`
- User: `scim` / Password: `scim123`
- Data persisted in a named Docker volume (`scim-mysql-data`)
- Healthcheck via `mysqladmin ping`

Wait a few seconds for MySQL to initialise, then verify:

```bash
docker-compose ps   # should show "healthy" or "Up"
```

### Step 3 — Start the application

```bash
./mvnw spring-boot:run
```

On Windows:
```powershell
.\mvnw.cmd spring-boot:run
```

> **JDK version note:** If you are running IntelliJ IDEA 2024+ (which may ship JBR 21 or 25), ensure `JAVA_HOME` points at a JDK 17 installation before invoking Maven from the command line:
> ```powershell
> $env:JAVA_HOME = "C:\Users\<you>\.jdks\temurin-17"
> .\mvnw.cmd spring-boot:run
> ```

On first startup, Flyway runs `V1__create_users_table.sql` and `V2__create_user_emails_table.sql`. On subsequent startups it detects they have already been applied and skips them. Hibernate validates that the live schema matches the entity model and aborts startup if they diverge.

The application starts on `http://localhost:8080`.

### Step 4 — Open Swagger UI

Navigate to: **http://localhost:8080/swagger-ui.html**

Click **Authorize** (top right), enter the bearer token value from `application.properties`:

```
super-secret-scim-token-change-me
```

You can now test all five endpoints interactively in the browser. Request and response schemas are documented inline.

### Step 5 — Stop

```bash
docker-compose down          # stops MySQL, data volume preserved
docker-compose down -v       # stops MySQL and deletes the data volume (clean slate)
```

---

## Running Tests

Tests use H2 in-memory database — **no Docker, no MySQL needed**:

```bash
./mvnw test
```

The test profile (`@ActiveProfiles("test")`) activates `application-test.properties`, which:
- Points Spring at an H2 in-memory database in MySQL compatibility mode
- Disables Flyway (Flyway scripts use MySQL DDL that H2 does not fully understand even in `MODE=MySQL`; H2 manages the schema via `ddl-auto=create-drop` instead)
- Overrides the auth token to `test-token-12345`
- Uses `H2Dialect` to avoid MySQL-specific SQL generation

All 19 tests should pass: **13 MockMvc integration tests** and **6 Mockito unit tests**.

### Integration tests (`UserControllerTest`)

Full Spring Boot context is loaded with `@SpringBootTest` + `@AutoConfigureMockMvc`. The real Spring Security filter chain is active, so auth tests (`401` scenarios) reflect actual production behaviour — there is no mocking of security. Every test method is `@Transactional` so the H2 database is rolled back after each test, preventing state leakage between tests.

Tests cover:
- `POST` happy path, no token, wrong token, duplicate userName, multiple primary emails, blank userName
- `GET /{id}` happy path, not found
- `GET` list happy path, filter match, unsupported filter
- `PUT /{id}` happy path
- `DELETE /{id}` happy path + confirmation via GET, not found

### Unit tests (`UserServiceTest`)

No Spring context — `@ExtendWith(MockitoExtension.class)` only. The repository, mapper, and filter parser are all Mockito mocks. These tests run in milliseconds and verify business rules in isolation:
- Create with unique userName → saves and returns DTO
- Create with duplicate userName → throws `DuplicateUserNameException`, `save()` never called
- Get by id → delegates to repository and mapper
- Get by unknown id → throws `UserNotFoundException`
- Delete by id → calls `repository.delete(entity)`
- Delete by unknown id → throws `UserNotFoundException`, `delete()` never called

---

## Sample curl Commands

Replace `<TOKEN>` with `super-secret-scim-token-change-me` (or whatever is set in `application.properties`).

### Create a user
```bash
curl -s -X POST http://localhost:8080/scim/v2/Users \
  -H "Authorization: Bearer super-secret-scim-token-change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": "alice@example.com",
    "externalId": "okta-001",
    "name": {
      "givenName": "Alice",
      "familyName": "Smith",
      "formatted": "Alice Smith"
    },
    "emails": [
      { "value": "alice@example.com", "type": "work", "primary": true }
    ],
    "active": true
  }' | jq .
```
Returns **201 Created** with the server-assigned `id` and `meta`.

### Get one user
```bash
curl -s http://localhost:8080/scim/v2/Users/{id} \
  -H "Authorization: Bearer super-secret-scim-token-change-me" | jq .
```

### List all users (paginated)
```bash
curl -s "http://localhost:8080/scim/v2/Users?startIndex=1&count=10" \
  -H "Authorization: Bearer super-secret-scim-token-change-me" | jq .
```

### Filter by userName
```bash
curl -s "http://localhost:8080/scim/v2/Users?filter=userName%20eq%20%22alice%40example.com%22" \
  -H "Authorization: Bearer super-secret-scim-token-change-me" | jq .
```
(URL-decoded filter: `userName eq "alice@example.com"`)

### Filter by externalId
```bash
curl -s "http://localhost:8080/scim/v2/Users?filter=externalId%20eq%20%22okta-001%22" \
  -H "Authorization: Bearer super-secret-scim-token-change-me" | jq .
```

### Full update (replace) a user
```bash
curl -s -X PUT http://localhost:8080/scim/v2/Users/{id} \
  -H "Authorization: Bearer super-secret-scim-token-change-me" \
  -H "Content-Type: application/json" \
  -d '{
    "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
    "userName": "alice@example.com",
    "name": {
      "givenName": "Alicia",
      "familyName": "Smith",
      "formatted": "Alicia Smith"
    },
    "emails": [
      { "value": "alice@example.com", "type": "work", "primary": true }
    ],
    "active": true
  }' | jq .
```
Returns **200 OK** with the updated resource.

### Delete a user
```bash
curl -s -X DELETE http://localhost:8080/scim/v2/Users/{id} \
  -H "Authorization: Bearer super-secret-scim-token-change-me"
```
Returns **204 No Content** on success.

### Test auth failure
```bash
curl -s -X GET http://localhost:8080/scim/v2/Users \
  -H "Authorization: Bearer wrong-token" | jq .
```
Returns **401** with SCIM error body.

---

## Configuration Reference

All configuration lives in `src/main/resources/application.properties`. Test overrides are in `src/test/resources/application-test.properties`.

| Property | Default | Description |
|---|---|---|
| `server.port` | `8080` | Embedded Tomcat port |
| `spring.datasource.url` | `jdbc:mysql://localhost:3306/scimdb?...` | MySQL JDBC URL |
| `spring.datasource.username` | `scim` | DB user (matches docker-compose) |
| `spring.datasource.password` | `scim123` | DB password (matches docker-compose) |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Hibernate verifies schema against entities; Flyway owns DDL |
| `spring.jpa.show-sql` | `true` | Logs all SQL to stdout; disable in production |
| `scim.auth.token` | `super-secret-scim-token-change-me` | Static bearer token; change before deploying |
| `springdoc.api-docs.path` | `/api-docs` | OpenAPI JSON endpoint |
| `springdoc.swagger-ui.path` | `/swagger-ui.html` | Swagger UI URL |
| `logging.level.com.amrit.scim` | `DEBUG` | Application log level |

---

## SCIM Compliance Matrix

### Implemented (RFC 7643 / RFC 7644)

| Feature | RFC Reference | Notes |
|---|---|---|
| `POST /scim/v2/Users` | RFC 7644 §3.3 | Creates user; returns 201 with server-assigned id and meta |
| `GET /scim/v2/Users/{id}` | RFC 7644 §3.4.1 | Returns single user or 404 SCIM error |
| `GET /scim/v2/Users` | RFC 7644 §3.4.2 | Paginated ListResponse with totalResults, startIndex, itemsPerPage |
| `PUT /scim/v2/Users/{id}` | RFC 7644 §3.5.1 | Full replacement; preserves id and meta.created |
| `DELETE /scim/v2/Users/{id}` | RFC 7644 §3.6 | Hard delete; 204 on success, 404 if not found |
| `userName eq "value"` filter | RFC 7644 §3.4.2.2 | Exact equality match (not LIKE) |
| `externalId eq "value"` filter | RFC 7644 §3.4.2.2 | Exact equality match |
| ListResponse envelope | RFC 7644 §3.4.2 | schemas, totalResults, startIndex, itemsPerPage, Resources |
| SCIM Error schema on all 4xx/5xx | RFC 7644 §3.12 | schemas, status, scimType (where applicable), detail |
| Bearer token authentication | RFC 7644 §2 | Required on all `/scim/v2/**` routes |
| `schemas` field | RFC 7643 §3.1 | Always returned; defaulted in DTO |
| `id` — server-assigned UUID | RFC 7643 §3.1 | Never modifiable by client |
| `userName` — required, unique | RFC 7643 §4.1 | 400 if blank; 409 if taken |
| `externalId`, `name`, `emails`, `active` | RFC 7643 §4.1 | All optional except userName |
| `meta.resourceType`, `created`, `lastModified`, `location` | RFC 7643 §3.1 | Server-managed; absolute URI in location |
| Single-primary constraint on emails | RFC 7643 §2.4 | 400 `invalidValue` if more than one email has primary=true |

### Not implemented (out of scope for v1)

| Feature | RFC Reference | Notes |
|---|---|---|
| `PATCH /scim/v2/Users/{id}` | RFC 7644 §3.5.2 | Partial update with patch operations; requires an op parser |
| `/scim/v2/Groups` | RFC 7643 §4.2 | Group management and membership |
| `/scim/v2/Schemas` | RFC 7644 §7 | Schema discovery endpoint |
| `/scim/v2/ServiceProviderConfig` | RFC 7644 §5 | Capability discovery |
| `/scim/v2/ResourceTypes` | RFC 7644 §6 | Resource type discovery |
| Bulk operations | RFC 7644 §3.7 | Batch create/update/delete |
| ETags / `If-Match` | RFC 7644 §3.14 | Optimistic concurrency control |
| Complex filter expressions | RFC 7644 §3.4.2.2 | AND, OR, NOT, nested attribute paths |
| Enterprise User Schema extension | RFC 7643 §4.3 | `urn:ietf:params:scim:schemas:extension:enterprise:2.0:User` |
| Soft delete (`active=false`) | — | Currently a hard DELETE; no audit history preserved |

**References:** [RFC 7643](https://datatracker.ietf.org/doc/html/rfc7643) · [RFC 7644](https://datatracker.ietf.org/doc/html/rfc7644)

---

## Design Decisions

### 1. Why a separate `ScimUser` DTO and `UserEntity`?

The `UserEntity` is the persistence model — it maps directly to the `users` table, has JPA annotations (`@Entity`, `@Column`, `@OneToMany`), and Hibernate manages its lifecycle. The `ScimUser` DTO is the API model — it matches the SCIM wire format (nested `name`, `emails` list, `schemas` array, `meta`), has Jackson serialisation behaviour, and carries Bean Validation annotations.

Keeping them separate means:
- Renaming a database column does not change what JSON the API returns.
- Adding a new SCIM field does not require a database migration.
- Each class has exactly one responsibility.

`UserMapper` is the explicit translation layer between them. It assigns the server-generated UUID and timestamps (Java `UUID.randomUUID()` and `Instant.now()`) so neither the controller nor the service needs to know how IDs are generated.

### 2. Why exact-equality for `eq` filter and not LIKE/contains?

RFC 7644 §3.4.2.2 defines `eq` as exact equality. A `LIKE '%value%'` query would match `alice@example.com` when filtering for `ice@example.com` — incorrect. Okta and Entra ID send `eq` filters expecting exact results; returning extra users would cause the IdP to match against the wrong account.

The repository uses `findByUserName(String, Pageable)` which Spring Data JPA translates to `WHERE user_name = ?` — exact equality. The earlier `findByUserNameContainingIgnoreCase` was a semantic bug.

### 3. How N+1 queries are prevented

Without intervention, fetching a page of 10 users with `FetchType.EAGER` on emails would produce 11 queries: one for the users list, then one `SELECT user_emails WHERE user_id=?` per user. At 100 users per page, that is 101 queries.

The solution is two-pronged:
- **Single-user reads** (GET by id, GET by userName): `@EntityGraph(attributePaths = "emails")` on the repository methods forces Hibernate to use a `JOIN FETCH`, loading user + emails in one query.
- **List reads**: `FetchType.LAZY` + `@BatchSize(size = 25)` on the `emails` collection. When Hibernate initialises the collection for multiple users, it groups the loads into batches: `SELECT * FROM user_emails WHERE user_id IN (?, ?, ... up to 25)`. A page of 100 users produces at most 5 secondary queries instead of 100.

### 4. Why emails are in a separate table, not a JSON column

A `TEXT` or `JSON` column storing the emails array would be simpler to read and write in Java. The downsides:
- MySQL cannot enforce referential integrity or uniqueness constraints on JSON content.
- Querying "find all users with this email address" would require a full-table scan or a generated column.
- Schema migrations affecting email structure are invisible to the DB — you cannot `ALTER TABLE` a JSON field.

The `@OneToMany` relationship with a `user_emails` table gives proper relational modelling: the foreign key constraint is enforced by the DB, cascade delete is handled automatically, and indexed lookups on email values are possible.

### 5. Why the bearer token filter is not a Spring `@Bean`

`OncePerRequestFilter` annotated with `@Bean` causes Spring Boot's servlet auto-configuration to detect it and register it as a standalone `Filter` in the servlet container in addition to inserting it into the security chain. The filter then runs twice per request.

`OncePerRequestFilter`'s internal guard (it marks requests with a per-request attribute on first execution) prevents double execution in most cases, but the design is architecturally wrong and can break under certain request forwarding scenarios. The correct approach is to construct the filter as a plain instance (private factory method, not a bean) and add it to the security chain directly via `.addFilterBefore(...)`.

### 6. Why Flyway instead of `ddl-auto=update`

`ddl-auto=update` is convenient in development but dangerous in production: it silently modifies the live schema based on what Hibernate thinks it should look like, with no record of what changed, no rollback path, and no review process. A renamed field causes an old column to be abandoned with its data.

Flyway gives:
- A versioned, sequential record of every schema change
- The ability to review DDL in code review before it runs
- Repeatable CI: the same migrations run in the same order every time
- Safe rollback: you write a new migration to undo a change

`spring.jpa.hibernate.ddl-auto=validate` means if you add a field to the entity but forget to write the migration, startup fails immediately rather than silently diverging.

---

## Future Work

- **`PATCH` support** — partial updates using SCIM patch operations (RFC 7644 §3.5.2); requires a proper patch-op parser that handles `add`, `replace`, `remove` operations with path expressions
- **`/Groups` endpoints** — group management and membership (RFC 7643 §4.2); requires `groups` and `group_members` tables and a `GroupController`/`GroupService`
- **ETags / conditional updates** — `ETag` header + `If-Match` for optimistic concurrency control (RFC 7644 §3.14); requires a `version` column and `@Version` on the entity
- **Full filter grammar** — replace the regex parser with an ANTLR-based implementation supporting `AND`, `OR`, `NOT`, and nested attribute paths (e.g. `emails.value eq "x@y.com"`)
- **JWT authentication** — replace the static token with `spring-boot-starter-oauth2-resource-server` for proper OAuth 2.0 / OIDC; the IdP signs the JWT and this service validates it against the JWKS endpoint
- **Soft delete** — set `active=false` on DELETE rather than hard-deleting, preserving audit history and allowing account reactivation
- **Multi-tenancy** — partition users by tenant ID so the same service can host multiple customers; requires a `tenant_id` column and tenant-scoped uniqueness constraints
- **`/ServiceProviderConfig` + `/Schemas` endpoints** — allow IdPs to discover capabilities (pagination support, filter support, authentication schemes) at runtime without manual configuration
- **Additional Flyway migrations** — index on `external_id` for faster externalId-filter queries; index on `email_value` for future email-based lookups
- **Metrics + tracing** — `spring-boot-starter-actuator` + Micrometer for Prometheus metrics; OpenTelemetry for distributed tracing in multi-service deployments
