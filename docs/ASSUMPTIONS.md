# Scheme Saathi – Assumptions & Verification Boundaries

1. **Illustrative Scheme Rules & Thresholds**:
   - The 5 seeded schemes (`PM-KISAN`, `NSAP-IGNOAPS`, `PMUY`, `PM-JAY`, `APY`) use deterministic rule trees whose financial thresholds and state top-ups are explicitly labeled as **illustrative** until verified on the official government portals (`pmkisan.gov.in`, `nsap.nic.in`, `pmuy.gov.in`, `beneficiary.nha.gov.in`, `npscra.nsdl.co.in`).
2. **No Automatic Government Submission**:
   - No direct government submission API exists or is claimed. All prepared applications pass through the **Human Confirmation Gate** (with SHA-256 review snapshot hash verification) to generate a **Guided Handoff Pack** for citizen submission on the official portal or at a Common Service Centre (CSC).
3. **No Aadhaar Collection**:
   - In compliance with citizen privacy rules, 12-digit Aadhaar numbers are blocked at input, never stored in Firestore, and automatically redacted before any AI prompt or document analysis.
4. **Voice Echo-Back Gate**:
   - Spoken age numerals in Hindi (e.g., `बासठ` = 62, `बयासठ` = 62) or English require explicit **हाँ (Yes) / नहीं (No)** confirmation before `ageConfirmedViaVoice` is set to `true`. Unconfirmed voice age values always evaluate to `UNKNOWN`.
5. **Date of Birth & Age Calculation**:
   - Citizen Date of Birth (DOB) is collected in `DD/MM/YYYY` format with manual typing (auto-inserted `/` separators) and Material 3 `DatePickerDialog`.
   - Dates are strictly validated against Gregorian calendar rules (rejecting `31/02/2000`, non-leap day `29/02/2001`, future dates, and ages outside 0–120 years).
   - Age is accurately computed by accounting for whether the citizen's birthday has occurred yet in the reference year.
   - DOB is encrypted client-side using AES-256-GCM (`encryptedDob`) before cloud persistence.
   - Plaintext DOB is never sent to the LLM; only the calculated age is provided to AI tools.
   - Backward compatibility is preserved for profiles with only `birthYear` by falling back gracefully, displaying the DOB field empty and prompting the user to complete it.
