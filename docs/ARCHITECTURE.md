# Scheme Saathi – Architecture & Security Specification

## 1. System Overview
**Scheme Saathi** is a bilingual (Hindi `hi` / English `en`) Government Welfare Scheme Assistant and Collaborative Task Management application built natively for Android using Kotlin, Jetpack Compose (Material 3), Firebase Authentication (Google Sign-In via Jetpack Credential Manager), Cloud Firestore Enterprise (`ai-studio-android-schemesa-21eb409c-f6fb-4301-b974-a40aa35d6aab`), Firebase App Check, and Firebase AI Logic (`Firebase.ai`).

## 2. Core Architectural Layers
1. **UI & Presentation Layer (`com.example.ui.*`)**:
   - 15 responsive, mobile-first Jetpack Compose screens styled with the civic palette (`#1B5E3F` forest green, `#E07B00` saffron, `#F6F4EF` warm cream, `#FFFFFF` surface) and bundled `Noto Sans` + `Noto Sans Devanagari` typography (18sp default / 22sp Large Text mode).
   - Root authentication gate (`AuthGateScreen`) enforcing Google Sign-In before instantiating feature ViewModels or attaching Firestore snapshot listeners.
2. **Deterministic Domain Layer (`com.example.domain.EligibilityEngine`)**:
   - Pure, synchronous, I/O-free rule evaluator supporting `ELIGIBLE`, `NOT_ELIGIBLE`, and `UNKNOWN`.
   - Operators: `EQ`, `NEQ`, `LT`, `LTE`, `GT`, `GTE`, `IN`, `NOT_IN`, `BETWEEN`, `EXISTS`, `EXTERNAL_LOOKUP`.
   - Combinators: `ALL` (any `NOT_ELIGIBLE` -> `NOT_ELIGIBLE`; else any `UNKNOWN` -> `UNKNOWN`; else `ELIGIBLE`) and `ANY` (any `ELIGIBLE` -> `ELIGIBLE`; all `NOT_ELIGIBLE` -> `NOT_ELIGIBLE`; else `UNKNOWN`).
   - State-specific income threshold overrides (`Uttar Pradesh`, `Bihar`, `Maharashtra`, `Madhya Pradesh`, `Rajasthan`) and external beneficiary lookup guidance (`PM-JAY`).
3. **AI & Voice Orchestration Layer (`com.example.ai.SchemeSaathiAiService`)**:
   - Multi-model Gemini integration via Firebase AI Logic (`Firebase.ai`):
     - `gemini-3.8-flash`: Standard multi-turn bilingual chatbot with streaming (`generateContentStream`).
     - `gemini-3.1-flash-lite`: Low-latency fast response mode.
     - `gemini-4-carbon`: High Thinking mode configured with `ThinkingLevel.HIGH`.
     - `gemini-4-argon`: Complex interactive reasoning and multimodal document photo analysis (`PickVisualMedia` + bitmap inspection).
     - `gemini-3.8-live`: Real-time conversational voice assistant.
     - `gemini-3.8-flash-tts`: Speech script preparation paired with Android `TextToSpeech` (`hi-IN` / `en-IN`).
   - Strict safety invariants: PII redaction before LLM calls, Aadhaar number blocking, prompt-injection detection, and zero confirmation/submission tools exposed to the LLM.
4. **Encrypted Data & Repository Layer (`com.example.data.repository.SchemeSaathiRepository`)**:
   - Field-level AES-256-GCM encryption (`SecurityUtils.encryptPii`) for citizen Name and Phone prior to Cloud Firestore persistence.
   - User-scoped subcollections (`/users/{userId}/profile/{docId}`, `/users/{userId}/applications/{applicationId}`, `/users/{userId}/tasks/{taskId}`) protected by zero-trust `firestore.rules`.
