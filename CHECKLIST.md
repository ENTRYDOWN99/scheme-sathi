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
| **Phase 5 — Android Parity & Audit Fixes (`/app`)** | Shared REST client (`BackendApiClient`), Android Keystore JWT (`TokenStore`), Room offline checklist DB (`SchemeSaathiDatabase`), local alerts (`NotificationHelper`), dynamic current reference year, RBAC UI gate, PII-redacted logging, `Phase5ParityTest` | `bf61492` | Completed & Committed |
| **Phase 6 — Docker, CI/CD & E2E Tests** | `backend/Dockerfile`, `web/Dockerfile`, `docker-compose.yml`, `.github/workflows/ci.yml`, Playwright E2E suite (`web/tests/e2e/saathi.spec.ts`) | `8f0f776` | Completed & Committed |
| **Phase 7 — Documentation & Hardening Pass** | Removed Android Firestore usage in favor of `BackendApiClient` (`docs/AUDIT.md`), added `.gitattributes` (LF), Prettier `format:check`, expanded Playwright (4 roles, OTP/login failure, AI errors, EN/HI, `@axe-core/playwright` WCAG AA), Android Compose UI tests (`ConfirmationGateComposeTest`), `docs/DEPLOYMENT.md` (Render/Railway/Cloud Run/Vercel/Play Console), signed release `.aab` | Pending commit | Verified |

---

## 2. Core Invariants & Safety Guarantees

- [x] **No Aadhaar Collection or Storage**: Rejected in UI validation and Express middleware (`noAadhaarMiddleware` -> `400`). (Cloud Firestore client dependencies were removed from Android in Phase 7.)
- [x] **Deterministic Rule Engine Parity**: Identical 5-scheme (`PM-KISAN`, `NSAP-IGNOAPS`, `PMUY`, `PM-JAY`, `APY`) `ALL`/`ANY` tree evaluation, state income overrides, and `UNKNOWN` handling for missing/unconfirmed data in Kotlin (`EligibilityEngine.kt`) and TypeScript (`eligibilityEngine.ts`).
- [x] **DOB (`DD/MM/YYYY`) Validation & Privacy**: Calendar validation (`31/02/2000` rejected, future dates rejected), age accounting for birthday not yet reached, AES-256-GCM encrypted storage, and age-only LLM context.
- [x] **Human Confirmation Gate**: Requires explicit citizen declarations (`reviewedFields`, `acknowledgedUnknowns`, `confirmNoAutoSubmit`) and immutable SHA-256 snapshot hash to generate a CSC Handoff Pack; AI auto-submit/self-approval blocked across backend, web, and Android.
- [x] **4-Role RBAC (`CITIZEN`, `CSC_OPERATOR`, `REVIEWER`, `SUPERVISOR`)**: Server-enforced authorization across routes and role-gated UI controls on Web (`/operator`) and Android (`ApplicationTrackingScreen` + `SettingsScreen`).
- [x] **Zero Hardcoded AI Keys in Mobile**: Android uses Firebase AI SDK + App Check (`DebugAppCheckProviderFactory` / `RecaptchaAppCheckProviderFactory`); backend AI gateway supports optional server `GEMINI_API_KEY` with deterministic grounded fallback.
- [x] **Bilingual UX (`en` / `hi`)**: Shared dictionaries (`shared/i18n/`), localized Android string resources (`values/` and `values-hi/`), and live Web/Android language toggle.

---

## 3. Verified Commands & Exact Scopes

- **Backend Lint, Format & TypeScript Build**: `cd backend && npm run lint && npm run format:check && npm run build` -> **PASS** (0 lint errors, Prettier clean, `dist/` & SQL/OpenAPI assets built).
- **Backend Unit & API Integration Tests**: `cd backend && npm test` -> **PASS** (`2` files, `15 passed`: `tests/eligibility.test.ts` 9 + `tests/api.integration.test.ts` 6).
- **Shared Package Type Check**: `backend\node_modules\.bin\tsc.cmd -p shared\tsconfig.json` -> **PASS** (exit code 0).
- **Docker Compose Configuration Validation**: `docker compose config > NUL` -> **PASS** (exit code 0).
- **Web Lint, Build, Playwright E2E & Axe Accessibility (Chromium)**: `cd web && npm run lint && npm run build && npm run test:e2e` -> **PASS** (`1 passed` covering all 4 roles, OTP/password login failures, AI busy/network/generic error states, `hi`/`en` toggle across all 8 routes, Human Confirmation Gate, and `@axe-core/playwright` WCAG 2.0/2.1 AA checks with 0 violations).
- **Android Debug Tests & Signed Release Bundle**:
  - `.\gradlew.bat testDebugUnitTest` (full suite, no filters) -> **PASS** (`19 passed`, `0 failed`, `0 skipped`: `DateUtilsTest` 13, `Phase5ParityTest` 2, `ConfirmationGateComposeTest` 2, `ExampleUnitTest` 1, `ExampleRobolectricTest` 1).
  - `.\gradlew.bat bundleRelease` -> **PASS** (`app/build/outputs/bundle/release/app-release.aab` built and `jarsigner -verify` passed using an ephemeral local upload keystore outside Git).
- **Known Limitations**:
  - Release `.aab` verification used a local self-signed upload keystore (`jarsigner -verify` reports self-signed/no-timestamp warnings); production Play Console distribution requires a dedicated upload keystore and Play App Signing.
  - Android Compose tests ran via Robolectric JVM (`testDebugUnitTest`); `connectedAndroidTest` on a physical/emulator Android device, live Firebase Google Sign-In (`google-services.json`), and live Gemini quota/network calls were not executed in this offline hardening pass.

