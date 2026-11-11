# Scheme Saathi (`saathi`) — Technical Audit & Target Architecture Proposal

> **Note:** Historical audit of the original prototype (Step 0). Superseded: a shared Express backend in `/backend` now serves both `/web` and `/app`. See [ARCHITECTURE.md](ARCHITECTURE.md) for current state.

> **Phase 1 / Step 0 Audit** — Verified directly against repository files on `main` (`4f6a60c`).

---

## 1. Executive Summary

**Scheme Saathi** is currently a native Android (Kotlin + Jetpack Compose) bilingual (`hi` / `en`) prototype backed directly by Firebase Client SDKs (`FirebaseAuth`, `FirebaseFirestore`, `FirebaseAppCheck`, `Firebase.ai`) and a `/backend` folder containing only Cloud Firestore security rules and emulator tests.

While the Android prototype implements strong domain foundations — a pure deterministic 5-scheme rule engine (`EligibilityEngine.kt`), strict PII redaction and Aadhaar blocking (`SecurityUtils.kt`), AES-256-GCM encrypted profile fields, an echo-back voice numeral confirmation gate, and a SHA-256 Human Confirmation Gate — **there is no shared HTTP backend, no web application, no server-side RBAC enforcement, and significant client-side coupling**.

To deliver a production-ready **Website (`/web`)** and **Mobile App (`/app`)** powered by **ONE shared backend (`/backend`)**, this audit details the current system state, security/completeness gaps, tech debt, and the proposed unified architecture.

---

## 2. Current Inventory (Verified from Code)

### 2.1 Tech Stack & Build Configuration
- **Android (`/app`)**: Kotlin `2.2.10`, AGP `9.1.1`, Jetpack Compose BOM `2024.09.00` (Material 3), `minSdk = 24`, `compileSdk = 36`, `targetSdk = 36`, Java 11 (`app/build.gradle.kts`, `gradle/libs.versions.toml`).
- **Application ID**: `com.aistudio.schemesaathi.kxmzpq` (`app/build.gradle.kts:17`).
- **Firebase SDKs (BOM `34.17.0`)**: `firebase-auth`, `firebase-firestore`, `firebase-ai`, `firebase-appcheck-recaptcha`, `firebase-appcheck-debug`.
- **Auth**: Google Sign-In via Jetpack Credential Manager (`GetGoogleIdOption`, `GetSignInWithGoogleOption` in `ui/auth/AuthComponents.kt`).
- **Backend (`/backend`)**: Node `package.json` with `firebase-tools ^13.29.1` and `@firebase/rules-unit-testing ^3.0.3`, `firestore.rules`, `firebase.json` (emulator ports `8085`/`9099`), `firebase-blueprint.json`, `tests/firestore.test.js`. **No REST API server or relational database exists.**

### 2.2 Existing Features & Screens (16 Total)
1. **Authentication Gate (`AuthGateScreen`)** — Unauthenticated root screen with Hindi/English toggle, "no Aadhaar" notice, Google Sign-In button (`ui/screens/OnboardingAndProfileScreens.kt:73`).
2. **Welcome (`AppScreen.WELCOME`)** — Overview of the 5 welfare schemes and non-auto-submit guardrail.
3. **Language & Accessibility (`AppScreen.LANGUAGE_SELECTION`)** — Switch between `hi`/`en`, preview coming-soon Indian languages, toggle Large Text mode (18sp -> 22sp).
4. **Privacy Consent (`AppScreen.CONSENT`)** — DPDP-style consent opt-in/withdrawal (`updateConsent`) and cloud profile deletion.
5. **Profile Setup (`AppScreen.PROFILE_SETUP`)** — Encrypted Name, Encrypted Phone, Encrypted DOB (`DD/MM/YYYY` with Material 3 `DatePickerDialog`, slash auto-insert, leap-year/0–120 validation, backward-compatible `birthYear` fallback), Annual Income, State (6 override options), District, Family Size, Gender, Residence Type, BPL Status, Social Category, Agricultural Land, Bank Account, LPG Connection, Income Tax Payer, and Role selector.
6. **Home Dashboard (`AppScreen.HOME_DASHBOARD`)** — Citizen greeting, eligible/unknown/not-eligible stat cards, quick actions (AI Chat, Voice, Scheme Explorer), TTS read-aloud button (`HomeAndSchemesScreens.kt:75`).
7. **Scheme Discovery (`AppScreen.SCHEME_RESULTS`)** — Searchable list of the 5 seeded schemes with live eligibility outcome pills (`ELIGIBLE`, `UNKNOWN`, `NOT_ELIGIBLE`).
8. **Scheme Details (`AppScreen.SCHEME_DETAILS`)** — Bilingual scheme metadata, illustrative benefit summary, official source URL, CSC instructions.
9. **Eligibility Breakdown (`AppScreen.ELIGIBILITY_RESULTS`)** — Condition-by-condition evaluation explanation, missing field list, state override notices, external lookup prompts.
10. **Document Checklist (`AppScreen.DOCUMENT_CHECKLIST`)** — Per-scheme required documents with `HAVE`, `MISSING`, `UNKNOWN` chips, alternatives, and "how to obtain" guidance (`ApplicationAndTasksScreens.kt:75`).
11. **Application Preparation (`AppScreen.APPLICATION_FORM`)** — Provenance-tracked form fields (`profile`, `user_typed`, `voice_confirmed`) with Aadhaar guard.
12. **Human Confirmation Gate (`AppScreen.REVIEW_CONFIRM`)** — Deterministic SHA-256 snapshot hash (`SecurityUtils.computeSnapshotHash`), explicit uncertainty & accuracy checkboxes, non-auto-submit warning, Guided Handoff Pack (Copy, Share, Open Official Portal).
13. **Application Tracking (`AppScreen.APPLICATION_TRACKING`)** — Distinguishes `SELF-REPORTED` (`verified=false`) from Department `VERIFIED` (`verified=true`), status chips.
14. **AI Chat (`AppScreen.AI_CHAT`)** — Multi-turn Gemini chat with mode switcher (`Standard` -> `gemini-3.8-flash`, `Fast Answers` -> `gemini-3.1-flash-lite`, `Deep Thinking` -> `gemini-4-carbon`), prompt chips, tool execution badges, and offline deterministic fallback.
15. **Voice Assistant (`AppScreen.VOICE_ASSISTANT`)** — Android `SpeechRecognizer` + Hindi/English numeral parsing (`बासठ` = 62) with mandatory Echo-Back Confirmation Gate (`"आपकी उम्र 62 साल है, सही है?"`), Gemini Live Voice turn, and Android `TextToSpeech` playback.
16. **Alerts & Settings (`AppScreen.ALERTS_SETTINGS`)** — Collaborative task creation/status advancement, Calendar intent export, Stage 8 Security Audit summary & CSV export, Sign Out.

### 2.3 Roles (`CitizenProfile.role` & `CollaborativeTask.assigneeRole`)
- `CITIZEN`
- `CSC_OPERATOR`
- `SUPERVISOR`
- `REVIEWER`

### 2.4 API Endpoints & Data Access
- **HTTP REST Endpoints**: **None**. All persistence goes directly from Android client to Cloud Firestore via `SchemeSaathiRepository.kt` (`users/{uid}/profile/main`, `users/{uid}/applications/{id}`, `users/{uid}/tasks/{id}`).
- **AI Calls**: Direct client-side calls via `Firebase.ai` SDK in `SchemeSaathiAiService.kt`.

### 2.5 Data Models (`app/src/main/java/com/example/data/model/SchemeModels.kt` & `backend/firebase-blueprint.json`)
- `CitizenProfile`: `userId`, `encryptedName`, `encryptedPhone`, `encryptedDob`, `birthYear`, `ageConfirmedViaVoice`, `gender`, `state`, `district`, `residenceType`, `annualIncome`, `occupation`, `bplStatus`, `socialCategory`, `hasBankAccount`, `hasLpgConnection`, `ownsAgriculturalLand`, `isIncomeTaxPayer`, `familySize`, `consentGranted`, `preferredLanguage`, `role`, timestamps.
- `GovernmentScheme` / `RuleGroup` / `RuleCondition` / `DocumentRequirement`: Currently **only in Kotlin memory** (`EligibilityEngine.initialFiveSchemes`: `PM-KISAN`, `NSAP-IGNOAPS`, `PMUY`, `PM-JAY`, `APY`).
- `SchemeApplication`: `userId`, `schemeId`, `schemeName`, `ruleVersion`, `status` (`DRAFT`..`ABANDONED`), `verified`, provenance fields (`applicantName*`, `stateDistrict*`, `bankIfsc*`), `checklistSummary`, `snapshotHash`, `explicitCitizenConfirmed`, timestamps.
- `CollaborativeTask`: `userId`, `title`, `description`, `assigneeName`, `assigneeRole`, `priority`, `status` (`PENDING`, `IN_PROGRESS`, `COMPLETED`, `CANCELLED`), `schemeCode`, `dueDateLabel`, `deliveryChannel` (`IN_APP`, `SMS_MOCK`), `deliveryStatus`, `activityLogSummary`, timestamps.
- `ChatMessage`, `AuditFinding`.

### 2.6 AI Integration (`SchemeSaathiAiService.kt`)
- System prompt enforces deterministic grounding, `UNKNOWN` for missing fields, no auto-submit, no Aadhaar, 5-scheme scope, illustrative disclaimer.
- Pre-prompt guards: `SecurityUtils.containsAadhaarNumber`, `detectPromptInjectionOrAutoSubmit`, `redactPiiForLlm`.
- Grounding tools (`executeDeterministicTools`): `get_profile_summary` (sends calculated age only, never DOB), `check_eligibility`, `explain_eligibility`, `get_required_documents`, `list_missing_fields`.
- Model fallback chain:
  - High Thinking: `gemini-4-carbon` -> `gemini-3.8-flash` (`ThinkingLevel.HIGH`) -> `gemini-3.1-flash-lite`
  - Standard: `gemini-3.8-flash` -> `gemini-3.1-flash-lite`
  - Fast: `gemini-3.1-flash-lite` -> `gemini-3.8-flash`
  - TTS prep: `gemini-3.8-flash-tts` -> `gemini-3.8-flash`
- Central error mapper (`mapAiErrorToFriendlyMessage`): categorizes quota (`429`/`resource_exhausted`/`limit: 0`), network (`unable to resolve host`/`timeout`), and generic errors into localized messages, falling back to `buildDeterministicWizardFallback`.

### 2.7 Security Rules (`backend/firestore.rules`)
- Zero-trust UID scoping (`isOwner(userId)`), strict field whitelists (`hasAll` / `hasOnly`), max field sizes, enum validation, immutable `createdAt`/`userId`.
- Enforces `verified == true` for department statuses (`ACKNOWLEDGED`, `APPROVED`, `REJECTED`) and `explicitCitizenConfirmed == true` for `CONFIRMED`/`HANDED_OFF`.
- Rejects un-whitelisted fields such as `aadhaar`.

---

## 3. Gaps, Incomplete Features & Technical Debt

### 3.1 Critical Architectural Gaps
1. **No Shared Backend API or Database**: There is no REST API, no OpenAPI/Swagger spec, no server-side auth (JWT + refresh tokens), no server-side RBAC, no persistent relational database with migrations/seeds, and no web backend.
2. **Duplicated/Client-Trapped Business Logic**: Eligibility rules (`EligibilityEngine.kt`), scheme catalog, and state overrides live solely inside the Android app APK. A website cannot share rules without duplicating code unless moved to `/backend` as the single source of truth.
3. **Broken/Mocked RBAC Enforcement**: Any authenticated `CITIZEN` can switch their own `role` dropdown on `ProfileSetupScreen` (`CITIZEN`, `CSC_OPERATOR`, `SUPERVISOR`, `REVIEWER`) and click `"Approved (Verified)"` on `ApplicationTrackingScreen` (`ApplicationAndTasksScreens.kt:636`). Real RBAC must be enforced server-side with role-protected endpoints and immutable/admin-controlled role claims or audited role assignments.
4. **No Multi-User Operator/Supervisor/Reviewer Visibility**: Firestore rules lock all applications and tasks strictly under `/users/{userId}/...`, meaning a CSC Operator, Supervisor, or Reviewer cannot view assigned tasks or review citizen applications across users.

### 3.2 Incomplete Features & UX Gaps
5. **Document Checklist Not Persisted**: `_documentStatuses` (`SchemeSaathiViewModel.kt`) lives only in a volatile `MutableStateFlow`; app restart loses checklist state.
6. **Hardcoded Bilingual Strings & Mixed Labels**: Only ~44 strings are in `res/values/strings.xml` and `res/values-hi/strings.xml`. Nearly all UI text, dropdown options (`"Female / महिला"`), and status badges are hardcoded `if (language == "hi") ... else ...` in Compose screens and ViewModels, making expansion to Marathi/Bengali/Tamil impossible without centralized i18n JSON/XML resources. Notice `MainActivity.kt:412` labels Settings `"प्रोफ़ाइल"` in Hindi vs `"Settings"` in English, and `ApplicationTrackingScreen:552` still contains `"14. Application Status Tracking"`.
7. **No Real Notifications / Reminders**: Task reminders set `deliveryChannel = "SMS_MOCK"` and `deliveryStatus = "SCHEDULED"` with no actual notification delivery mechanism or Android notification channel.
8. **Missing Offline Scheme/Checklist Cache**: Despite declaring Room (`androidx-room-*`) and DataStore (`datastore-preferences`) in `libs.versions.toml`, neither is implemented.
9. **No Data Export (DPDP Compliance)**: Users can withdraw consent and delete cloud profile, but cannot export their personal data (profile, applications, audit trail) in JSON/CSV.
10. **No Manual Dark Mode Toggle**: `SchemeSaathiTheme` reads `isSystemInDarkTheme()` with no user-facing Light/Dark/System setting.

### 3.3 Security & Technical Debt
11. **Predictable Client-Side Key Derivation**: `SecurityUtils.deriveUserKey(userId)` hashes a constant prefix `"scheme-saathi-vault-v1:$userId"`. Anyone who knows a `userId` can derive the AES key. On Android, keys/tokens should use Android Keystore; on Backend, PII encryption at rest must use a server-managed `PII_ENCRYPTION_KEY` from environment variables (with per-record random IV + auth tag).
12. **PII in Android Logs**: `FirestoreErrorHandler.kt:30-51` logs `email`, `userId`, and `providerData` via `Log.e("FirestoreError", ...)`. Logs must never emit PII.
13. **Hardcoded Year (`2026`)**: `EligibilityEngine.kt:27` (`REFERENCE_YEAR = 2026`) and `SchemeSaathiViewModel.kt:523` (`2026 - pendingAge`) hardcode `2026` instead of using the system clock/current calendar year.
14. **Test/Emulator Port Mismatch & Hanging Tests**: `backend/firebase.json` configures Firestore emulator on port `8085`, while `FirestoreEmulatorTestBase.kt` points to `127.0.0.1:8080`, causing `./gradlew testDebugUnitTest` to hang for 2 minutes on emulator tests when the emulator is down.
15. **Unused Dependencies & Permissions**: `libs.versions.toml` and `app/build.gradle.kts` include unused CameraX (`camera-camera2`, `camera-lifecycle`, `camera-view`, `camera-core`), `play-services-location`, `coil-compose`, and `accompanist-permissions` despite photo analyzer removal.
16. **Missing Web, CI/CD, and Containerization**: No `/web`, no `Dockerfile`/`docker-compose.yml`, no ESLint/Prettier/ktlint, no Playwright E2E tests, and no `.github/workflows` CI pipeline.

---

## 4. Target Architecture Proposal & Stack Decision

### 4.1 Decision on Existing `/backend` Stack
The existing `/backend` directory is **not** a server runtime — it contains only static Firebase rules and JS rules tests. Keeping Firestore-only rules cannot deliver:
- OpenAPI / Swagger REST API consumed by both Next.js Web and Android Kotlin,
- JWT + refresh token authentication,
- Server-side RBAC across `CITIZEN`, `CSC_OPERATOR`, `SUPERVISOR`, `REVIEWER` workflows,
- Server-side rate limiting, audit logs, and centralized Gemini AI provider resilience (retries, timeouts, 3 error states),
- Local `docker-compose` portability without cloud Firebase credentials.

**Recommendation**: Retain existing `backend/firestore.rules` & `firestore.test.js` under `backend/firebase/` (or in place in `backend/`) so Firebase compatibility is preserved without breaking existing rules tests, and build a **Node.js 24 + TypeScript + Express REST API** inside `/backend` backed by **SQLite (`node:sqlite` built-in) for zero-dependency tests/dev and PostgreSQL (`pg`) support for Docker/production**, with SQL migrations, seeders, OpenAPI 3.0 spec + Swagger UI (`/api/docs`), JWT + refresh token auth, PII AES-256-GCM encryption at rest, rate limiting, and audit logging.

### 4.2 Target Monorepo Structure
```text
saathi/
├── backend/              # Express + TypeScript REST API, OpenAPI 3.0, SQLite/Postgres migrations & seeds,
│                         # centralized Eligibility Engine, AI Gateway (Gemini + deterministic fallback),
│                         # RBAC, JWT + Refresh Tokens, Audit Log, AES-256-GCM PII Vault + existing firestore.rules
├── shared/               # Single source of truth:
│   ├── design-tokens/    # Civic Green/Saffron/Cream palette, Light/Dark colors, Typography scale
│   ├── i18n/             # Extensible hi.json, en.json (+ ready for mr/bn/ta)
│   └── api-client/       # OpenAPI-generated / strongly-typed TypeScript API client & models
├── web/                  # Next.js 15 (App Router, TypeScript) + Tailwind CSS PWA
│                         # Mobile-first, WCAG AA, OTP/Password/Demo Login, all screens & RBAC views
├── app/                  # Native Android Kotlin + Jetpack Compose app
│                         # Connected to shared Backend API via Retrofit/OkHttp + Keystore-encrypted tokens
│                         # Room offline cache for schemes & checklists, local notification alerts, res strings
├── docs/                 # AUDIT.md, ARCHITECTURE.md, ASSUMPTIONS.md, DEPLOYMENT.md
├── .github/workflows/    # CI pipeline (lint, unit + integration + Playwright UI tests, builds)
├── docker-compose.yml    # One-command Postgres + Backend + Web local stack
└── CHECKLIST.md          # Honest verification & delivery matrix
```

### 4.3 Security & Compliance Blueprint (OWASP Top 10 + DPDP)
- **Authentication**: Short-lived JWT Access Tokens (`15m`) + rotating Refresh Tokens (`7d`, hashed in DB). Web uses `httpOnly`, `SameSite=Lax`, `Secure` cookies; Android stores tokens encrypted with **Android Keystore (`EncryptedSharedPreferences` / AES-GCM Keystore wrapper)**.
- **RBAC Matrix**:
  - `CITIZEN`: Manage own profile, consent, data export/delete, check eligibility, create/confirm own applications (`HANDED_OFF` / `SUBMITTED_SELF_REPORTED`), view/create personal reminders.
  - `CSC_OPERATOR`: View citizen queue (`HANDED_OFF` / `IN_REVIEW`), acknowledge applications (`ACKNOWLEDGED`), create/advance verification tasks.
  - `REVIEWER`: Review & verify submitted applications (`ACKNOWLEDGED`, `APPROVED`, `REJECTED` with `verified=true`), inspect audit logs.
  - `SUPERVISOR`: Full oversight of tasks, workflows, audit logs, and rule versions (never bypasses Citizen Human Confirmation Gate).
- **Privacy / No Aadhaar / No PII in Logs**:
  - Client + Server validation rejects 12-digit Aadhaar patterns (`\b\d{12}\b` after separator stripping) everywhere with HTTP `400`.
  - PII (`name`, `phone`, `dob`) encrypted at rest with AES-256-GCM using `PII_ENCRYPTION_KEY` (validated at startup, never logged).
  - HTTP request logger redacts `authorization`, `cookie`, `password`, `otp`, and PII fields.
  - DPDP Consent Gate + `GET /api/v1/profile/export` (JSON download) + `DELETE /api/v1/profile` (full erasure).
- **Human Confirmation Gate Invariant**:
  - `POST /api/v1/applications/:id/confirm` verifies the submitted `snapshotHash` against server-computed SHA-256 of the stored draft + checklist and requires `explicitCitizenConfirmed === true` and `uncertainAcknowledged === true`. AI service has **no** submission tool and only calls deterministic endpoints.

---

## 5. Verification & Hardening Pass — Android Firebase/Firestore Migration Audit

### 5.1 Screen & Layer Grep Summary (`app/`)
- **Compose Screens (`app/src/main/java/com/example/ui/screens/*.kt`)**: **0 screens** import `FirebaseFirestore` or call Cloud Firestore directly; all 15 Compose screens delegate state and actions exclusively to `SchemeSaathiViewModel`.
- **ViewModel & Entrypoint Migration (`MainActivity.kt`, `SchemeSaathiViewModel.kt`, `SchemeModels.kt`, `BackendApiClient.kt`)**:
  - Removed `FirebaseFirestore`, `SchemeSaathiRepository.kt`, `FirestoreErrorHandler.kt`, `@DocumentId`/`@IgnoreExtraProperties`/`@ServerTimestamp`/`com.google.firebase.Timestamp` annotations, and `libs.firebase.firestore`.
  - Migrated profile read/write/delete, application creation/confirmation/review, checklist sync, and collaborative task CRUD directly to `BackendApiClient` (`/api/v1/*`) paired with local reactive state and Room offline checklist persistence (`SchemeSaathiDatabase`).
  - Deleted obsolete Firestore emulator tests (`app/src/test/java/com/example/base/FirestoreEmulatorTestBase.kt`, `app/src/test/java/com/example/data/repository/SchemeSaathiRepositoryRuleTest.kt`, and `backend/tests/firestore.test.js`) along with unused `@firebase/rules-unit-testing` / `firebase-tools` backend devDependencies.

### 5.2 Justified Remaining Firebase SDK Exceptions (Non-Firestore)
1. **Google Sign-In Gate (`FirebaseAuth` + Jetpack Credential Manager in `MainActivity.kt` & `ui/auth/AuthComponents.kt`)**:
   - Retained solely to authenticate real Android users with their Google account before loading the workspace, avoiding custom password UI changes on mobile while backend role switching/sync runs cleanly through `BackendApiClient` (`TokenStore`).
2. **Gemini Client SDK (`Firebase.ai` in `ai/SchemeSaathiAiService.kt` + `util/AppCheckInitializer.kt`)**:
   - Retained intentionally so mobile streaming chat and live-voice script generation authenticate via Firebase App Check without embedding any Gemini API keys in Android source/Gradle/resources. Backend `/api/v1/ai/*` remains the unified gateway for Web PWA and server fallback.

