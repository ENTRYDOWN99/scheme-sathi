# Scheme Saathi (स्कीम साथी)

> **⚠️ DISCLAIMER**: **Not an official government app.** Scheme Saathi is an independent civic-tech assistant designed for hackathon demonstration. Eligibility results, financial thresholds, and benefit calculations are strictly informational and illustrative; final application decisions and benefit disbursements rest solely with the respective government issuing authorities.

[![Android](https://img.shields.io/badge/Platform-Android_API_24+-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Backend-Firebase_Auth_|_Firestore_|_App_Check-FFCA28?style=flat&logo=firebase&logoColor=black)](https://firebase.google.com)
[![Gemini](https://img.shields.io/badge/AI-Firebase_AI_SDK_(Gemini)-8E75C2?style=flat&logo=google&logoColor=white)](https://firebase.google.com/docs/ai)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

---

## ⚡ Judge Quick Start

> ### 🏆 For Hackathon Judges & Evaluators:
>
> #### 🚀 Path A: Fastest Evaluation (Recommended)
> Download the pre-built, signed APK directly from GitHub Releases:
> - **Download Link**: `TODO: https://github.com/YOUR_USERNAME/saathi/releases/latest/download/SchemeSaathi.apk`
> - **Why this path?** This APK is pre-signed with the registered SHA-1 certificate, allowing **real Google Sign-In** and **live Firebase backend sync** to work immediately on any Android phone (Android 7.0+ / API 24+).
> *(Note: The `.apk` file is intentionally excluded from the Git repository history to maintain a clean codebase).*
>
> #### 🛠️ Path B: Build from Source
> ```powershell
> # 1. Clone repository
> git clone https://github.com/YOUR_USERNAME/saathi.git
> cd saathi
>
> # 2. Copy the template Firebase configuration
> copy app\google-services.json.example app\google-services.json
>
> # 3. Build the debug APK
> .\gradlew.bat assembleDebug
> ```
> ⚠️ **Important Note on Building with the Example Config**:
> When building from source with `app/google-services.json.example`, the project compiles cleanly and offline features (deterministic eligibility engine, offline fallback chat, UI navigation) function as expected. However, **live Google Sign-In and Cloud Firestore will NOT authenticate with the example file** because Google Play Services and **Firebase App Check** strictly enforce that the Android app's signing certificate SHA-1 and OAuth client ID match a live registered Firebase project in the cloud. To enable live auth in your own build, see [Connecting Your Own Firebase Project](#connecting-your-own-firebase-project).

---

## 📌 Overview

**Scheme Saathi** (Citizen Welfare Scheme & Collaborative Task Companion) is a native Android application built to bridge the gap between Indian citizens and social welfare entitlements. 

In India, hundreds of billions of rupees in welfare benefits remain unclaimed annually due to discovery barriers, complex multi-conditional eligibility rules, illiteracy, and administrative friction. Scheme Saathi tackles this with a **deterministic-first, AI-assisted architecture**:
- **Deterministic Rule Engine**: Eligibility is evaluated strictly by a deterministic rule engine (never an AI hallucination).
- **Gemini AI Grounding**: Natural language conversation and document photo analysis grounded exclusively in authoritative rule outcomes.
- **Privacy By Design**: No Aadhaar numbers are ever collected or stored; citizen identifiers (Name, Phone) are encrypted with per-user AES-256-GCM.
- **Human Confirmation Gate**: Applications cannot be automatically submitted to government portals; citizens verify all fields and receive a structured Guided Handoff Package for Common Service Centres (CSC).

---

## 🏛️ Supported Welfare Schemes

Scheme Saathi seeds 5 core government welfare schemes with deterministic rule trees and official portal integrations:

| Scheme Code | Scheme Name & Ministry | Illustrative Benefits | Core Eligibility Criteria | Official Source Portal |
|---|---|---|---|---|
| **PM-KISAN** | Pradhan Mantri Kisan Samman Nidhi *(Ministry of Agriculture & Farmers Welfare)* | ₹6,000 / year in 3 direct benefit transfer (DBT) installments | Age ≥ 18, owns cultivable land, not an income-tax payer, active bank account | [pmkisan.gov.in](https://pmkisan.gov.in) |
| **NSAP-IGNOAPS** | Indira Gandhi National Old Age Pension Scheme *(Ministry of Rural Development)* | Central ₹200–₹500/mo + State top-up (e.g. ₹500–₹1,000/mo) | Age ≥ 60, BPL household, annual income within state threshold (UP, Bihar, MH, MP, RJ overrides) | [nsap.nic.in](https://nsap.nic.in) |
| **PMUY** | Pradhan Mantri Ujjwala Yojana 2.0 *(Ministry of Petroleum and Natural Gas)* | Deposit-free LPG connection + first refill and stove assistance | Adult woman (Age ≥ 18), no existing LPG connection in household, BPL or SC/ST/deprived category | [pmuy.gov.in](https://www.pmuy.gov.in) |
| **PM-JAY** | Ayushman Bharat - PM Jan Arogya Yojana *(National Health Authority)* | Up to ₹5 Lakh cashless hospitalization cover per family per year | BPL/NFSA household, senior citizen aged 70+ (Vaya Vandana), or low income; requires SECC-2011/NFSA lookup | [beneficiary.nha.gov.in](https://beneficiary.nha.gov.in) |
| **APY** | Atal Pension Yojana *(PFRDA / NPS Trust)* | Guaranteed monthly pension of ₹1,000–₹5,000 after age 60 | Entry age 18–40, unorganized sector worker, savings bank account for auto-debit, not an income-tax payer | [npscra.nsdl.co.in](https://www.npscra.nsdl.co.in) |

---

## 🛡️ Security & Privacy Architecture

Security and citizen protection are built into the foundation of Scheme Saathi:

### 1. No Aadhaar Number Storage
- In strict adherence to privacy rules, the app **never asks for, accepts, or stores 12-digit Aadhaar numbers**.
- Verified in `SecurityUtils.containsAadhaarNumber`: any 12-digit sequence entered into forms or chat is detected via regex and immediately blocked.

### 2. Field-Level AES-256-GCM Encryption
- Sensitive citizen identifiers (Name, Phone number) are encrypted locally using **AES-256-GCM** before being saved to Cloud Firestore.
- **Key Derivation (as implemented in `SecurityUtils.kt`)**:
  - The encryption key is derived per authenticated user by taking a **SHA-256** hash over the salt string `"scheme-saathi-vault-v1:$userId"`.
  - Encryption uses `Cipher.getInstance("AES/GCM/NoPadding")` with a **unique 12-byte initialization vector (IV)** generated per encryption call using `SecureRandom`.
  - Includes a **128-bit authentication tag**.
  - Ciphertext is stored as `"GCM1:"` followed by Base64-encoded `IV + CipherText + Tag`.

### 3. PII Redaction Before LLM Calls
- Personal names, 10-digit Indian mobile numbers, and bank account sequences are automatically redacted (`[REDACTED_NAME]`, `[REDACTED_PHONE]`) by `SecurityUtils.redactPiiForLlm` before any prompt is dispatched to Gemini.

### 4. Firebase App Check Protection
- Backend resources (Firestore, Storage, Gemini) are guarded by **Firebase App Check** initialized at startup in `AppCheckInitializer.kt`.
- **Debug builds**: Uses `DebugAppCheckProviderFactory` with optional intent extra token support (`DEBUG_TOKEN_EXTRA`).
- **Release builds**: Uses `RecaptchaAppCheckProviderFactory` with a Google reCAPTCHA site key to ensure only genuine, untampered app instances can access backend APIs.

### 5. Human Confirmation Gate
- Scheme Saathi **does not** automatically submit applications to government servers.
- The **Review & Confirm Screen** calculates a **SHA-256 snapshot hash** over all form fields, document statuses, and user declarations (`SecurityUtils.computeSnapshotHash`).
- Requires explicit citizen confirmation and acknowledgment of uncertain fields to generate a structured **Guided Handoff Pack** for submission at an authorized Common Service Centre (CSC) or official portal.

---

## 🤖 AI & Voice Engine (Firebase AI SDK)

Scheme Saathi is **designed to run on free-tier Gemini via Firebase AI SDK (`Firebase.ai`)**:

### Models Configured (Verbatim code identifiers from `SchemeSaathiAiService.kt`):
- **`gemini-3.8-flash`**:
  - Standard bilingual conversational multi-turn chat (`generateContentStream`).
  - High Thinking mode configured with `ThinkingLevel.HIGH` for deep reasoning.
  - Multimodal document photo analysis (extracting certificate types, legibility, and details from photos while redacting any Aadhaar sequences).
  - Real-time spoken voice turn processing.
- **`gemini-3.1-flash-lite`**:
  - Ultra-fast low-latency responses for quick eligibility Q&A.
- **`gemini-3.8-flash-tts`** + Android `TextToSpeech`:
  - Generates culturally natural Hindi (`hi-IN`) and English (`en-IN`) spoken audio scripts before playback via Android's local TTS engine.

### Zero Hardcoded API Keys
The application stores **zero Gemini or OpenAI API keys in code, Gradle files, or resource strings**. All generative AI calls route through the official Firebase AI SDK, which authenticates through Firebase App Check and Google Play Services.

### Deterministic Tool Grounding
Before the AI responds, it executes up to 6 local deterministic tools:
1. `get_profile_summary` — reads redacted profile attributes.
2. `check_eligibility` — runs the deterministic engine.
3. `explain_eligibility` — pulls rule condition outcomes (`ELIGIBLE`, `NOT_ELIGIBLE`, `UNKNOWN`).
4. `get_required_documents` — lists mandatory documents and acceptable alternatives.
5. `list_missing_fields` — lists profile fields causing `UNKNOWN` outcomes.

If the AI call times out (60s) or fails, the engine automatically falls back to an offline deterministic summary.

---

## 📱 Tech Stack & Libraries

- **Language**: Kotlin 2.0
- **UI Toolkit**: Jetpack Compose with Material Design 3 (M3)
- **Architecture**: MVVM + Repository pattern with Unidirectional Data Flow (UDF)
- **Async & State**: Kotlin Coroutines & `StateFlow` (`collectAsStateWithLifecycle`)
- **Authentication**: Android Jetpack `CredentialManager` (Google Sign-In) + Firebase Auth
- **Database**: Cloud Firestore Enterprise with real-time snapshot listeners
- **App Integrity**: Firebase App Check (Debug + Play Integrity / reCAPTCHA)
- **Generative AI**: Firebase AI SDK (`Firebase.ai`)
- **Audio & Voice**: Android `TextToSpeech` + Gemini TTS script synthesis
- **Image Inspection**: AndroidX Activity Result `PickVisualMedia` + Bitmap processing

---

## 🚀 Building from Source

### Prerequisites
- **JDK**: Java 17 or Java 21
- **Android Studio**: Ladybug / Meerkat or command-line Android SDK (API 36 / Build Tools 36.0.0)
- **Android Device / Emulator**: Android 7.0+ (API level 24 or higher)

### 1. Build the Debug APK
```powershell
# Windows
copy app\google-services.json.example app\google-services.json
.\gradlew.bat assembleDebug

# Linux / macOS
cp app/google-services.json.example app/google-services.json
chmod +x gradlew
./gradlew assembleDebug
```
The resulting APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

> **Note on Keystores for Fresh Clones**: `app/build.gradle.kts` automatically detects whether a local custom keystore exists. If absent, it gracefully defaults to standard Android debug signing so the project builds out of the box on any fresh clone without manual keystore configuration.

### 2. Connecting Your Own Firebase Project
To test live Google Sign-In and Cloud Firestore with your own Firebase project:
1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a project.
2. Register an Android App with package name:
   ```
   com.aistudio.schemesaathi.kxmzpq
   ```
3. Add your debug SHA-1 signing certificate fingerprint.
4. Enable **Google** under **Authentication → Sign-in method**.
5. Download your real `google-services.json` and place it in the `app/` folder.

---

## 🧪 Testing Firestore Security Rules

The repository includes a backend unit test suite (`backend/tests/firestore.test.js`) verifying zero-trust Firestore security rules using the `@firebase/rules-unit-testing` harness:
- Unauthenticated users cannot read or write profiles, applications, or tasks.
- Users can only access their own user-scoped subcollections (`users/{userId}/*`).
- 12-digit Aadhaar fields are strictly rejected at the database rule boundary.

### Prerequisites for Rules Testing:
- **Node.js**: v18+ (tested on Node v20/v24)
- **Java Runtime**: Java 11 or higher (required by the Firebase Firestore emulator)

### Running the Test Suite:
```bash
cd backend
npm ci
npm test
```
> **Environment Testing Status**:
> The `npm test` script runs `firebase emulators:exec --only firestore "node --test tests/firestore.test.js"` using the local `firebase-tools` devDependency. In this local development machine, full emulator execution was **not run** during build verification due to absence of the local Firestore emulator runtime.

---

## 📂 Repository Structure

```
saathi/
├── app/
│   ├── google-services.json.example   # Safe Firebase template for fresh clones
│   ├── build.gradle.kts               # Android application configuration & dependencies
│   └── src/main/
│       ├── AndroidManifest.xml        # Permissions, themes, Application declaration
│       ├── java/com/example/
│       │   ├── MainActivity.kt        # Edge-to-edge entry point, App Check init, compose root
│       │   ├── ai/
│       │   │   └── SchemeSaathiAiService.kt   # Multi-model Gemini grounding & TTS engine
│       │   ├── data/
│       │   │   ├── model/SchemeModels.kt      # CitizenProfile, Scheme, Application, Task models
│       │   │   └── repository/                # Cloud Firestore sync & error handler
│       │   ├── domain/
│       │   │   └── EligibilityEngine.kt       # 5-scheme deterministic rule engine
│       │   ├── ui/
│       │   │   ├── auth/AuthComponents.kt     # CredentialManager Google Sign-In button
│       │   │   ├── screens/                   # 15 Jetpack Compose screens
│       │   │   └── theme/                     # Civic color palette, typography & theme
│       │   └── util/
│       │       ├── AppCheckInitializer.kt     # Debug & production Firebase App Check setup
│       │       └── SecurityUtils.kt           # AES-256-GCM, Aadhaar guard, SHA-256 snapshot
│       └── res/                               # Hindi/English strings, drawables, XML layouts
├── backend/                                   # Firebase configuration & security rules
│   ├── firestore.rules                        # Zero-trust Cloud Firestore security rules
│   ├── firebase.json                          # Emulator & database configuration
│   ├── firebase-blueprint.json                # Schema and data model blueprint
│   ├── package.json                           # Rules testing scripts & local firebase-tools
│   ├── package-lock.json                      # Locked dependencies for npm ci
│   └── tests/
│       └── firestore.test.js                  # Rules unit test suite (@firebase/rules-unit-testing)
├── docs/                                      # Architecture, assumptions, and design notes
├── .env.example                               # Environment configuration template
├── LICENSE                                    # MIT License
├── build.gradle.kts                           # Root Gradle project configuration
├── settings.gradle.kts                        # Plugin and repository management
└── gradlew / gradlew.bat                      # Gradle wrapper executables
```

---

## ⚖️ License & Acknowledgments

- Licensed under the [MIT License](LICENSE).
- Government scheme data and benefit definitions are based on publicly available welfare portals under the Government of India.
