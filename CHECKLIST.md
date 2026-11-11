# SchemeSaathi — End-to-End Delivery & Verification Checklist

> **Disclaimer**: SchemeSaathi is NOT an official government application. All eligibility results and scheme rules are informational guidance only; final decisions rest solely with the respective issuing authorities.

---

## 1. Phase-by-Phase Delivery Status

| Phase | Scope | Commit | Status |
|---|---|---|---|
| **Phase 1 — Audit & Architecture** | `docs/AUDIT.md`, `docs/ARCHITECTURE.md`, `.env.example`, `.gitignore` | `acf12fb` | Completed & Committed |
| **Phase 2 — Unified REST Backend (`/backend`)** | Express + TypeScript API (`/api/v1/*`), SQLite + PostgreSQL 16 migrations (`001_initial.sql`), deterministic `EligibilityEngine`, AES-256-GCM PII crypto, Aadhaar guard middleware, 4-role RBAC, Swagger UI (`/api/docs` & `openapi.yaml`), Vitest + Supertest suite | `0ccf406` | Completed & Committed |
| **Phase 3 — Shared Design & Client (`/shared`)** | `design-tokens/tokens.{json,css}`, bilingual `i18n/{en,hi}.json`, typed API client (`shared/api-client/index.ts`) | `176cdaf` | Completed & Committed |
| **Phase 4 — Responsive Web PWA (`/web`)** | Next.js 15 App Router + TypeScript + Tailwind PWA (`/`, `/login`, `/profile`, `/chat`, `/apply`, `/alerts`, `/settings`, `/operator`), `manifest.webmanifest`, `sw.js`, `icon.svg` | `342086d` | Completed & Committed |
| **Phase 5 — Android Parity & Audit Fixes (`/app`)** | Shared REST client (`BackendApiClient`), Android Keystore JWT (`TokenStore`), Room offline checklist DB (`SchemeSaathiDatabase`), local alerts (`NotificationHelper`), dynamic current reference year, RBAC UI gate, PII-redacted error logging (`FirestoreErrorHandler`), `Phase5ParityTest` | `bf61492` | Completed & Committed |
| **Phase 6 — Docker, CI/CD & E2E Tests** | `backend/Dockerfile`, `web/Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml`, Playwright E2E suite (`web/tests/e2e/saathi.spec.ts`) | `8f0f776` | Completed & Committed |
| **Phase 7 — Documentation & Checklist** | `docs/DEPLOYMENT.md`, updated `README.md`, `CHECKLIST.md` | Pending commit | In Progress |

---

## 2. Core Invariants & Safety Guarantees

- [x] **No Aadhaar Collection or Storage**: Rejected in UI validation, Express middleware (`noAadhaarMiddleware` -> `400`), and Cloud Firestore security rules (`incoming().keys().hasOnly([...])`).
- [x] **Deterministic Rule Engine Parity**: Identical 5-scheme (`PM-KISAN`, `NSAP-IGNOAPS`, `PMUY`, `PM-JAY`, `APY`) `ALL`/`ANY` tree evaluation, state income overrides, and `UNKNOWN` handling for missing/unconfirmed data in Kotlin (`EligibilityEngine.kt`) and TypeScript (`eligibilityEngine.ts`).
- [x] **DOB (`DD/MM/YYYY`) Validation & Privacy**: Calendar validation (`31/02/2000` rejected, future dates rejected), age accounting for birthday not yet reached, AES-256-GCM encrypted storage, and age-only LLM context.
- [x] **Human Confirmation Gate**: Requires explicit citizen declarations (`reviewedFields`, `acknowledgedUnknowns`, `confirmNoAutoSubmit`) and immutable SHA-256 snapshot hash to generate a CSC Handoff Pack; AI auto-submit/self-approval blocked across backend, web, and Android.
- [x] **4-Role RBAC (`CITIZEN`, `CSC_OPERATOR`, `REVIEWER`, `SUPERVISOR`)**: Server-enforced authorization across routes and role-gated UI controls on Web (`/operator`) and Android (`ApplicationTrackingScreen` + `SettingsScreen`).
- [x] **Zero Hardcoded AI Keys in Mobile**: Android uses Firebase AI SDK + App Check (`DebugAppCheckProviderFactory` / `RecaptchaAppCheckProviderFactory`); backend AI gateway supports optional server `GEMINI_API_KEY` with deterministic grounded fallback.
- [x] **Bilingual UX (`en` / `hi`)**: Shared dictionaries (`shared/i18n/`), localized Android string resources (`values/` and `values-hi/`), and live Web/Android language toggle.

---

## 3. Verified Commands & Exact Scopes

- **Backend Lint & TypeScript Build**: `cd backend && npm run lint && npm run build` -> **PASS** (0 lint errors, `dist/` & SQL/OpenAPI assets built).
- **Backend Unit & API Integration Tests**: `cd backend && npm test` -> **PASS** (`2` files, `15 passed`: `tests/eligibility.test.ts` 9 + `tests/api.integration.test.ts` 6).
- **Shared Package Type Check**: `backend\node_modules\.bin\tsc.cmd -p shared\tsconfig.json` -> Verified prior to docs phase (exit code 0); re-verified alongside Web check.
- **Docker Compose Configuration Validation**: `docker compose config > NUL` -> **PASS** (exit code 0).
- **Web Playwright E2E Suite (Chromium)**: `cd web && npm run test:e2e` -> **PASS** (`1 passed` end-to-end flow verified in Phase 6 covering scheme catalog, Hindi/English switch, login, invalid DOB rejection + `01/01/1964` age `62`, profile save, Human Confirmation Gate Handoff Pack, AI safety block, and Reviewer RBAC approval).
- **Android Debug Build & Non-Emulator Unit/Robolectric Tests**:
  `.\gradlew.bat assembleDebug testDebugUnitTest --tests "com.example.Phase5ParityTest" --tests "com.example.util.DateUtilsTest" --tests "com.example.ExampleUnitTest" --tests "com.example.ExampleRobolectricTest"` -> **PASS** (`17 passed`: `DateUtilsTest` 13, `Phase5ParityTest` 2, `ExampleUnitTest` 1, `ExampleRobolectricTest` 1).
- **Firestore Emulator Suites (Known Local Limitation)**:
  - `backend`: `npm run test:rules` (`firebase emulators:exec --only firestore "node --test tests/firestore.test.js"`)
  - `app`: `SchemeSaathiRepositoryRuleTest` (connects to `127.0.0.1:8085`)
  - Both require an active local Firebase Firestore Emulator runtime and were intentionally excluded from offline non-emulator test runs.
