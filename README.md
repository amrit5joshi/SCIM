# SCIM 2.0 User Provisioning Service

A production-style SCIM 2.0 user provisioning API built with Java 17 and Spring Boot 3, backed by MySQL — designed as a portfolio/interview project demonstrating real-world enterprise integration patterns.

---

## What It Does

Modern B2B SaaS applications need to automatically synchronise user accounts with their customers' Identity Providers (IdPs) such as Okta, Microsoft Entra ID (formerly Azure AD), or OneLogin. When a company's IT team adds a new employee in Okta, Okta needs a standardised way to push that new account into every SaaS product the company uses. The System for Cross-domain Identity Management (SCIM) 2.0 protocol — defined in RFC 7643 and RFC 7644 — is that standard.

This service acts as the **SCIM server** (sometimes called the "service provider"). It exposes a REST API that an IdP can call to create, read, update, and delete user accounts. When a company administrator adds a new employee in their IdP, the IdP sends a `POST /scim/v2/Users` request here; when they deactivate an employee, the IdP sends a `DELETE /scim/v2/Users/{id}`. The service persists users in MySQL and returns responses in the SCIM-mandated JSON shape, so any standards-compliant IdP can plug in without custom integration work.

---

## Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Language | Java | 17 |
| Framework | Spring Boot | 3.2.5 |
| Web | Spring MVC (embedded Tomcat) | — |
| Persistence | Spring Data JPA + Hibernate | — |
| Database | MySQL | 8.0 |
| Security | Spring Security | — |
| Validation | Jakarta Bean Validation (Hibernate Validator) | — |
| Boilerplate reduction | Lombok | — |
| API docs | springdoc-openapi (Swagger UI) | 2.5.0 |
| Build | Maven | 3.x |
| Testing | JUnit 5 + Mockito + MockMvc + H2 | — |
| Containerisation | Docker / Docker Compose | — |

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+ (or use the included `./mvnw` wrapper)
- Docker Desktop (for MySQL)

### Step 1 — Clone the repository
```bash
git clone https://github.com/amrit/scim-provisioning-service.git
cd scim-provisioning-service
```

### Step 2 — Start MySQL with Docker Compose
```bash
docker-compose up -d
```
This starts a MySQL 8 container on port `3306` with database `scimdb`, user `scim`, password `scim123`.  
Wait a few seconds for MySQL to initialise, then verify:
```bash
docker-compose ps   # should show "healthy" or "Up"
```

### Step 3 — Start the application
```bash
./mvnw spring-boot:run
```
Spring Boot will auto-create the `users` and `user_emails` tables on first startup (Hibernate `ddl-auto=update`).

> **Note on JDK version:** This project targets Java 17. If you are running IntelliJ IDEA 2026.x (which ships JBR 25), set `JAVA_HOME` to a JDK 17 or 21 installation before using Maven on the command line:
> ```powershell
> # Windows PowerShell example — path matches the Temurin JDK downloaded by this project
> $env:JAVA_HOME = "C:\Users\<you>\.jdks\temurin-17"
> ```
> The `.mvn/jvm.config` file contains `--add-opens` flags that allow IntelliJ's bundled Maven to run on Java 25 if you haven't set `JAVA_HOME`. For cleanest results, point `JAVA_HOME` at JDK 17/21.

### Step 4 — Open Swagger UI
Navigate to: **http://localhost:8080/swagger-ui.html**

Click **Authorize** (top right), enter `super-secret-scim-token-change-me`, and you can test all endpoints directly in the browser.

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
Returns **201 Created** with the full resource including the server-generated `id` and `meta`.

### Get one user (replace `{id}` with the UUID from the create response)
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

### Replace (full update) a user
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

### Delete a user
```bash
curl -s -X DELETE http://localhost:8080/scim/v2/Users/{id} \
  -H "Authorization: Bearer super-secret-scim-token-change-me"
```
Returns **204 No Content** on success.

---

## Running Tests

Tests use H2 in-memory DB — **no Docker needed**:
```bash
./mvnw test
```
All 19 tests should pass: 13 MockMvc integration tests (`UserControllerTest`) and 6 Mockito unit tests (`UserServiceTest`).

> **Two fixes applied for H2 compatibility:**
> 1. The `email_value` column name (instead of `value`) avoids an H2 reserved-keyword conflict.
> 2. `NON_KEYWORDS=VALUE` in the H2 JDBC URL and `H2Dialect` in test properties prevent `ENGINE=InnoDB` DDL errors.

---

## SCIM Compliance Notes

### ✅ Implemented (per RFC 7643 / RFC 7644)
- `POST /scim/v2/Users` — create user (RFC 7644 §3.3)
- `GET /scim/v2/Users/{id}` — retrieve user (RFC 7644 §3.4.1)
- `GET /scim/v2/Users` — list with pagination (RFC 7644 §3.4.2)
- `PUT /scim/v2/Users/{id}` — full replacement (RFC 7644 §3.5.1)
- `DELETE /scim/v2/Users/{id}` — hard delete (RFC 7644 §3.6)
- `userName eq "value"` filter expression (RFC 7644 §3.4.2.2)
- Paginated `ListResponse` envelope with `totalResults`, `startIndex`, `itemsPerPage` (RFC 7644 §3.4.2)
- SCIM Error schema on all 4xx/5xx responses (RFC 7644 §3.12)
- Bearer token authentication on all `/scim/v2/**` routes (RFC 7644 §2)
- `schemas`, `id`, `userName`, `externalId`, `name`, `emails`, `active`, `meta` fields (RFC 7643 §4.1)
- Server-assigned UUID for `id` (RFC 7643 §3.1)
- Server-managed `meta.created`, `meta.lastModified`, `meta.location` (RFC 7643 §3.1)

### ❌ Not Implemented (out of scope for v1)
- `PATCH /scim/v2/Users/{id}` — partial update (RFC 7644 §3.5.2)
- `/scim/v2/Groups` and Group membership (RFC 7643 §4.2)
- `/scim/v2/Schemas` discovery endpoint (RFC 7644 §7)
- `/scim/v2/ServiceProviderConfig` (RFC 7644 §5)
- `/scim/v2/ResourceTypes` (RFC 7644 §6)
- Bulk operations (RFC 7644 §3.7)
- ETags / conditional updates (`If-Match` header) (RFC 7644 §3.14)
- Complex filter expressions (AND, OR, nested attributes)
- Enterprise User Schema extension (RFC 7643 §4.3)

**References:** [RFC 7643](https://datatracker.ietf.org/doc/html/rfc7643) · [RFC 7644](https://datatracker.ietf.org/doc/html/rfc7644)

---

## Design Decisions

### 1. Why a separate `ScimUser` DTO vs `UserEntity`?

The `UserEntity` is the **persistence model** — it maps directly to the MySQL `users` table. The `ScimUser` DTO is the **API model** — it matches the SCIM wire format (nested `name`, `emails` list, `schemas` array).

Keeping them separate (with `UserMapper` translating between them) means:
- A DB schema change (e.g. renaming a column) doesn't break the API contract.
- An API change (e.g. adding a new SCIM field) doesn't force a DB migration.
- Each class has a single responsibility: entities know about JPA, DTOs know about JSON.

This is the classic **DTO pattern** — a standard architecture question in interviews.

### 2. How filtering is parsed (and why only `userName eq` is supported)

`ScimFilterParser` uses a single compiled `Pattern` that matches exactly `userName eq "value"`. Anything else throws an `InvalidFilterException` (→ 400).

The SCIM filter grammar is actually a full expression language (RFC 7644 §3.4.2.2) supporting AND, OR, NOT, and nested paths. A production implementation would use an ANTLR grammar or the `scim2-sdk` library. For a portfolio project, the regex approach is honest — it does exactly what it claims, and the 400 error for unsupported filters is SCIM-spec compliant.

### 3. How errors are mapped to the SCIM error schema

`ScimExceptionHandler` (`@RestControllerAdvice`) intercepts every exception type and converts it to a `ScimError` DTO:

| Exception | HTTP Status | `scimType` |
|---|---|---|
| `UserNotFoundException` | 404 | _(none)_ |
| `DuplicateUserNameException` | 409 | `uniqueness` |
| `InvalidFilterException` | 400 | `invalidFilter` |
| `MethodArgumentNotValidException` | 400 | `invalidValue` |
| Auth failure (in filter) | 401 | _(none)_ |
| Anything else | 500 | _(none)_ |

Centralising this means controllers have no try/catch blocks — they stay clean and focused on HTTP translation.

### 4. The emails-storage tradeoff

Emails are stored in a separate `user_emails` table with a `@OneToMany` relationship to `users`.

**Why not a JSON column?**  
A JSON column (`TEXT` or MySQL's native `JSON`) would be simpler to read/write in Java. However, you lose:
- Referential integrity (MySQL can't index or constrain JSON array contents)
- The ability to query "find all users with email X@Y.com" efficiently
- Schema clarity (any tooling that inspects the DB sees a typed schema)

**Trade-off accepted:** the `@OneToMany` relationship means a JOIN on every user read. For a provisioning service (low-traffic, admin-only) this is perfectly acceptable, and it demonstrates proper relational data modelling.

---

## Future Work

- **`PATCH` support** — partial updates using the SCIM patch operation model (RFC 7644 §3.5.2); requires a proper patch-op parser
- **`/Groups` endpoints** — group management and group membership (RFC 7643 §4.2)
- **ETags / conditional updates** — `ETag` header + `If-Match` for optimistic concurrency control (RFC 7644 §3.14)
- **Full filter grammar** — replace the regex parser with an ANTLR-based implementation supporting AND, OR, NOT
- **JWT authentication** — replace the static token with `spring-boot-starter-oauth2-resource-server` for proper OAuth 2.0 / OIDC
- **Flyway migrations** — replace `ddl-auto=update` with versioned migration scripts for safe production schema changes
- **`/ServiceProviderConfig` + `/Schemas` endpoints** — allow IdPs to discover capabilities at runtime
- **Soft delete / `active=false`** — rather than hard-deleting, set `active=false` to preserve audit history
- **Multi-tenancy** — partition users by tenant ID so the same service can host multiple customers
