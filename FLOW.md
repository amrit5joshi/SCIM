# How This App Works — Complete Flow Guide

---

## What This App Is

This is a **SCIM Server** (also called a "Service Provider" in SCIM terminology).

It is the bridge between your application's user database and an enterprise Identity Provider (IdP) like Okta or Microsoft Entra ID. When a company's IT admin manages employees in their IdP, this server automatically keeps your app's users in sync — no manual account creation, no manual deletion.

---

## The Big Picture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     COMPANY IT ADMIN                                │
│                                                                     │
│         "Add Alice to our systems"                                  │
│                   │                                                 │
│                   ▼                                                 │
│  ┌────────────────────────────────┐                                 │
│  │   Identity Provider (IdP)      │  ← Okta / Microsoft Entra ID   │
│  │                                │                                 │
│  │  Stores: all company employees │                                 │
│  │  Role:   single source of truth│                                 │
│  │          for who works here    │                                 │
│  └────────────────────────────────┘                                 │
│                   │                                                 │
│                   │  Okta automatically calls every app             │
│                   │  configured for this company                    │
│                   │                                                 │
└───────────────────┼─────────────────────────────────────────────────┘
                    │
        POST /scim/v2/Users
        Authorization: Bearer <token>
        {
          "userName": "alice@company.com",
          "name": { "givenName": "Alice" },
          "emails": [{ "value": "alice@company.com" }],
          "active": true
        }
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                YOUR SCIM SERVER  (this project)                     │
│                                                                     │
│  1. Validates the bearer token                                      │
│  2. Checks userName is not a duplicate                              │
│  3. Saves the user to MySQL                                         │
│  4. Returns 201 with server-assigned UUID + meta                    │
│                                                                     │
│  Running at:  http://your-server:8080/scim/v2/Users                 │
└─────────────────────────────────────────────────────────────────────┘
                    │
                    │  INSERT INTO users ...
                    │
                    ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     MySQL 8 DATABASE                                │
│                                                                     │
│  Table: users                                                       │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ id (UUID) │ user_name          │ active │ created            │   │
│  │───────────│────────────────────│────────│────────────────────│   │
│  │ 550e8400  │ alice@company.com  │ 1      │ 2024-01-15 09:00   │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  Table: user_emails                                                 │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ id │ user_id   │ email_value        │ type │ primary_email   │   │
│  │────│───────────│────────────────────│──────│─────────────────│   │
│  │ 1  │ 550e8400  │ alice@company.com  │ work │ 1               │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Where Everything Sits

| Component | Where it lives | What it stores |
|---|---|---|
| **Identity Provider (Okta / Entra ID)** | Okta's cloud / Microsoft's cloud | Master list of all company employees, their roles, groups |
| **Your SCIM Server** (this project) | Your server / AWS / Azure / any cloud | HTTP API that receives provisioning requests |
| **MySQL Database** | Your database server / RDS / Cloud SQL | Your app's own copy of the users that have been provisioned |

**Key point:** The IdP is the **source of truth** for who exists. Your MySQL is your **local copy** of the users the IdP has told you about. They stay in sync via SCIM calls.

---

## Full Lifecycle — What Happens at Each Stage

### Stage 1 — Employee Joins the Company

```
IT Admin adds "Alice" in Okta
        │
        ▼
Okta calls:  POST /scim/v2/Users
        │
        ▼
Your SCIM server creates Alice in MySQL
        │
        ▼
Alice can now log in to your application
```

### Stage 2 — Employee Details Change

```
IT Admin updates Alice's name in Okta
        │
        ▼
Okta calls:  PUT /scim/v2/Users/{alice-uuid}
        │
        ▼
Your SCIM server updates Alice's record in MySQL
```

### Stage 3 — Employee Leaves the Company

```
IT Admin deactivates Alice in Okta
        │
        ▼
Okta calls:  DELETE /scim/v2/Users/{alice-uuid}
        │
        ▼
Your SCIM server deletes Alice from MySQL
        │
        ▼
Alice can no longer log in to your application
        (access revoked automatically — no manual work)
```

### Stage 4 — Okta Checks Who Already Exists

```
Okta wants to sync / verify users already in your system
        │
        ▼
Okta calls:  GET /scim/v2/Users?filter=userName eq "alice@company.com"
        │
        ▼
Your SCIM server queries MySQL and returns the matching user
        │
        ▼
Okta knows Alice is already provisioned — skips re-creating her
```

---

## Request & Response — What the IdP Actually Sends

### Create User (POST)

**Okta sends →**
```json
{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "userName": "alice@company.com",
  "externalId": "okta-internal-id-abc123",
  "name": {
    "givenName": "Alice",
    "familyName": "Smith",
    "formatted": "Alice Smith"
  },
  "emails": [
    { "value": "alice@company.com", "type": "work", "primary": true }
  ],
  "active": true
}
```

**Your SCIM server responds with 201 →**
```json
{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "userName": "alice@company.com",
  "externalId": "okta-internal-id-abc123",
  "name": { "givenName": "Alice", "familyName": "Smith", "formatted": "Alice Smith" },
  "emails": [{ "value": "alice@company.com", "type": "work", "primary": true }],
  "active": true,
  "meta": {
    "resourceType": "User",
    "created": "2024-01-15T09:00:00.000Z",
    "lastModified": "2024-01-15T09:00:00.000Z",
    "location": "https://your-app.com/scim/v2/Users/550e8400-e29b-41d4-a716-446655440000"
  }
}
```

Okta stores the `id` (`550e8400...`) you returned. Every future update or delete for Alice uses that UUID.

---

## How to Connect This App to an Identity Provider

### Prerequisites
- This app must be **publicly accessible** (not just localhost) — Okta needs to reach it over the internet
- Deploy to any cloud: AWS EC2, Azure App Service, Railway, Render, DigitalOcean, etc.
- Your URL will look like: `https://your-app.com`

---

### Option A — Connect to Okta (Step by Step)

#### Step 1 — Deploy this app to a public URL

Make sure the app is running and reachable at something like:
```
https://your-app.com/scim/v2/Users
```

#### Step 2 — Log in to Okta Admin Console

Go to `https://your-org.okta.com/admin`

#### Step 3 — Create a new Application

```
Applications → Applications → Create App Integration
→ Select: SWA (or SAML if you want SSO too)
→ Or better: Applications → Browse App Catalog → search "SCIM"
→ Click: Create New App → select SCIM 2.0
```

#### Step 4 — Configure the SCIM connection

Fill in the Provisioning tab:

| Field | Value |
|---|---|
| SCIM connector base URL | `https://your-app.com/scim/v2` |
| Unique identifier field | `userName` |
| Authentication mode | `HTTP Header` |
| Authorization | `Bearer super-secret-scim-token-change-me` |

> **Important:** Change `scim.auth.token` in `application.properties` to a strong random value before deploying. That same value goes in the Okta Authorization field.

#### Step 5 — Enable Provisioning Actions

In the Provisioning → To App tab, enable:
- Push New Users ✅
- Push Profile Updates ✅
- Deactivate Users ✅

#### Step 6 — Assign users or groups to the app

```
Assignments → Assign → Assign to People (or Groups)
```

The moment you assign Alice to the app, Okta immediately calls `POST /scim/v2/Users` on your server. Alice appears in your MySQL database.

#### Step 7 — Test it

In Okta:
```
Provisioning → Import → Import Now
```

Check your MySQL:
```sql
SELECT * FROM users;
SELECT * FROM user_emails;
```

Alice's row should be there.

---

### Option B — Connect to Microsoft Entra ID (Step by Step)

#### Step 1 — Go to Entra ID Admin Center

```
https://entra.microsoft.com
→ Applications → Enterprise Applications → New Application
→ Create your own application → give it a name → Non-gallery
```

#### Step 2 — Set up Provisioning

```
Your App → Provisioning → Get Started
→ Provisioning Mode: Automatic
```

#### Step 3 — Fill in Admin Credentials

| Field | Value |
|---|---|
| Tenant URL | `https://your-app.com/scim/v2` |
| Secret Token | `super-secret-scim-token-change-me` |

Click **Test Connection** — Entra ID calls `GET /scim/v2/Users` and expects a 200. If it gets one, the connection is valid.

#### Step 4 — Configure Attribute Mappings

Entra ID shows you a mapping of its fields to SCIM fields. The defaults work:
- `userPrincipalName` → `userName`
- `givenName` → `name.givenName`
- `surname` → `name.familyName`
- `mail` → `emails[type eq "work"].value`

#### Step 5 — Start Provisioning

```
Provisioning → Start Provisioning
```

Entra ID begins an initial sync cycle — it calls `GET /scim/v2/Users` first to see who already exists, then `POST /scim/v2/Users` for every assigned user who is not yet in your system.

---

## The Token — How Authentication Works

The IdP and your SCIM server share a **static secret token**. Think of it like an API key.

```
application.properties:
  scim.auth.token=super-secret-scim-token-change-me
                          │
                          │ same value configured in Okta/Entra
                          ▼
Every request from Okta:
  Authorization: Bearer super-secret-scim-token-change-me
```

Your `SecurityConfig` reads every incoming `Authorization` header and compares it to `scim.auth.token`. If they match, the request proceeds. If not, a 401 SCIM error is returned immediately — no controller code runs.

**For production**, replace the static token with a proper OAuth 2.0 setup:
- The IdP acts as the OAuth client
- Your app validates a signed JWT from the IdP's JWKS endpoint
- Token rotation happens automatically — no manual secret updates

---

## Summary — Who Does What

| Who | Responsibility |
|---|---|
| **IT Admin** | Adds/removes employees in the IdP once |
| **Okta / Entra ID** | Calls your SCIM endpoints automatically when users change |
| **Your SCIM Server** (this app) | Receives the calls, validates them, writes to MySQL |
| **MySQL** | Stores your app's local copy of provisioned users |
| **Your Application** | Reads users from MySQL to handle login, permissions, etc. |

The IT admin touches nothing in your app directly. SCIM is the automated pipe between the company's central user directory and every application they use.
