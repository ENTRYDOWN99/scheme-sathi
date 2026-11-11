# Deployment Guide — SchemeSaathi (Backend + Web PWA + Android)

> **Disclaimer**: SchemeSaathi is NOT an official government application. All scheme rules are informational guidance only; final eligibility and benefits rest solely with the issuing government authority.

---

## 1. Architecture & Required Production Secrets

One unified backend (`/backend`) powers both clients:
- **Backend (`/backend`)**: Node.js 24 + Express + TypeScript (`sqlite:` in dev/local, `postgresql://` / PostgreSQL 16 in production), serving `/api/v1/*`, Swagger UI (`/api/docs`), OpenAPI 3.0 spec (`/api/openapi.json`), and `/health`.
- **Web PWA (`/web`)**: Next.js 15 App Router + TypeScript + Tailwind CSS (`output: 'standalone'`), connecting to `NEXT_PUBLIC_API_URL`.
- **Android Mobile App (`/app`)**: Native Kotlin + Jetpack Compose (`minSdk 24`, `targetSdk 36`), connecting to `BuildConfig.API_BASE_URL` with Android Keystore JWT (`TokenStore`) and Room offline checklist persistence (`SchemeSaathiDatabase`).

### Generate Mandatory Production Secrets (`NODE_ENV=production`)
The backend refuses to start in `NODE_ENV=production` unless all three secrets meet strict requirements in [env.ts](../backend/src/config/env.ts):
```bash
# 32+ character secrets for JWT access & refresh signing:
openssl rand -base64 36   # -> JWT_SECRET
openssl rand -base64 36   # -> JWT_REFRESH_SECRET

# Exactly 64 hex characters (32 bytes) for AES-256-GCM PII encryption:
openssl rand -hex 32      # -> PII_ENCRYPTION_KEY
```

---

## 2. Backend Deployment (`/backend`)

### Option A: Render (Web Service + Managed PostgreSQL)
1. In the Render Dashboard, create a **PostgreSQL** instance and copy its **Internal/External Database URL**.
2. Create a **New Web Service** connected to your GitHub repo:
   - **Root Directory**: `backend`
   - **Runtime**: Node (or Docker using `backend/Dockerfile`)
   - **Build Command**: `npm ci && npm run build`
   - **Pre-Deploy / Release Command**: `npm run migrate && npm run seed`
   - **Start Command**: `npm start`
   - **Health Check Path**: `/health`
3. Add **Environment Variables**:
   - `NODE_ENV=production`
   - `PORT=4000` (or leave default `PORT` provided by Render)
   - `DATABASE_URL=<Render PostgreSQL URL>`
   - `JWT_SECRET=<generated>`
   - `JWT_REFRESH_SECRET=<generated>`
   - `PII_ENCRYPTION_KEY=<64-hex>`
   - `CORS_ORIGIN=https://your-vercel-app.vercel.app`
   - `GEMINI_API_KEY=<optional server key>`
4. Deploy and verify `https://<your-render-service>.onrender.com/health` and `/api/docs`.

### Option B: Railway (PostgreSQL + Backend Service)
1. Create a new Railway project and add a **PostgreSQL** database plugin.
2. Add a **GitHub Service** pointing to this repo with Root Directory `/` and Dockerfile Path `backend/Dockerfile` (or set Root Directory `backend`, Build `npm ci && npm run build`, Start `sh -c "npm run migrate && npm run seed && npm start"`).
3. Link `${{Postgres.DATABASE_URL}}` as `DATABASE_URL`, set `NODE_ENV=production`, `JWT_SECRET`, `JWT_REFRESH_SECRET`, `PII_ENCRYPTION_KEY`, `CORS_ORIGIN`, and optional `GEMINI_API_KEY`.
4. Generate a Railway public domain under **Settings → Networking** and verify `/health`.

### Option C: Google Cloud Run (Container + Cloud SQL PostgreSQL)
1. Enable **Cloud Run**, **Artifact Registry**, and **Cloud SQL** in your GCP project.
2. Build and push the backend image from repo root:
   ```bash
   gcloud builds submit --config=- . <<'EOF'
   steps:
     - name: 'gcr.io/cloud-builders/docker'
       args: ['build', '-f', 'backend/Dockerfile', '-t', 'gcr.io/PROJECT_ID/saathi-backend:latest', '.']
   images: ['gcr.io/PROJECT_ID/saathi-backend:latest']
   EOF
   ```
3. Run database migrations/seed against your Cloud SQL instance (via Cloud SQL Auth Proxy), then deploy to Cloud Run:
   ```bash
   gcloud run deploy saathi-backend \
     --image gcr.io/PROJECT_ID/saathi-backend:latest \
     --platform managed \
     --region asia-south1 \
     --allow-unauthenticated \
     --port 4000 \
     --add-cloudsql-instances PROJECT_ID:asia-south1:saathi-pg \
     --set-env-vars "NODE_ENV=production,CORS_ORIGIN=https://your-domain.com" \
     --set-secrets "DATABASE_URL=saathi-db-url:latest,JWT_SECRET=saathi-jwt:latest,JWT_REFRESH_SECRET=saathi-jwt-ref:latest,PII_ENCRYPTION_KEY=saathi-pii-key:latest,GEMINI_API_KEY=saathi-gemini:latest"
   ```

### Option D: Local / VM Docker Compose
```bash
docker compose up --build -d
# Checks Postgres health -> runs migrate & seed -> exposes Backend (:4000) and Web (:3000)
curl -f http://localhost:4000/health
```

---

## 3. Web PWA Deployment on Netlify / Vercel (`/web`)

> **Same-Origin Cookie Proxy**: Backend auth issues `httpOnly`, `SameSite=Lax`, `Secure` session cookies in production. Setting `NEXT_PUBLIC_API_URL=/api/v1` and `BACKEND_URL=https://<service>.onrender.com` enables [web/next.config.ts](../web/next.config.ts) `rewrites()` to proxy `/api/v1/*` to `BACKEND_URL` under the same origin so auth cookies work reliably across Netlify/Vercel and Render.

### Option A: Netlify (`netlify.toml`)
1. Import this GitHub repo in Netlify (auto-reads root `netlify.toml` with `base = "web"` and Node `24`).
2. Set **Site Configuration → Environment Variables**:
   - `NEXT_PUBLIC_API_URL=/api/v1`
   - `BACKEND_URL=https://<service>.onrender.com`
3. Deploy and set backend `CORS_ORIGIN` on Render to your `https://<site>.netlify.app` URL.

### Option B: Vercel
1. Go to **Vercel Dashboard → Add New Project** and import your GitHub repository.
2. Configure **Project Settings → Build & Development**:
   - **Root Directory**: `web` (Note: `web/next.config.ts` enables `experimental.externalDir` to import `shared/`, so ensure **Include source files outside of the Root Directory** is enabled in Vercel General Settings).
   - **Framework Preset**: Next.js
   - **Build Command**: `npm run build`
3. Set **Environment Variables**:
   - `NEXT_PUBLIC_API_URL=/api/v1`
   - `BACKEND_URL=https://<service>.onrender.com`
4. Deploy and confirm:
   - Update `CORS_ORIGIN` on the backend to include the exact `https://...vercel.app` (and custom domain) origin.
   - Verify `/` renders the scheme catalog, `/login` authenticates cleanly, and `manifest.webmanifest` / `sw.js` load over HTTPS.

---

## 4. Android Build & Google Play Console Release (`/app`)

### Step 1: Configure Upload Keystore & Firebase Config (Never Commit Secrets)
1. Copy your live Firebase Android config to `app/google-services.json` (with release SHA-1 & SHA-256 fingerprints registered in Firebase Console).
2. Generate a production upload keystore outside the repository (or verify with an ephemeral scratch keystore):
   ```powershell
   keytool -genkeypair -v -keystore C:\secure\my-upload-key.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   ```
   > Keep `.jks` / `.keystore` files **strictly outside Git** (`.gitignore` excludes `*.jks`, `*.keystore`).

### Step 2: Build Signed Release `.aab`
`app/build.gradle.kts` reads `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`, and `API_BASE_URL` directly from environment variables:
```powershell
$env:KEYSTORE_PATH = "C:\secure\my-upload-key.jks"
$env:STORE_PASSWORD = "your-store-password"
$env:KEY_PASSWORD = "your-key-password"
$env:API_BASE_URL = "https://api.your-domain.example/api/v1"

.\gradlew.bat bundleRelease
# Output: app/build/outputs/bundle/release/app-release.aab

# Verify AAB JAR signature:
jarsigner -verify -verbose -certs app\build\outputs\bundle\release\app-release.aab
```

### Step 3: Publish in Google Play Console
1. Open **Google Play Console → Create App** (`Scheme Saathi`, Default language Hindi or English, App type: App, Free).
2. Complete **App Content** declarations:
   - **Privacy Policy URL**: Host your privacy notice covering AES-256-GCM encryption, DPDP export/erasure, and the strict **No Aadhaar Collection** policy.
   - **Government Apps Declaration**: Select **No** (SchemeSaathi is an independent non-official civic assistant; matches the in-app disclaimer).
   - **Data Safety**: Declare encrypted in-transit/at-rest user profile fields (`name`, `phone`, `DOB`, location/income) and deletion option in Settings / Consent screens.
   - **App Access / Test Credentials**: Provide instructions for testing the 4 demo roles (`CITIZEN`, `CSC_OPERATOR`, `REVIEWER`, `SUPERVISOR`) or Google Sign-In test account.
3. Under **Release → Testing → Closed Testing (or Internal Testing)**, upload `app/build/outputs/bundle/release/app-release.aab`.
4. Copy the **App Signing key certificate SHA-1 & SHA-256** generated by Google Play App Signing back into **Firebase Console → Project Settings → Your Android App** so Google Sign-In (`CredentialManager`) and **Firebase App Check** work in Play-distributed builds.
5. Roll out to Internal/Closed testers, verify crash-free sign-in and `/api/v1` connectivity over HTTPS (`network_security_config.xml` restricts cleartext HTTP exclusively to local debug hosts), then promote to Production.
