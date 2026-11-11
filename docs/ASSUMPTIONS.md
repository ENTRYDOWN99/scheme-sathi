# Scheme Saathi – Assumptions & Verification Boundaries

1. **Illustrative Scheme Rules & Thresholds**:
   - The 5 seeded schemes (`PM-KISAN`, `NSAP-IGNOAPS`, `PMUY`, `PM-JAY`, `APY`) use deterministic rule trees whose financial thresholds and state top-ups are explicitly labeled as **illustrative** until verified on the official government portals (`pmkisan.gov.in`, `nsap.nic.in`, `pmuy.gov.in`, `beneficiary.nha.gov.in`, `npscra.nsdl.co.in`).
2. **No Automatic Government Submission**:
   - No direct government submission API exists or is claimed. All prepared applications pass through the **Human Confirmation Gate** (with SHA-256 review snapshot hash verification) to generate a **Guided Handoff Pack** for citizen submission on the official portal or at a Common Service Centre (CSC).
3. **No Aadhaar Collection**:
   - In compliance with citizen privacy rules, 12-digit Aadhaar numbers are blocked at input, never stored in Firestore, and automatically redacted before any AI prompt or document analysis.
4. **Voice Echo-Back Gate**:
   - Spoken age numerals in Hindi (e.g., `बासठ` = 62, `बयासठ` = 62) or English require explicit **हाँ (Yes) / नहीं (No)** confirmation before `ageConfirmedViaVoice` is set to `true`. Unconfirmed voice age values always evaluate to `UNKNOWN`.
