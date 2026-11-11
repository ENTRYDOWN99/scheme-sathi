# Contributing to Scheme Saathi

Thank you for contributing to Scheme Saathi. Please follow this short workflow before opening a Pull Request.

## 1. Fork & Branch
1. Fork the repository on GitHub (`ENTRYDOWN99/saathi` or your fork remote).
2. Create a focused branch from `main`:
   ```bash
   git checkout -b feat/short-description
   ```
3. Never commit secrets (`.env`, `app/google-services.json`, `*.jks`, `*.keystore`, or `*.apk` / `*.aab`).

## 2. Commit Style
Use Conventional Commits:
- `feat: ...` — new capability or rule enhancement
- `fix: ...` — bug or accessibility fix
- `docs: ...` — documentation updates
- `test: ...` — unit, Compose, or Playwright E2E coverage
- `chore: ...` — build, formatting, or dependency maintenance

## 3. PR Checklist
Before requesting review, run the relevant checks for the workspace(s) you touched:
- [ ] **Backend (`/backend`)**: `npm run lint && npm run format:check && npm run build && npm test`
- [ ] **Web (`/web`)**: `npm run lint && npm run build && npm run test:e2e` (verify `hi`/`en` and zero `@axe-core/playwright` WCAG AA violations)
- [ ] **Android (`/app`)**: `.\gradlew.bat assembleDebug testDebugUnitTest`
- [ ] Preserve all safety invariants: no 12-digit Aadhaar collection/storage, no automatic government portal submission, and deterministic eligibility evaluation only.
- [ ] Update `CHECKLIST.md` or relevant `docs/` if behavior or test commands change.

