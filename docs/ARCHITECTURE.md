# Scheme Saathi – Architecture & Security Specification

## 1. System Overview
**Scheme Saathi** is a bilingual (Hindi `hi` / English `en`) Government Welfare Scheme Assistant and Collaborative Task Management application built natively for Android using Kotlin, Jetpack Compose (Material 3), Firebase Authentication (Google Sign-In via Jetpack Credential Manager), Cloud Firestore, Firebase App Check, and Firebase AI Logic (`Firebase.ai`).

## 2. Core Architectural Layers
1. **UI & Presentation Layer (`com.example.ui.*`)**:
   - Responsive, mobile-first Jetpack Compose screens styled with the civic palette (`#1B5E3F` forest green, `#E07B00` saffron, `#F6F4EF` warm cream, `#FFFFFF` surface) and bundled typography (18sp default / 22sp Large Text mode).
   - Root authentication gate (`AuthGateScreen`) enforcing Google Sign-In before instantiating feature ViewModels or attaching Firestore snapshot listeners.
   - Clean, end-user friendly UI: debug/workflow hubs gated behind `BuildConfig.DEBUG`, plain action labels ("Scan Sample", "Deep Thinking", "Fast Answers"), and localized role and status labels without mixed language artifacts.
2. **Deterministic Domain Layer (`com.example.domain.EligibilityEngine`)**:
   - Pure, synchronous, I/O-free rule evaluator supporting `ELIGIBLE`, `NOT_ELIGIBLE`, and `UNKNOWN`.
   - Operators: `EQ`, `NEQ`, `LT`, `LTE`, `GT`, `GTE`, `IN`, `NOT_IN`, `BETWEEN`, `EXISTS`, `EXTERNAL_LOOKUP`.
   - Combinators: `ALL` (any `NOT_ELIGIBLE` -> `NOT_ELIGIBLE`; else any `UNKNOWN` -> `UNKNOWN`; else `ELIGIBLE`) and `ANY` (any `ELIGIBLE` -> `ELIGIBLE`; all `NOT_ELIGIBLE` -> `NOT_ELIGIBLE`; else `UNKNOWN`).
   - State-specific income threshold overrides (`Uttar Pradesh`, `Bihar`, `Maharashtra`, `Madhya Pradesh`, `Rajasthan`) and external beneficiary lookup guidance (`PM-JAY`).
3. **AI & Voice Orchestration Layer (`com.example.ai.SchemeSaathiAiService`)**:
   - Multi-model Gemini integration via Firebase AI Logic (`Firebase.ai`) with a robust Model Fallback Chain:
     - `gemini-3.8-flash`: Primary multimodal and multi-turn conversational assistant (Free Tier). Supports text, streaming, and image input.
     - `gemini-3.1-flash-lite`: Low-latency fast text mode and primary text fallback.
     - `gemini-3.8-flash-tts`: Speech script preparation paired with Android `TextToSpeech` (`hi-IN` / `en-IN`), with fallback to `gemini-3.8-flash`.
     - `gemini-4-carbon`: High Thinking reasoning mode (fallback chain: `gemini-4-carbon` -> `gemini-3.8-flash` with `ThinkingLevel.HIGH` -> `gemini-3.1-flash-lite`).
     - `gemini-4-argon`: Multimodal document analysis mode (fallback chain: `gemini-4-argon` -> `gemini-3.8-flash` with image input).
   - **Central Error Mapper & Graceful Degradation**:
     - Quota / Rate-limit (429, limit 0, ResourceExhausted) -> user-friendly bilingual notice ("AI is busy right now, please try again in a minute" / "AI सर्वर अभी व्यस्त है...").
     - Network timeouts / connectivity errors -> friendly connection notice ("Please check your internet connection...").
     - Raw exception strings and URLs are logged with `Log.e` only and never exposed in the UI.
     - Chat complete fallback: Deterministic offline rule summary.
     - Document vision fallback: "Could not analyze photo automatically, please tick the checklist manually".
   - **Strict Safety Invariants**:
     - Client-side PII redaction before LLM calls (`SecurityUtils.redactPiiForLlm`).
     - Regex Aadhaar blocker (`SecurityUtils.containsAadhaarNumber`).
     - Prompt injection and auto-submit refusal (`SecurityUtils.detectPromptInjectionOrAutoSubmit`).
     - Zero form submission tools exposed to the LLM (Mandatory Human Confirmation Gate with SHA-256 snapshot hash).
4. **Encrypted Data & Repository Layer (`com.example.data.repository.SchemeSaathiRepository`)**:
   - Field-level AES-256-GCM encryption (`SecurityUtils.encryptPii`) for citizen Name and Phone prior to Cloud Firestore persistence.
   - User-scoped subcollections (`/users/{userId}/profile/{docId}`, `/users/{userId}/applications/{applicationId}`, `/users/{userId}/tasks/{taskId}`) protected by zero-trust `firestore.rules`.
