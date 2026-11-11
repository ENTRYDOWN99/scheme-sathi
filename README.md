# Scheme Saathi (स्कीम साथी)

> **⚠️ DISCLAIMER**: **Not an official government app.** Scheme Saathi is an independent civic-tech assistant designed for hackathon demonstration. Eligibility results, financial thresholds, and benefit calculations are strictly informational and illustrative; final application decisions and benefit disbursements rest solely with the respective government issuing authorities.

[![Android](https://img.shields.io/badge/Platform-Android_API_24+-3DDC84?style=flat&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin_2.0-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/UI-Jetpack_Compose_Material_3-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Firebase](https://img.shields.io/badge/Backend-Firebase_Auth_|_Firestore_|_App_Check-FFCA28?style=flat&logo=firebase&logoColor=black)](https://firebase.google.com)
[![Gemini](https://img.shields.io/badge/AI-Firebase_AI_SDK_(Gemini)-8E75C2?style=flat&logo=google&logoColor=white)](https://firebase.google.com/docs/ai)

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

## 🚀 Getting Started & Building from Source

### Prerequisites
- **JDK**: Java 17 or Java 21
- **Android Studio**: Ladybug / Meerkat or command-line Android SDK (API 36 / Build Tools 36.0.0)
- **Android Device / Emulator**: Android 7.0+ (API level 24 or higher)

### 1. Clone the Repository
```bash
git clone https://github.com/TODO_YOUR_USERNAME/saathi.git
cd saathi
```

### 2. Configure Firebase (`google-services.json`)
The real `google-services.json` is excluded from Git to prevent credential leaks. 

For **local development and building**:
```powershell
# Copy the provided template to app/google-services.json
copy app\google-services.json.example app\google-services.json
```

For **full Google Sign-In and live Firebase features**:
1. Go to the [Firebase Console](https://console.firebase.google.com/) and create a project.
2. Register an Android App with package name:
   ```
   com.aistudio.schemesaathi.kxmzpq
   ```
3. Add your debug SHA-1 signing fingerprint:
   ```bash
   # View your local debug keystore SHA-1:
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
   ```
4. Enable **Google** under **Authentication → Sign-in method**.
5. Download your real `google-services.json` and place it at:
   ```
   app/google-services.json
   ```

### 3. Build the Debug APK
```powershell
# Windows
.\gradlew.bat assembleDebug

# Linux / macOS
chmod +x gradlew
./gradlew assembleDebug
```
The resulting APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

> **Note on Keystores for Fresh Clones**: `app/build.gradle.kts` automatically detects whether a local custom keystore exists. If absent, it gracefully defaults to standard Android debug signing so the project builds out of the box on any fresh clone without manual keystore configuration.

---

## 🧪 Testing Firestore Security Rules (Optional)

The repository includes a comprehensive unit test suite (`firestore.test.js`) verifying zero-trust access control rules:
- Unauthenticated users cannot read or write profiles, applications, or tasks.
- Users can only access their own subcollections (`users/{userId}/*`).
- Aadhaar fields are strictly rejected by security rules.

To run the rules unit tests:
```bash
# 1. Install dependencies
npm ci

# 2. Start Firebase Emulator and run tests
npm test
```
*(Requires Firebase CLI and Java runtime for emulator suite).*

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
├── docs/                                      # Architecture, assumptions, and design notes
├── firebase.json                              # Firestore emulator ports & rules configuration
├── firestore.rules                            # Zero-trust Firestore security rules
├── firestore.test.js                          # Security rules unit tests
├── package.json                               # Rules testing npm scripts
├── build.gradle.kts                           # Root Gradle project configuration
├── settings.gradle.kts                        # Plugin and repository management
└── gradlew / gradlew.bat                      # Gradle wrapper executables
```

---

## ⚖️ License & Acknowledgments

- Built for the Hackathon by TODO: Your Team Name / Author Name.
- Government scheme data and benefit definitions are based on publicly available welfare portals under the Government of India.
