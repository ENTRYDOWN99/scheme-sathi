# Scheme Saathi – Unified Architecture & Security Specification

## 1. System Overview
**Scheme Saathi** is a bilingual (Hindi `hi` / English `en`, extensible i18n) Government Welfare Scheme Assistant and Collaborative Task Management platform delivered in two client forms powered by **one shared backend**:
1. **Shared Backend (`/backend`)**: Node.js 24 + TypeScript + Express REST API with OpenAPI 3.0 / Swagger UI (`/api/docs`), JWT + rotating Refresh Token auth, server-side RBAC (`CITIZEN`, `CSC_OPERATOR`, `SUPERVISOR`, `REVIEWER`), deterministic Eligibility Engine (single source of truth), AES-256-GCM PII encryption at rest, rate limiting, audit logging, and a resilient Gemini AI service wrapper (with retries, timeouts, and deterministic offline fallback). Retains `firestore.rules` for Firebase emulator rules verification.
2. **Responsive Website (`/web`)**: Next.js 15 (App Router, TypeScript) + Tailwind CSS PWA with `httpOnly` cookie session handling, SEO landing page, full feature parity, WCAG AA accessibility, Light/Dark mode, and Large Text toggle.
3. **Native Mobile App (`/app`)**: Native Android (Kotlin, Jetpack Compose, Material 3) connected to the shared `/backend` REST API (via Retrofit + OkHttp + Keystore-secured token storage), Room offline cache for schemes and checklists, local push notifications for alerts, and localized string resources (`res/values*`).

```text
+-----------------------+       HTTPS / REST JSON        +----------------------------------+
|  Next.js Web PWA      | -----------------------------> |  Shared Backend API (/backend)   |
|  (/web, httpOnly JWT) |                                |  • Auth (OTP/Password, JWT+Ref)  |
+-----------------------+                                |  • RBAC Middleware (4 Roles)     |
                                                         |  • Deterministic Rule Engine     |
+-----------------------+       HTTPS / REST JSON        |  • OpenAPI 3.0 (/api/docs)       |
|  Android Compose App  | -----------------------------> |  • AES-256-GCM PII Vault         |
|  (/app, Keystore JWT) |                                |  • Audit Logs & Rate Limiter     |
|  + Room Offline Cache |                                +--------+----------------+--------+
+-----------------------+                                         |                |
                                                                  v                v
                                                         +---------------+  +---------------+
                                                         | SQLite / PG   |  | Gemini AI API |
                                                         | Migrations &  |  | (Retries +    |
                                                         | Seed Data     |  | Fallback)     |
                                                         +---------------+  +---------------+
```

## 2. Core Architectural Layers

### 2.1 Shared Design System & i18n (`/shared`)
- **`shared/design-tokens`**: Canonical color tokens (`#1B5E3F` Civic Green, `#E07B00` Accent Saffron, `#F6F4EF` Cream background, semantic `ELIGIBLE`/`UNKNOWN`/`NOT_ELIGIBLE` colors, Light & Dark modes) and typography scale (`18sp`/`22sp` Large Text mode).
- **`shared/i18n`**: Structured JSON localization bundles (`en.json`, `hi.json`) consumed by `/web` and synchronized with Android `strings.xml` / `values-hi/strings.xml`, extensible to additional Indian languages (`mr`, `bn`, `ta`, etc.).
- **`shared/api-client`**: OpenAPI 3.0 specification and typed TypeScript REST client shared by `/web` and integration tests.

### 2.2 Backend (`/backend`)
- **Database & Migrations**: Relational schema (`users`, `refresh_tokens`, `citizen_profiles`, `schemes`, `document_checklists`, `applications`, `tasks`, `audit_logs`) with SQL migrations and deterministic seed data (`PM-KISAN`, `NSAP-IGNOAPS`, `PMUY`, `PM-JAY`, `APY` + 4 role sample accounts). Supports SQLite (`node:sqlite`) for fast local/CI testing and PostgreSQL (`pg`) via `DATABASE_URL` in Docker/production.
- **Deterministic Eligibility Engine (`backend/src/domain/eligibilityEngine.ts`)**: Single source of truth evaluating `ALL` and `ANY` combinators (`EQ`, `NEQ`, `LT`, `LTE`, `GT`, `GTE`, `IN`, `NOT_IN`, `BETWEEN`, `EXISTS`, `EXTERNAL_LOOKUP`), state income overrides, DOB birthday age math (`DD/MM/YYYY`), voice age confirmation gates, and `UNKNOWN` handling.
- **AI Gateway (`backend/src/services/aiService.ts`)**: Wraps Gemini REST/SDK behind timeouts, exponential retry on transient errors, pre-prompt Aadhaar/prompt-injection/PII redaction, deterministic tool grounding, strict 3-bucket error mapping (`busy` / `network` / `generic`), and full deterministic summary fallback.
- **Security & Compliance**:
  - Helmet security headers, locked-down CORS (`CORS_ORIGIN`), Express rate limiting (`express-rate-limit`), Zod schema validation.
  - Aadhaar regex guard (`\b\d{12}\b` after separator stripping) rejects 12-digit inputs at API boundary without false-positives on dates (`01/01/1964`).
  - Field-level AES-256-GCM encryption at rest (`PII_ENCRYPTION_KEY`) for Name, Phone, and Date of Birth (`DD/MM/YYYY`), unique 12-byte random IV per write.
  - Immutable `audit_logs` table recording auth, consent, role changes, application confirmation, status verification, and task actions without logging PII.
  - DPDP endpoints: consent toggle, full JSON data export (`GET /api/v1/profile/export`), and full account erasure (`DELETE /api/v1/profile`).

### 2.3 Website (`/web`)
- Next.js 15 App Router + TypeScript + Tailwind CSS, installable PWA (`manifest.webmanifest` + service worker).
- Full bilingual parity (`hi`/`en`), Large Text mode, Light/Dark theme switcher, WCAG AA keyboard navigation & skip links, SEO-friendly landing page.
- Complete workflows: OTP/demo login, role-aware dashboard (Citizen, CSC Operator queue, Reviewer verification, Supervisor audit), profile with DD/MM/YYYY calendar picker + auto-slash formatting, deterministic eligibility breakdown, persistent document checklist, guided application form with Human Confirmation Gate (SHA-256 snapshot verification), AI Chat + Web Speech voice assistant, Alerts/Tasks, and DPDP Settings.

### 2.4 Android App (`/app`)
- Native Kotlin + Jetpack Compose (Material 3) preserving all existing screens while connecting to `/backend` REST API via Retrofit + OkHttp (`ApiClient.kt` + `TokenStore.kt` backed by Android Keystore AES-GCM encryption).
- Offline-first Room cache (`SchemeSaathiDatabase`) for schemes, rules evaluation snapshots, and document checklists so users in low-connectivity rural areas can continue working offline and sync when reconnected.
- Local Android `NotificationManager` channel for task & application alert notifications.
- Externalized strings in `res/values/strings.xml` and `res/values-hi/strings.xml`.
