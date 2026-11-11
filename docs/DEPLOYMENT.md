# Deployment Guide — SchemeSaathi (Backend + Web PWA + Android)

> **Disclaimer**: SchemeSaathi is NOT an official government application. All scheme rules are informational guidance only; final eligibility and benefits rest solely with the issuing government authority.

## 1. Architecture Overview

One unified backend (`/backend`) powers both clients:
- **Backend (`/backend`)**: Node.js 24 + Express + TypeScript + SQLite (zero-config local/dev) or PostgreSQL 16 (production), serving `/api/v1/*`, Swagger UI (`/api/docs`), OpenAPI 3.0 spec (`/api/openapi.json`), and `/health`.
- **Web PWA (`/web`)**: Next.js 15 App Router + TypeScript + Tailwind CSS (`output: 'standalone'`), communicating with `/api/v1` via `httpOnly` JWT cookies.
- **Android Mobile App (`/app`)**: Native Kotlin + Jetpack Compose (`minSdk 24`, `targetSdk 36`), communicating with `/api/v1` via OkHttp (`BuildConfig.API_BASE_URL`), storing JWTs in **Android Keystore (`TokenStore`)**, caching document checklists offline in **Room (`SchemeSaathiDatabase`)**, and dispatching local notifications (`NotificationHelper`).

---

## 2. Production Docker Compose Deployment (PostgreSQL + Backend + Web)

1. Configure production secrets in `.env` at repository root:
   ```env
   POSTGRES_PASSWORD=strong-random-db-password
   JWT_SECRET=<at-least-32-character-random-secret>
   JWT_REFRESH_SECRET=<at-least-32-character-different-random-secret>
   PII_ENCRYPTION_KEY=<64-hex-character-aes-256-key>
   CORS_ORIGIN=https://your-domain.example
   NEXT_PUBLIC_API_URL=https://api.your-domain.example/api/v1
   GEMINI_API_KEY=<your-gemini-api-key>
   ```
   > **Security**: The backend refuses to boot in `NODE_ENV=production` if `JWT_SECRET` / `JWT_REFRESH_SECRET` are `< 32` characters or if `PII_ENCRYPTION_KEY` is not 64 hex chars.

2. Launch all three services with automatic PostgreSQL migrations & seeding:
   ```bash
   docker compose up --build -d
   ```

3. Verify health:
   ```bash
   curl -f http://localhost:4000/health
   # Web UI at http://localhost:3000
   # Swagger OpenAPI docs at http://localhost:4000/api/docs
   ```

---

## 3. Manual Bare-Metal / VM Deployment

### Backend (`/backend`)
```bash
cd backend
npm ci
npm run build
NODE_ENV=production npm run migrate
NODE_ENV=production npm run seed
NODE_ENV=production npm start
```

### Web PWA (`/web`)
```bash
cd web
npm ci
NEXT_PUBLIC_API_URL=https://api.your-domain.example/api/v1 npm run build
NODE_ENV=production npm start
```

---

## 4. Android Build & Play Store Release

1. **Google Services Config**: Copy your real `google-services.json` to repository root (or `google-services.json.example` for offline/REST-only builds).
2. **Backend Endpoint Injection**: Pass `API_BASE_URL` cleanly via environment or Gradle property:
   - Emulator connecting to host machine: `http://10.0.2.2:4000/api/v1` (default)
   - Production HTTPS endpoint:
     ```bash
     API_BASE_URL=https://api.your-domain.example/api/v1 ./gradlew assembleRelease
     ```
3. **Upload Keystore Environment Variables** (never commit `.jks`/`.keystore` files):
   ```env
   KEYSTORE_PATH=/path/to/my-upload-key.jks
   STORE_PASSWORD=...
   KEY_PASSWORD=...
   ```
4. **Build Signed App Bundle (`.aab`)**:
   ```bash
   ./gradlew bundleRelease
   # Output: app/build/outputs/bundle/release/app-release.aab
   ```
5. **Pre-Release Checklist**:
   - Confirm `google-services.json` includes release SHA-1 & SHA-256 fingerprints.
   - Verify Firebase App Check reCAPTCHA / Play Integrity enforcement in Firebase Console.
   - Confirm `network_security_config.xml` only allows cleartext for local emulator/localhost testing; production calls must use `https://`.

