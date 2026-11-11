# SchemeSaathi Web UI/UX Redesign Specification (`docs/DESIGN.md`)

> **Status**: Design Specification Only (awaiting `"proceed"` before editing code/tokens/styles)  
> **Scope**: `/web` (Next.js 15 App Router, React 19, Tailwind CSS v4, TypeScript, PWA, `hi`/`en`) + proposed token additions for `shared/design-tokens/tokens.{json,css}`  
> **Non-Official Disclaimer**: SchemeSaathi is **not** an official government application. All eligibility outputs and scheme rules are informational guidance only; final decisions rest with the issuing government authority.

---

## 1. Current UI Audit

### 1.1 What Looks Basic Today
Inspected across `web/src/app/**` and `web/src/components/**`:
1. **Unlayered Visual Hierarchy**: Almost every section uses identical `.saathi-card` white boxes (`border-radius: 16px`, 1px outline, `p-5`/`p-6`) with zero shadow/elevation separation, flat spacing, and no visual distinction between primary metrics, interactive controls, and secondary metadata.
2. **Dated Earthy/Olive Palette**: `tokens.css` currently mixes `#F6F4EF` parchment, `#1B5E3F` dark green, and `#EAE6DD` beige, giving it a generic form-builder appearance rather than a modern, high-trust civic service platform.
3. **Typography & Devanagari Rendering**: `layout.tsx` does not load web fonts via `next/font/google`, relying instead on system fallbacks (`Noto Sans` / `system-ui`). Hindi headings and uppercase tracking (`tracking-wide`) crowd Devanagari *shirorekha* (top bar) and *matras*.
4. **Navigation Chrome**: `AppShell.tsx` places all desktop nav links in a horizontal single top bar with an abrupt bottom 5-tab bar on mobile (`md:hidden`), leaving `/profile` and `/operator` off mobile primary navigation unless linked from Home. There is no collapsible desktop workspace sidebar, user avatar chip, or header theme switcher.
5. **Landing Page (`/`) Lacks Narrative Onboarding**: Signed-out visitors immediately see an inline banner, 4 unstyled demo-role buttons, and dense stacked scheme cards instead of a scannable hero, trust strip, 3-step explanation, featured scheme grid, FAQ, and structured disclaimer footer.
6. **Raw Form & Workflow Screens**:
   - `/login` uses a raw single text box for OTP (`#otp-code-input`) instead of a segmented 6-digit OTP experience, and mixes register/password/OTP tabs with bare outline buttons.
   - `/profile` renders a long flat 12-field grid with no section grouping or step progress, and hides the `<input type="date">` inside a tiny button label.
   - `/chat` uses a minimal fixed box (`min-h-[360px] max-h-[55vh]`) with plain text bubbles, no typing indicator, a single prompt button, and raw meta strings when `busy`/`network`/`generic` errors trigger.
   - `/apply` has no visual stepper showing `Eligibility Checked -> Draft Created -> Human Confirmed -> CSC Handoff Unlocked`, and status badges share generic gray backgrounds.
   - `/operator` lacks KPI cards, status filter pills, table density polish, and empty/skeleton states.
7. **Missing Feedback States**: No unified Skeleton loaders, Toast notifications, Drawers, Tooltips, EmptyStates, or designed ErrorState banners.

### 1.2 Contract Invariants That MUST NOT Change
The redesign **must preserve** all routes, backend API contracts (`@shared/api-client`), deterministic rule logic, safety behavior, and every Playwright/Axe hook verified in [`web/tests/e2e/saathi.spec.ts`](../web/tests/e2e/saathi.spec.ts):

| Category | Exact Identifiers / Text / Contracts to Preserve | Source File |
|---|---|---|
| **Routes** | `/`, `/login`, `/profile`, `/chat`, `/apply`, `/alerts`, `/settings`, `/operator` | `web/src/app/**` |
| **Roles** | `CITIZEN`, `CSC_OPERATOR`, `REVIEWER`, `SUPERVISOR` | `shared/api-client/index.ts` |
| **`data-testid` Hooks** | `data-testid="active-role-badge"`, `data-testid="computed-age-badge"` | `AppShell.tsx`, `profile/page.tsx` |
| **Input Element IDs** | `#otp-identifier`, `#otp-code-input`, `#register-name`, `#auth-identifier`, `#auth-password`, `#dob-input` | `login/page.tsx`, `profile/page.tsx` |
| **Header & Auth Controls** | Button matching `/Toggle language/i` (keeping visible label `हिन्दी`/`EN` + `<span className="sr-only"> Toggle language</span>`), `Sign Out` / `बाहर` button, `Sign In` / `लॉग इन` link | `AppShell.tsx` |
| **i18n JSON Keys (`en.json` / `hi.json`)** | `appName`, `tagline`, `disclaimer`, `noAadhaarNotice`, `nav.*`, `roles.*`, `outcomes.*`, `documents.*`, `dob.*`, `aiErrors.*`, `confirmationGate.*`, `dpdp.*` | `shared/i18n/{en,hi}.json` |
| **Critical Headings & Copy Checked in E2E** | `'OTP से सुरक्षित प्रवेश'`, `'Sign In with One-Time Passcode'`, `'यह कोई आधिकारिक सरकारी ऐप नहीं है'`, `'RBAC Protected'`, `'मार्गदर्शित आवेदन ड्राफ़्ट'`, `'सहयोगी कार्य और अलर्ट'`, `'CSC Operator Workbench'`, `'Reviewer Workbench'`, `'Supervisor Workbench'`, `'Audit Trail (PII Redacted)'`, `'Status: APPROVED'` | `saathi.spec.ts` |
| **E2E Button & Link Names** | `'Password Login'`, `'Phone / Email OTP'`, `'Send OTP'`, `'Verify & Sign In'`, `'Save Profile & Recalculate'`, `'Create Draft & Open Human Confirmation Gate'`, `'Confirm & Unlock Guided Handoff'`, `'Copy Guided Handoff Pack'`, `'Send'`, `'भेजें'`, `'Approve'`, `'Continue as CSC Operator'`, `'Continue as Reviewer'`, `'Continue as Supervisor'` | `saathi.spec.ts` |
| **Safety Notices & Gate Behavior** | Top/banner non-official app disclaimer (`t.disclaimer`); No-Aadhaar notice (`t.noAadhaarNotice`); 3 required checkboxes inside `role="dialog"` on Human Confirmation Gate keeping submit disabled until all 3 are checked; AI auto-submit safety block; AI `busy`/`network`/`generic` fallback copy (`t.aiErrors.*`) | `AppShell.tsx`, `HumanConfirmationGateModal.tsx`, `chat/page.tsx` |

---

## 2. Design Principles & Brand Personality

### 2.1 Brand Personality
- **Calm, Confident, Trustworthy**: Crisp layout discipline inspired by **Stripe** and **Linear** — generous whitespace, clean hairlines, clear tabular hierarchy, zero visual clutter.
- **Warm Indian Public-Service Character**: Deep civic navy/indigo paired with measured warm saffron highlights and fluent Devanagari typography (`Noto Sans Devanagari`), feeling helpful and human for both rural/semi-urban citizens on mobile and CSC Operators / Reviewers on desktop.

### 2.2 Explicit Non-Government Branding Rules
- **Prohibited Imagery**: **Never** use the National Emblem of India (Lion Capital of Ashoka), Ashoka Chakra, national tricolor flag motifs styled as official seals, ministerial crests, or phrases implying government endorsement.
- **Logo Mark**: Keep a clean geometric monogram (`सा` / abstract shield-book mark in civic indigo `#1E3A8A` with subtle saffron dot/accent), paired with `"SchemeSaathi"` / `"स्कीम साथी"`.
- **Mandatory Non-Official Disclosure**: Retain `t.disclaimer` (`"Not an official government app..."` / `"यह कोई आधिकारिक सरकारी ऐप नहीं है..."`) visibly in the persistent top notice strip, the landing footer, and the Human Confirmation Gate.

---

## 3. Color System (Light & Dark Mode + WCAG AA Contrast Verification)

### 3.1 Palette Philosophy
- **Primary (Civic Navy / Indigo)**: Anchors navigation active states, primary CTAs, links, and structural authority without feeling bureaucratic.
- **Accent (Warm Saffron / Amber-Orange)**: Reserved for step highlights, focus rings, unread counters, Human Confirmation Gate shield alerts, and warm civic warmth. Interactive saffron buttons with white labels use `#B45309` (WCAG AA compliant `5.02:1`), reserving `#F59E0B` for dark-mode solid fills (`#0B1120` text, `8.77:1`), icons, or 2px border accents.
- **Semantic States**: Emerald green (`Eligible` / `Have` / `Approved`), Amber (`Unknown / Needs Info` / warning), Crimson red (`Not Eligible` / `Missing` / `Rejected` / `Error`).
- **Neutral Surfaces**: Cool crisp slate-whites (`#F8FAFC`, `#FFFFFF`, `#F1F5F9`) in Light Mode; deep midnight slates (`#0B1120`, `#111827`, `#1E293B`) in Dark Mode.

### 3.2 Exact Hex Values (Light & Dark Mode)

| Role | Light Hex | Dark Hex | Usage |
|---|---|---|---|
| **Canvas Background** | `#F8FAFC` | `#0B1120` | App root background |
| **Elevated Surface** | `#FFFFFF` | `#111827` | Cards, top bar, sidebar, modals, drawers |
| **Subtle Surface** | `#F1F5F9` | `#1E293B` | Table headers, assistant chat bubble, input wells, secondary pills |
| **Surface Hover** | `#E2E8F0` | `#334155` | Row/button hover backgrounds |
| **Primary Text** | `#0F172A` | `#F8FAFC` | Headings, body copy, primary values |
| **Muted Text** | `#475569` | `#CBD5E1` | Subtitles, metadata, timestamps (`#94A3B8` permitted only on `#111827`/`#0B1120`) |
| **Subtle Hairline Border** | `#CBD5E1` | `#334155` | Card dividers, table row hairlines |
| **Interactive Input Outline** | `#64748B` | `#64748B` | Form borders, checkbox/outline controls (`>= 3:1` UI component contrast) |
| **Primary (Navy/Indigo)** | `#1E3A8A` | `#818CF8` | Primary CTA background, active indicators, links |
| **On Primary** | `#FFFFFF` | `#0B1120` | Text/icons inside solid Primary controls |
| **Primary Hover** | `#1E40AF` | `#A5B4FC` | Primary CTA hover |
| **Primary Container** | `#E0E7FF` | `#1E293B` | Active nav background, trust highlight cards |
| **On Primary Container** | `#1E1B4B` | `#E0E7FF` | Text inside Primary Container |
| **Secondary Accent (Warm Saffron)** | `#B45309` | `#F59E0B` | Secondary CTA, unread pill, confirmation highlight |
| **On Secondary** | `#FFFFFF` | `#0B1120` | Text inside solid Saffron controls (eliminates hardcoded `text-white` dark-mode issue) |
| **Secondary Container** | `#FEF3C7` | `#451A03` | Disclaimer banner, warning callouts |
| **On Secondary Container** | `#78350F` | `#FDE68A` | Disclaimer & warning callout text |
| **Success / Eligible Bg** | `#ECFDF5` | `#064E3B` | `ELIGIBLE`, `HAVE`, `APPROVED` chips & success alerts |
| **Success / Eligible Fg** | `#065F46` | `#6EE7B7` | Text inside Success chips/alerts |
| **Warning / Unknown Bg** | `#FFFBEB` | `#451A03` | `UNKNOWN`, `Needs Info`, `busy`/`AI Warning` chips |
| **Warning / Unknown Fg** | `#92400E` | `#FCD34D` | Text inside Warning chips |
| **Error / Not Eligible Bg** | `#FEF2F2` | `#450A0A` | `NOT_ELIGIBLE`, `MISSING`, `REJECTED`, safety-block bubble |
| **Error / Not Eligible Fg** | `#B91C1C` | `#FCA5A5` | Text inside Error chips & form errors |

### 3.3 Contrast Ratio Table (Computed WCAG 2.1 Ratios)
*(Minimum targets: **4.5:1** normal text, **3.0:1** large text `>=18pt/14pt bold` & UI borders.)*

| Mode | Foreground on Background | Hex Pair | Measured Ratio | WCAG AA Status |
|---|---|---|---|---|
| Light | Primary text on Canvas | `#0F172A` on `#F8FAFC` | **17.06:1** | Pass (AAA) |
| Light | Primary text on Surface | `#0F172A` on `#FFFFFF` | **17.85:1** | Pass (AAA) |
| Light | Muted text on Surface | `#475569` on `#FFFFFF` | **7.58:1** | Pass (AAA) |
| Light | Muted text on Subtle Surface | `#475569` on `#F1F5F9` | **6.92:1** | Pass (AA+) |
| Light | On Primary on Primary | `#FFFFFF` on `#1E3A8A` | **10.36:1** | Pass (AAA) |
| Light | On Primary Hover on Primary Hover | `#FFFFFF` on `#1E40AF` | **8.72:1** | Pass (AAA) |
| Light | On Primary Container on Container | `#1E1B4B` on `#E0E7FF` | **12.98:1** | Pass (AAA) |
| Light | On Saffron on Saffron | `#FFFFFF` on `#B45309` | **5.02:1** | Pass (AA) |
| Light | On Saffron Container on Container | `#78350F` on `#FEF3C7` | **8.15:1** | Pass (AAA) |
| Light | Eligible Fg on Eligible Bg | `#065F46` on `#ECFDF5` | **7.29:1** | Pass (AAA) |
| Light | Unknown Fg on Unknown Bg | `#92400E` on `#FFFBEB` | **6.84:1** | Pass (AA+) |
| Light | Not Eligible Fg on Not Eligible Bg | `#B91C1C` on `#FEF2F2` | **5.91:1** | Pass (AA+) |
| Light | Error text on Surface | `#B91C1C` on `#FFFFFF` | **6.47:1** | Pass (AA+) |
| Light | Input Outline border on Surface | `#64748B` on `#FFFFFF` | **4.76:1** | Pass (UI >= 3:1) |
| Dark | Primary text on Canvas | `#F8FAFC` on `#0B1120` | **18.00:1** | Pass (AAA) |
| Dark | Primary text on Surface | `#F8FAFC` on `#111827` | **16.96:1** | Pass (AAA) |
| Dark | Muted text (`Slate 400`) on Surface | `#94A3B8` on `#111827` | **6.92:1** | Pass (AA+) |
| Dark | Muted text (`Slate 300`) on Subtle | `#CBD5E1` on `#1E293B` | **9.85:1** | Pass (AAA) |
| Dark | On Primary on Primary | `#0B1120` on `#818CF8` | **6.31:1** | Pass (AA+) |
| Dark | On Primary Container on Container | `#E0E7FF` on `#1E293B` | **11.87:1** | Pass (AAA) |
| Dark | On Saffron on Saffron | `#0B1120` on `#F59E0B` | **8.77:1** | Pass (AAA) |
| Dark | On Saffron Container on Container | `#FDE68A` on `#451A03` | **12.03:1** | Pass (AAA) |
| Dark | Eligible Fg on Eligible Bg | `#6EE7B7` on `#064E3B` | **6.38:1** | Pass (AA+) |
| Dark | Unknown Fg on Unknown Bg | `#FCD34D` on `#451A03` | **10.39:1** | Pass (AAA) |
| Dark | Not Eligible Fg on Not Eligible Bg | `#FCA5A5` on `#450A0A` | **8.51:1** | Pass (AAA) |
| Dark | Error text on Surface | `#FCA5A5` on `#111827` | **9.35:1** | Pass (AAA) |
| Dark | Input Outline border on Surface | `#64748B` on `#111827` | **3.73:1** | Pass (UI >= 3:1) |

---

## 4. Typography (`next/font/google` + Devanagari Rules)

### 4.1 Font Families
Loaded cleanly via `next/font/google` in `web/src/app/layout.tsx` using CSS variables (zero external CSS blocking requests):
- **UI & Latin**: `Plus Jakarta Sans` (weights `400, 500, 600, 700, 800`, `--font-sans`) with `Inter` / `system-ui` fallback.
- **Hindi / Devanagari**: `Noto Sans Devanagari` (weights `400, 500, 600, 700`, `--font-devanagari`), placed directly after `--font-sans` in the font stack:
  `font-family: var(--font-sans), var(--font-devanagari), system-ui, -apple-system, sans-serif;`
- **Monospace Data**: `JetBrains Mono` (`400, 500, 600`, `--font-mono`) for `DD/MM/YYYY` DOB input, SHA-256 snapshot hashes, IFSC codes, application IDs, and audit log timestamps.

### 4.2 Type Scale (Base `16px`; `.large-text` scales root to `20px`)

| Token / Role | Desktop Size | Mobile Size | Weight | Latin Line-Height | Devanagari (`html[lang="hi"]`) Line-Height | Tracking |
|---|---|---|---|---|---|---|
| `display` (Hero H1) | `2.5rem` (`40px`) | `1.875rem` (`30px`) | `800` | `1.15` | `1.30` | `-0.02em` (Latin) / `0` (Hindi) |
| `h1` (Page Title) | `1.75rem` (`28px`) | `1.5rem` (`24px`) | `700` | `1.25` | `1.38` | `-0.015em` / `0` |
| `h2` (Section Header) | `1.25rem` (`20px`) | `1.125rem` (`18px`) | `700` | `1.30` | `1.45` | `-0.01em` / `0` |
| `h3` (Card Title) | `1.0625rem` (`17px`) | `1rem` (`16px`) | `600` | `1.35` | `1.50` | `0` |
| `body` (Default Copy) | `1rem` (`16px`) | `1rem` (`16px`) | `400` | `1.55` | `1.70` | `0` |
| `body-sm` (Descriptions) | `0.875rem` (`14px`) | `0.875rem` (`14px`) | `400/500` | `1.50` | `1.65` | `0` |
| `caption` (Meta / Labels) | `0.75rem` (`12px`) | `0.75rem` (`12px`) | `600` | `1.40` | `1.55` | `+0.02em` (Latin) / `0` (Hindi) |
| `mono-sm` (Hashes / DOB) | `0.8125rem` (`13px`) | `0.8125rem` (`13px`) | `500` | `1.45` | — | `0.01em` |

**Devanagari Line-Height & Clipping Rules**:
1. When `html[lang="hi"]` is active, apply `--saathi-leading-body: 1.7` and `--saathi-leading-heading: 1.38` so upper vowel *matras* (`ि`, `ी`, `ै`, `ं`) and lower *ukars* (`ु`, `ू`, `ृ`) never collide across wrapped lines.
2. **Never** apply negative letter-spacing (`tracking-tight`) or CSS `text-transform: uppercase` to Devanagari strings (use `font-semibold` pill tags instead).
3. Use `tabular-nums` on age badges, counts, income fields, timestamps, and OTP inputs.

---

## 5. Spacing, Radius, Elevation, Motion & Icons

### 5.1 Spacing Scale (8px Grid)
- `xs`: `4px` (`0.25rem`) — tight icon/badge gap
- `sm`: `8px` (`0.5rem`) — compact field/chip spacing
- `md`: `16px` (`1rem`) — card internal mobile padding, form field stack
- `lg`: `24px` (`1.5rem`) — card desktop padding, grid gaps
- `xl`: `32px` (`2rem`) — section vertical separation
- `2xl`: `48px` (`3rem`) — hero & page section breaks
- **Touch Target Floor**: Navigation & bottom tabs **minimum `44px x 44px`** (`WCAG 2.5.5` / Apple/Material baseline); primary citizen action buttons, inputs, and checkboxes maintain **`48px`** height.

### 5.2 Radius Scale
- `sm`: `8px` — checkboxes, small chips, segmented OTP cells, table badges
- `md`: `12px` — inputs, selects, buttons, alert strips, nav items
- `lg`: `16px` — standard cards, chat bubbles, drawers
- `xl`: `20px` — modals (`HumanConfirmationGateModal`), hero callouts
- `full`: `9999px` — status pills, avatars, unread count badges

### 5.3 Elevation & Shadow Scale (Subtle Border + Soft Tint)
- `elevation-0`: `none` — inline sections on `#F8FAFC` / `#0B1120`
- `elevation-1` (Cards): `1px solid borderSubtle` + `0 1px 2px rgba(15, 23, 42, 0.05)` (`dark`: `0 1px 2px rgba(0, 0, 0, 0.35)`)
- `elevation-2` (Sticky Header / Hover Card / Dropdown): `1px solid borderSubtle` + `0 4px 12px rgba(15, 23, 42, 0.08)`
- `elevation-3` (Modals / Drawers / Toasts): `0 16px 40px -8px rgba(15, 23, 42, 0.18)`

### 5.4 Motion Rules
- **Micro-interactions (hover, chip toggle, tab active)**: `150ms cubic-bezier(0.16, 1, 0.3, 1)` (`ease-out`)
- **Drawers, Accordions, Modals, Toasts**: `200ms–250ms cubic-bezier(0.16, 1, 0.3, 1)`
- **Focus Ring**: Instant `0ms` appearance (`3px solid` saffron `#B45309` / `#F59E0B` with `2px` offset)
- **Reduced Motion**: Global `@media (prefers-reduced-motion: reduce)` rule setting `animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; scroll-behavior: auto !important;`

### 5.5 Iconography
- Standardize strictly on **`lucide-react`** (`1.75px` stroke width, `aria-hidden="true"` when paired with text, explicit `aria-label` when standalone icon button).
- Size grid: `16px` inline buttons/badges, `20px` nav items & inputs, `24px` card headers & mobile bottom tabs.

---

## 6. Component Inventory (All 7 Required States)

*(All components implement `default`, `hover`, `focus`, `active`, `disabled`, `loading`, `error` cleanly in both Light & Dark modes.)*

| Component | Default | Hover | Focus (`:focus-visible`) | Active / Pressed | Disabled | Loading | Error |
|---|---|---|---|---|---|---|---|
| **Button** (`Primary`, `Secondary Saffron`, `Outline`, `Ghost`, `Danger`) | Solid `#1E3A8A` / `#B45309` or `1px` outline; `min-h-[48px]` (CTA) / `40px` (compact); `12px` radius | `#1E40AF` / `#92400E` or `bgSurfaceSubtle`; subtle `150ms` shift | `3px` saffron focus ring + `2px` offset | `scale(0.98)` press + darker fill | `opacity: 0.45`, `cursor-not-allowed`, no hover shadow | Inline spinner (`16px` `Loader2`), label preserved or `aria-busy="true"`, click blocked | Danger variant (`#B91C1C` / `#FCA5A5` dark) or `aria-invalid` trigger toast |
| **Input** (`Text`, `Number`, `Search`, `Password`, `DOB`) | `bgSurface`, `1px solid outline` (`#64748B`), `48px` height, `12px` radius, explicit `<label htmlFor>` | Border darkens to `textPrimary` | `2px` primary/saffron ring + border `#1E3A8A` (`#818CF8` dark) | Caret visible, active clear/toggle eye icon | `bgSurfaceSubtle`, `opacity: 0.6`, read-only feel | Right-aligned `16px` spinner inside trailing adornment | `2px` border `#B91C1C` (`#FCA5A5` dark), `aria-invalid="true"`, `aria-describedby` inline `role="alert"` below |
| **Select** | Native accessible `<select>` or styled listbox (`48px`), chevron icon right (`pointer-events-none`) | Border `textPrimary`, `bgSurface` | `3px` focus ring + offset | Native menu / active highlight | `opacity: 0.6`, `bgSurfaceSubtle` | Spinner replaces chevron while options hydrate | `2px` error border + helper `role="alert"` |
| **Checkbox** (`HumanConfirmationGateModal` + filters) | `20px` box inside `>=44px` clickable `<label>` row, `1.5px` `#64748B` border | Subtle row tint `bgSurfaceSubtle`, border `#1E3A8A` | `3px` focus ring around box | Checked fill `#1E3A8A` / `#818CF8` + crisp checkmark | `opacity: 0.45` | Subtle pulse on row if async | Red border + warning message on container |
| **OTPInput** (Segmented 6-digit UX + single DOM `#otp-code-input` sync) | Six `48x52px` digit cells visually synchronized with an accessible `<input id="otp-code-input" inputMode="numeric" maxLength={6} autoComplete="one-time-code" />` so Playwright `.fill('000000')` and password/SMS autofill work natively | Active cell border highlights | Focused cell shows `2px` indigo/saffron ring | Active cell subtly scales text | All cells `bgSurfaceSubtle` `opacity: 0.5` | Inline verifying shimmer in verify CTA | All 6 cells show `#B91C1C` border + shake/static alert (`role="alert"`) |
| **Card** (`Interactive`, `Static`, `Alert Highlight`) | `bgSurface`, `1px` `borderSubtle`, `elevation-1`, `16px` radius, `16px/24px` padding | Interactive cards lift to `elevation-2` + border tint | Ring around interactive link/button | `elevation-1` slight depress | `opacity: 0.65` | Internal `Skeleton` lines | Top/left `4px` `#B91C1C` accent stripe |
| **Badge / StatusChip** (`OutcomeBadge`, `DocumentStatusBadge`, `AppStatusChip`, `RoleBadge`) | `9999px` pill, `12px` font-bold, `6px` icon dot + text (never color-only!) | Interactive filter chips gain border highlight | `3px` focus ring on interactive chips | Solid fill when selected | `opacity: 0.5` | Pulse pill (`w-20 h-6`) | Explicit `NOT_ELIGIBLE` / `MISSING` / `REJECTED` red tint |
| **Tabs** (Login modes, Role workbench sub-views) | Segmented track `bgSurfaceSubtle`, inactive transparent muted text (`min-h-[44px]`) | Muted -> `textPrimary` | Focus ring on active tab | Selected tab gets `bgSurface` + `elevation-1` + `font-semibold` | `opacity: 0.45` | Content panel shows skeleton | Inline error alert badge on tab if validation failed |
| **Stepper** (`/profile` & `/apply` progress) | Horizontal numbered steps (`40px` circle + connector + concise label; vertical compact stack on `<480px`) | Step titles clickable only if completed | Focus ring on navigable past step | Current step highlighted `#1E3A8A` + saffron progress bar | Future locked steps muted `#64748B` | Active step shows subtle spinner on transition | Step circle switches to exclamation `#B91C1C` if invalid |
| **Modal** (`HumanConfirmationGateModal`, Confirm Erasure) | Centered `max-w-lg` sheet (`20px` radius, `elevation-3`, `backdrop-blur-sm bg-slate-950/60`, `role="dialog"`, `aria-modal="true"`) | Close (`X`) & footer buttons gain hover state | Focus trapped on open, `Esc` closes when safe | Button press feedback | Backdrop interaction blocked while submitting | Submit button locked with spinner | Persistent `role="alert"` banner inside modal above actions |
| **Drawer** (Mobile Sidebar Overflow / Scheme Rule Details) | Slide-over sheet (`280px` left nav on mobile / bottom sheet for mobile detail), `elevation-3` | Close handle / item hover | Focus trapped inside open drawer | Active item highlight | Dimmed backdrop | Skeleton body | ErrorState inside drawer body |
| **Toast** (Profile saved, Handoff copied, DPDP exported) | Fixed bottom-right desktop / top-below-header mobile (`elevation-3`, `aria-live="polite"`, `<= 4s` non-critical; persistent if action needed) | Hover pauses auto-dismiss timer | Focusable close button | Close press | — | Spinner toast for long exports | Red border + `AlertCircle` icon (`role="alert"`) |
| **Table** (Audit Trail, CSC Metrics) | Sticky `bgSurfaceSubtle` `<thead>`, `12px`/`14px` rows, `1px` `borderSubtle` bottom hairlines, monospace IDs/timestamps, `overflow-x-auto` wrapper | Row `hover:bgSurfaceSubtle/70` | Keyboard-focusable sort/action buttons inside cells | Selected row subtle indigo tint | Dimmed disabled row actions | 5-row tabular `Skeleton` | `ErrorState` spanning card with Retry |
| **Skeleton** | `bgSurfaceSubtle` rounded blocks matching target height, soft opacity shimmer (static solid block if `prefers-reduced-motion`) | — | — | — | — | Always loading (`aria-busy="true"`) | Replaced by `ErrorState` on fetch failure |
| **EmptyState** (No filtered schemes, zero alerts, no applications) | Centered soft icon container (`48px`), clear bilingual headline, 1-line next-step copy, 1 clear CTA | CTA hover | CTA focus | CTA press | — | Switches to `Skeleton` | Switches to `ErrorState` |
| **ErrorState** (AI Busy / Network / Generic / Fetch fail) | Distinct tinted banner (`Warning` for `busy`, `Subtle` for `network`, `Error` for `generic`/`API failure`), clear Lucide icon, friendly `t.aiErrors.*` / retry message | Retry button hover | Focusable Retry CTA | Retry press | Disabled while retrying | Retry shows spinner | Always communicates failure cleanly without raw stack traces |
| **Avatar** (Signed-in user & chat sender) | `36px`/`40px` circle with user initials on `#E0E7FF` (`#1E1B4B`) for Citizen, `#FEF3C7` (`#78350F`) for Staff, `Sparkles` bot badge for Assistant | Subtle ring highlight when clickable | Focus ring if profile link | Subtle press | — | Circular skeleton | Generic `User` icon fallback |
| **Tooltip** (IFSC hint, Snapshot SHA-256 explanation, Collapsed Sidebar label) | High-contrast dark pill (`#0F172A` on light, `#F8FAFC` on dark), `role="tooltip"`, triggered by hover + keyboard `:focus` (never hover-only!) | Appears after `150ms` | Appears immediately on focus, dismisses on `Esc` | — | Hidden when disabled | — | — |

---

## 7. App Shell & Role-Aware Navigation

### 7.1 Persistent Safety & Utility Chrome
1. **Top Non-Official Disclaimer Strip**: Full-width compact bar (`min-h-[32px]`, `role="note"`) displaying `{t.disclaimer}` (`#FEF3C7` / `#78350F` light; `#451A03` / `#FDE68A` dark) so the non-official disclaimer is unmistakable on every screen.
2. **Language Switcher & Theme Toggle Placement**:
   - **Desktop Top Bar (right utility cluster)**:
     - **Theme Toggle** button (`Sun` / `Moon`, `aria-label="Toggle dark mode"`, `>=44x44px`).
     - **Language Switcher** pill (`EN` when `hi`, `हिन्दी` when `en` + `<span className="sr-only"> Toggle language</span>`, keeping the exact accessible name expected by E2E `getByRole('button', { name: /Toggle language/i })`).
     - **Active Role Badge** (`data-testid="active-role-badge"`) + **Avatar** + **Sign Out** / **Sign In** CTA.
   - **Mobile Top Bar**: Logo + condensed **Theme Toggle** (`44x44px`) + **Language Switcher** (`44px` height) + **Sign In / Role & Sign Out** trigger (or Sidebar Drawer button for staff/profile overflow).
   - Both preferences also stay editable on `/settings`.

### 7.2 Desktop Shell (Top Bar + Collapsible Sidebar `>= 1024px`, `768px` collapses by default)
- **Top Bar (`64px` height, sticky)**: Left brand mark + collapse/expand sidebar toggle (`PanelLeft`); right utility cluster (Theme, `EN`/`हिन्दी`, Role Badge, Sign Out / Sign In).
- **Collapsible Left Sidebar (`240px` expanded / `72px` collapsed rail with Tooltips)**:
  - **Primary Group (All Users)**:
    - `/` — Home (`t.nav.home`)
    - `/chat` — AI Chat (`t.nav.chat`)
    - `/apply` — Apply (`t.nav.apply`)
    - `/alerts` — Alerts (`t.nav.alerts` + unread badge)
    - `/profile` — Profile (`t.nav.profile`, visible when signed in)
  - **Staff Role Group (`CSC_OPERATOR`, `REVIEWER`, `SUPERVISOR` only)**:
    - `/operator` — CSC / Review (`t.nav.operator`) with role-specific subtitle (`Operator Queue`, `Approval Desk`, `Supervisor Audit`)
  - **System Group**:
    - `/settings` — Settings (`t.nav.settings`)
  - **Sidebar Footer Card (when expanded)**: Compact "No Aadhaar Stored" lock badge + role pill.

### 7.3 Mobile Shell (`< 768px`)
- **Mobile Top Bar (`56px` height)**: Hamburger/Drawer trigger (for `/profile` & `/operator` access when signed in), Brand logo, Theme icon toggle (`44px`), Language toggle (`EN`/`हिन्दी`, `44px`), Sign In / Sign Out.
- **Mobile Bottom Tab Bar (`64px` height, fixed bottom, `grid-cols-5`, every tab `>=44x64px` touch target)**:
  1. **Home** (`/`)
  2. **AI Chat** (`/chat`)
  3. **Apply** (`/apply`)
  4. **Alerts** (`/alerts` + unread count pill using `var(--saathi-secondary)` & `var(--saathi-on-secondary)`)
  5. **Settings** (`/settings`) — or for signed-in `CSC_OPERATOR`/`REVIEWER`/`SUPERVISOR`, `CSC / Review` (`/operator`) stays highlighted in the Mobile Drawer + top banner CTA so all 5 core citizen tabs remain identical across breakpoints and E2E scripts.

---

## 8. Page-by-Page Layout Notes & ASCII Wireframes

*(All wireframes preserve existing route URLs, element IDs, `data-testid` hooks, and E2E button copy.)*

### 8.1 Landing & Scheme Explorer (`/`)
**Sections (in order)**:
1. **Signed-Out Hero + Trust Strip** (or **Signed-In Citizen Welcome Summary Bar** with `Edit Profile & DOB` + `AI Chat` CTAs).
2. **Trust Strip**: 3 concise verification pills (`No Aadhaar stored`, `Human-confirmed applications`, `Hindi + English`).
3. **3-Step How It Works** (signed out): `1. Profile & DOB` -> `2. Deterministic Rule Check` -> `3. Human Confirmation & CSC Handoff`.
4. **Role Demo Quick Cards** (signed out): 4 clean cards (`CITIZEN`, `CSC_OPERATOR`, `REVIEWER`, `SUPERVISOR`) triggering `demoSignIn(role)`.
5. **Featured Schemes & Search/Filter Bar**: Instant search input + outcome pills (`All`, `Eligible`, `Needs Info`, `Not Eligible`), expanding inline scheme cards with Deterministic Rule Trace and persistent `HAVE`/`MISSING`/`UNKNOWN` Document Checklist chips.
6. **FAQ Accordion + Footer**: Answers common citizen questions ("Is this an official government portal?", "Why don't you ask for Aadhaar?") and anchors the non-official disclaimer footer.

```text
DESKTOP (/ - 1280px)
+-------------------------------------------------------------------------+
| [!] Not an official government app. Informational guidance only...      |
+----------+--------------------------------------------------------------+
| [सा]     | [Collapse]                           [Theme] [हिन्दी] [Login]|
| Scheme   +--------------------------------------------------------------+
| Saathi   | HERO: Know Your Welfare Eligibility Without Sharing Aadhaar  |
|          | [Sign In / OTP Access ->]   [Try Demo Roles v]               |
| [Home]   | TRUST: [Lock] No Aadhaar Stored | [Shield] Human-Confirmed | |
| [Chat]   |        [Languages] Hindi + English                           |
| [Apply]  +--------------------------------------------------------------+
| [Alerts] | HOW IT WORKS: [1. Profile/DOB] -> [2. Rules] -> [3. Handoff] |
| [Settin] +--------------------------------------------------------------+
|          | ROLE CARDS: [Citizen] [CSC Operator] [Reviewer] [Supervisor] |
|          +--------------------------------------------------------------+
|          | [Search schemes...]            [All][Eligible][Unknown][Not] |
|          | +----------------------------------------------------------+ |
|          | | PM-KISAN  (Agriculture • v1)         [Outcome] [Rules v] | |
|          | | Trace: Age >= 18 [PASS] ...                              | |
|          | | Docs: Land Record [HAVE][MISSING][UNKNOWN] ...           | |
|          | +----------------------------------------------------------+ |
|          | FAQ (Accordion) & Footer w/ Non-Official Disclaimer          |
+----------+--------------------------------------------------------------+

MOBILE (/ - 360px)
+----------------------------------+
| [!] Not an official govt app...  |
| [=] [सा] SchemeSaathi [Sun][EN]  |
+----------------------------------+
| HERO + Trust Chips (stacked)     |
| [Sign In / OTP Access ->]        |
| 3-Step How It Works (compact)    |
| 2x2 Role Demo Grid               |
| [Search schemes...]              |
| [PM-KISAN Card + Doc Chips]      |
| FAQ + Footer Disclaimer          |
+----------------------------------+
| [Home][Chat][Apply][Alerts][Set] |
+----------------------------------+
```

### 8.2 Login (`/login`)
- Clean single centered card (`max-w-xl`) + Role Demo Grid below.
- Segmented tab bar: `Phone / Email OTP` | `Password Login` | `Register Citizen`.
- OTP flow shows `#otp-identifier` + `Send OTP`, dev OTP badge when returned, and segmented **6-box visual OTP UI** backed directly by `#otp-code-input` so typing, mobile SMS autofill, and automated E2E `.fill()` all work cleanly.
- Inline error banner (`role="alert"`) displays wrong password / wrong OTP errors with high-contrast red framing.

```text
DESKTOP (/login - 1280px)                MOBILE (/login - 360px)
+-----------------------------------+    +------------------------------+
|  [Phone/OTP] [Password] [Register]|    | [Phone/OTP][Password][Reg]   |
|  Sign In with One-Time Passcode   |    | Sign In with OTP             |
|  [#otp-identifier     ] [Send OTP]|    | [#otp-identifier           ] |
|  (Dev OTP Code: 123456)           |    | [Send OTP (full width)     ] |
|  Enter 6-digit OTP:               |    | [0][0][0] [0][0][0]          |
|  [ _ ][ _ ][ _ ]  [ _ ][ _ ][ _ ] |    | [ Verify & Sign In         ] |
|  [    Verify & Sign In          ] |    | [!] Role Alert on Failure    |
|  [!] Error Alert (if wrong OTP)   |    |                              |
+-----------------------------------+    | Role Demo Cards (stacked):   |
|  One-Click Role Demo Access       |    | - Citizen / CSC Operator     |
|  [Citizen]        [CSC Operator]  |    | - Reviewer / Supervisor      |
|  [Reviewer]       [Supervisor]    |    +------------------------------+
+-----------------------------------+
```

### 8.3 Profile & DOB (`/profile`)
- **3-Step Visual Stepper** (`1. Identity & DOB` -> `2. Location & Household` -> `3. Socio-Economic Status` — either all visible as grouped sections or tabbed/stepped while keeping all form fields accessible for single-submit `"Save Profile & Recalculate"`, preventing broken form state!).
- **Prominent DOB Control**: `#dob-input` monospace formatted `DD/MM/YYYY` + accessible Calendar trigger + live `data-testid="computed-age-badge"` (`Age: 62 yrs`) + instant `role="alert"` inline validation errors (`31/02/2000` rejected cleanly).
- **Document Status Chips Strip**: Summary row at bottom showing current persistent counts (`X HAVE`, `Y MISSING`, `Z UNKNOWN`) with quick link back to scheme checklists.

```text
DESKTOP (/profile - 1280px)              MOBILE (/profile - 360px)
+-----------------------------------+    +------------------------------+
| [Lock] Never enter Aadhaar number |    | [Lock] No Aadhaar Notice     |
| Stepper: (1) Identity -> (2) Home |    | Step 1 of 3: Identity & DOB  |
|          -> (3) Eligibility Flags |    | Full Name: [________________]|
| +-------------------------------+ |    | DOB (DD/MM/YYYY) (Age:62 yrs)|
| | Full Name  | DOB (Age:62 yrs) | |    | [#dob-input      ] [Cal Btn] |
| | [________] | [__/__/____][Cal]| |    | [!] Invalid calendar date    |
| | State      | District         | |    | State / District / Income... |
| | [Select v] | [________]       | |    | Document Chips Summary:      |
| | Income     | BPL / Category   | |    | [HAVE: 3] [MISSING: 1] [?: 2]|
| | Land / Bank / Tax / LPG       | |    | [Save Profile & Recalculate] |
| +-------------------------------+ |    +------------------------------+
| Docs Summary: [HAVE][MISSING][?]  |
| [ Save Profile & Recalculate ]    |
+-----------------------------------+
```

### 8.4 AI Chat (`/chat`)
- Full-height responsive conversation card (`calc(100vh - 220px)` desktop, sticky composer above bottom nav on mobile).
- Header bar: Title, No-Aadhaar safety badge, and non-official AI grounding notice.
- **Suggested Prompt Chips**: Horizontal scroll strip (`Check My Summary`, `PM-KISAN documents?`, `Why am I Unknown?`).
- **Message Bubbles & States**:
  - User right-aligned (`#1E3A8A` / `#FFFFFF` light; `#818CF8` / `#0B1120` dark).
  - Assistant left-aligned (`bgSurfaceSubtle`) with `Speak` / `सुनें` TTS button & model/meta label.
  - **Typing Indicator**: Animated 3-dot subtle pulse bubble while `sending === true` (respects reduced motion).
  - **Designed Error States**:
    - `busy` (`429`): Amber warning card with clock icon + `t.aiErrors.busy`.
    - `network` (offline / `502`/`503`/`504`): Slate network-off card + `t.aiErrors.network`.
    - `generic` (`500`): Crimson soft card + `t.aiErrors.generic` + clear divider leading to offline deterministic rule summary.
    - `blockedBySafety`: Red shield-alert card (`Safety Policy`) highlighting human confirmation requirement.

```text
DESKTOP (/chat - 1280px)                 MOBILE (/chat - 360px)
+-----------------------------------+    +------------------------------+
| AI Chat | [Lock] No Aadhaar       |    | AI Chat | No Aadhaar Notice  |
| Chips: [Check My Summary] [Docs?] |    | [Check Summary] [PM-KISAN?]  |
+-----------------------------------+    +------------------------------+
| [Bot] Namaste! Ask about schemes. |    | [Bot] Welcome message...     |
|                     [User] Quote? |    |               [User] Submit? |
| [!] Safety Policy: Cannot auto-   |    | [!] Safety Policy Alert      |
|     submit...           [Speak]   |    | [Amber Busy Banner (429)]    |
| [Amber] AI is busy right now...   |    | [...] Typing indicator       |
+-----------------------------------+    +------------------------------+
| [Ask a scheme question...] [Mic]  |    | [Ask question...] [Mic][Send]|
|                          [Send]   |    +------------------------------+
+-----------------------------------+    | [Home][Chat][Apply][Alerts]  |
```

### 8.5 Apply & Human Confirmation Gate (`/apply`)
- **Visual Application Stepper**: `1. Draft Details` -> `2. Human Confirmation Gate` -> `3. Copy CSC Handoff Pack` -> `4. Reviewer Status`.
- **Prominent Human Confirmation Gate Modal (`role="dialog"`)**:
  - Saffron warning header shield (`t.confirmationGate.title`), subtitle (`t.confirmationGate.subtitle`).
  - Read-only grey card displaying Applicant, State/District, Bank IFSC summary, Checklist counts, and monospace `Snapshot SHA-256`.
  - **3 Large-Target Checkboxes (`min-h-[48px]` clickable label rows)** bound to `c1`, `c2`, `c3`.
  - Submit button (`Confirm & Unlock Guided Handoff`) visually **muted/disabled (`disabled={!allChecked || submitting}`)** with clear helper text (`"Check all 3 declarations above to unlock"`) until all 3 boxes are checked.
- **Tracked Applications Timeline**: Cards with status pills (`PENDING_CONFIRMATION`, `CONFIRMED`, `APPROVED`, `REJECTED`), reviewer notes, and one-click `Copy Guided Handoff Pack` toast feedback.

```text
DESKTOP (/apply - 1280px)                CONFIRMATION GATE MODAL
+-----------------------------------+    +------------------------------+
| Stepper: (1)Draft -> (2)Gate      |    | [Shield] Human Confirmation  |
|          -> (3)Handoff -> (4)Done |    | AI never auto-submits...     |
| [Select Scheme] [Applicant Name]  |    | +--------------------------+ |
| [State/Dist]    [IFSC Summary]    |    | | Scheme | Applicant | SHA | |
| [Optional Notes (No Aadhaar)]     |    | +--------------------------+ |
| [Create Draft & Open Gate]        |    | [ ] 1. Reviewed details...   |
| --------------------------------- |    | [ ] 2. Official portal...    |
| Tracked Applications & Handoffs:  |    | [ ] 3. Human applicant...    |
| [PM-KISAN] [ELIGIBLE] [CONFIRMED] |    |                              |
| [Copy Guided Handoff Pack]        |    |   [Cancel] [Confirm & Unlock]|
+-----------------------------------+    +------------------------------+
```

### 8.6 Alerts & Collaborative Tasks (`/alerts`)
- Header with unread count chip + status filter chips (`All`, `Open`, `Completed`).
- Structured task cards showing scheme chip, assignee role badge (`Citizen`, `CSC Operator`, etc.), `NEW` saffron pill (`onSecondary` text), due date, assignee name, and single-click `Mark Completed` primary button.
- Clean `EmptyState` card when zero tasks match.

### 8.7 Staff Workbench (`/operator` — CSC Operator / Reviewer / Supervisor)
- **Citizen RBAC Guard**: Clean centered access-notice card (`RBAC Protected: Sign in as CSC Operator, Reviewer, or Supervisor...`) with direct link to `/login`.
- **Staff Layout**:
  - **KPI Summary Grid (3–4 cards)**: `Active Users`, `Total Applications`, `Awaiting Review`, `Approved`.
  - **Applications Queue**: Clean cards/rows separating `Human Confirmed: Yes/No`, application status chip, and (only for `REVIEWER` / `SUPERVISOR` on `humanConfirmed` apps) inline verification note input + `Approve` (primary/success) and `Reject` (danger outline) buttons. `CSC_OPERATOR` explicitly sees zero `Approve` buttons.
  - **Security Audit Trail (PII Redacted)** (`REVIEWER` / `SUPERVISOR`): Clean tabular layout with horizontal scroll wrapper, sticky subtle header, monospace timestamps, role badges, and PII-redacted details.

### 8.8 Settings (`/settings`)
- Clean grouped settings list (`max-w-2xl`):
  1. **Preferences Group**: Bilingual switcher (`हिन्दी` / `English`), Theme Mode (`Light` / `Dark`), Large Text Accessibility Mode (`Standard 16px` / `Large 20px`).
  2. **DPDP Privacy & Data Controls** (when signed in): Dedicated privacy card highlighting zero-Aadhaar policy, `Export My Data (DPDP JSON)` download button, and destructive `Erase Personal Profile & Checklists` action (with confirmation prompt/toast).

---

## 9. Accessibility, Responsive & Performance Plan

### 9.1 Keeping Axe at 0 Violations & Existing Playwright E2E Passing
- **Color & Contrast**: Every foreground/background token pair verified `>= 4.5:1` (`>= 3.0:1` for borders/large headings). Hardcoded `text-white` on `var(--saathi-secondary)` badges in `AppShell.tsx` and `alerts/page.tsx` will be migrated to `var(--saathi-on-secondary)` so Dark Mode also passes `color-contrast` with zero violations.
- **Form Labels & Accessible Names**: Preserve `htmlFor`/`id` bindings (`#otp-identifier`, `#otp-code-input`, `#register-name`, `#auth-identifier`, `#auth-password`, `#dob-input`), `<span className="sr-only"> Toggle language</span>` on the language button (preventing `label-content-name-mismatch`), and explicit `aria-label` on icon-only controls (Theme, Voice Mic, Sidebar Toggle).
- **Keyboard & Focus Order**: Semantic landmarks (`<header>`, `<nav>`, `<main id="main-content">`, `<footer>`), skip link preserved, visible `3px` saffron focus outline on every control, focus trapping + `Esc` dismissal in `HumanConfirmationGateModal` and mobile Drawer.
- **No Selector Breakage**: Keep every `data-testid`, input `#id`, heading phrase, and button label audited in Section 1.2 intact.

### 9.2 Responsive Breakpoints
- **`360px` (Mobile Portrait)**: Single-column layout, 56px top bar + mobile nav drawer, 64px fixed 5-tab bottom bar (`44px+` touch targets), stacked cards (`16px` outer gutters), horizontal scroll containers around wide tables (`overflow-x-auto`).
- **`768px` (Tablet)**: Top bar + collapsed `72px` icon sidebar rail (expandable), 2-column form and role-card grids, comfortable `24px` gutters.
- **`1280px+` (Desktop)**: `240px` expanded collapsible sidebar + centered `max-w-6xl` content container, 4-column role grid / 3-4 card KPI bar, full-width audit table without horizontal clipping.

### 9.3 Performance Plan (Lighthouse `>= 90`)
- **`next/font/google` Self-Hosting**: Zero layout shift (`display: 'swap'`), automatic font subsetting (`latin`, `devanagari`).
- **Zero Heavy Animation/UI Runtime Libraries**: Pure Tailwind CSS v4 + CSS custom properties + tree-shaken `lucide-react` icons (no heavyweight runtime CSS-in-JS or chart bundles).
- **Minimal Client Re-renders**: Preserve `SaathiProvider` memoization and split large pages (`/operator`, `HumanConfirmationGateModal`) cleanly so non-staff routes never ship unnecessary audit-table code on first paint.
- **Offline PWA Assets**: Keep `manifest.webmanifest`, `sw.js`, and lightweight SVG icon (`icon.svg`) lean with explicit image/SVG dimensions (`CLS = 0`).

---

## 10. Proposed Token Additions & Updates (`tokens.json` & `tokens.css`)

*(Reference table only — no token files modified in this step.)*

### 10.1 Color Tokens

| CSS Custom Property (`tokens.css`) | JSON Path (`tokens.json`) | Proposed Light Value | Proposed Dark Value | Notes |
|---|---|---|---|---|
| `--saathi-primary` | `colors.{mode}.primary` | `#1e3a8a` | `#818cf8` | Civic Navy / Indigo primary |
| `--saathi-on-primary` | `colors.{mode}.onPrimary` | `#ffffff` | `#0b1120` | Contrast on primary |
| `--saathi-primary-hover` | `colors.{mode}.primaryHover` | `#1e40af` | `#a5b4fc` | New hover token |
| `--saathi-primary-container` | `colors.{mode}.primaryContainer` | `#e0e7ff` | `#1e293b` | Active nav & highlight surface |
| `--saathi-on-primary-container` | `colors.{mode}.onPrimaryContainer` | `#1e1b4b` | `#e0e7ff` | Text on primary container |
| `--saathi-secondary` | `colors.{mode}.secondary` | `#b45309` | `#f59e0b` | Warm saffron accent |
| `--saathi-on-secondary` | `colors.{mode}.onSecondary` | `#ffffff` | `#0b1120` | Text on solid saffron |
| `--saathi-secondary-hover` | `colors.{mode}.secondaryHover` | `#92400e` | `#fbbf24` | New saffron hover token |
| `--saathi-secondary-container` | `colors.{mode}.secondaryContainer` | `#fef3c7` | `#451a03` | Disclaimer & warning tint |
| `--saathi-on-secondary-container` | `colors.{mode}.onSecondaryContainer` | `#78350f` | `#fde68a` | Text on disclaimer/warning |
| `--saathi-background` | `colors.{mode}.background` | `#f8fafc` | `#0b1120` | Canvas background |
| `--saathi-surface` | `colors.{mode}.surface` | `#ffffff` | `#111827` | Elevated card/shell |
| `--saathi-surface-variant` | `colors.{mode}.surfaceVariant` | `#f1f5f9` | `#1e293b` | Subtle surface / bubbles |
| `--saathi-surface-hover` | `colors.{mode}.surfaceHover` | `#e2e8f0` | `#334155` | New interactive hover surface |
| `--saathi-on-surface` | `colors.{mode}.onSurface` | `#0f172a` | `#f8fafc` | Primary body text |
| `--saathi-on-surface-muted` | `colors.{mode}.onSurfaceMuted` | `#475569` | `#cbd5e1` | Secondary/muted copy |
| `--saathi-border-subtle` | `colors.{mode}.borderSubtle` | `#cbd5e1` | `#334155` | New structural hairline border |
| `--saathi-outline` | `colors.{mode}.outline` | `#64748b` | `#64748b` | Form & interactive outlines |
| `--saathi-eligible-bg` | `colors.{mode}.eligibleBg` | `#ecfdf5` | `#064e3b` | Success / Eligible fill |
| `--saathi-eligible-fg` | `colors.{mode}.eligibleFg` | `#065f46` | `#6ee7b7` | Success / Eligible text |
| `--saathi-unknown-bg` | `colors.{mode}.unknownBg` | `#fffbeb` | `#451a03` | Warning / Unknown fill |
| `--saathi-unknown-fg` | `colors.{mode}.unknownFg` | `#92400e` | `#fcd34d` | Warning / Unknown text |
| `--saathi-not-eligible-bg` | `colors.{mode}.notEligibleBg` | `#fef2f2` | `#450a0a` | Error / Not Eligible fill |
| `--saathi-not-eligible-fg` | `colors.{mode}.notEligibleFg` | `#b91c1c` | `#fca5a5` | Error / Not Eligible text |
| `--saathi-error` | `colors.{mode}.error` | `#b91c1c` | `#fca5a5` | Form/API alert error color |

### 10.2 Typography, Spacing, Elevation & Motion Tokens

| CSS Custom Property (`tokens.css`) | JSON Path (`tokens.json`) | Proposed Value |
|---|---|---|
| `--saathi-font-size` | `typography.baseFontSizePx` | `16px` (`20px` inside `.large-text`) |
| `--saathi-leading-body` | `typography.lineHeightBody` | `1.55` (`1.70` when `html[lang="hi"]`) |
| `--saathi-leading-heading` | `typography.lineHeightHeading` | `1.25` (`1.38` when `html[lang="hi"]`) |
| `--saathi-radius-sm` / `md` / `lg` / `xl` | `radii.{sm,md,lg,xl}` | `8px` / `12px` / `16px` / `20px` |
| `--saathi-shadow-sm` / `md` / `lg` | `elevation.{sm,md,lg}` | Subtle slate shadows matching Section 5.3 |
| `--saathi-motion-fast` / `normal` | `motion.{fast,normal}` | `150ms cubic-bezier(0.16, 1, 0.3, 1)` / `220ms cubic-bezier(0.16, 1, 0.3, 1)` |
| `--saathi-touch-target-min` | `spacing.minTouchTargetPx` | `44` (`48` for primary citizen CTAs) |

---

## 11. Implementation Plan (Ordered Steps)

| Step | Scope & Goal | Files Touched | Main Risks & Mitigations |
|---|---|---|---|
| **1. Design Tokens & Font Foundation** | Update palette, elevation, motion, and Devanagari line-height tokens; wire `Plus Jakarta Sans`, `Noto Sans Devanagari`, `JetBrains Mono` via `next/font/google`; add reduced-motion CSS. | `shared/design-tokens/tokens.json`, `shared/design-tokens/tokens.css`, `web/src/app/layout.tsx`, `web/src/app/globals.css` | Risk: Android tests/shared references reading `tokens.json` -> Mitigation: keep all existing `colors.light.*` / `colors.dark.*` keys backward-compatible while adding new keys. |
| **2. Core UI Primitives Library** | Build reusable accessible primitives (`Button`, `Input`, `Select`, `Checkbox`, `OTPInput`, `Card`, `Badge`, `Tabs`, `Stepper`, `Modal`, `Drawer`, `Toast`, `Table`, `Skeleton`, `EmptyState`, `ErrorState`, `Avatar`, `Tooltip`). | `web/src/components/ui/*` (new folder), `web/src/components/StatusBadges.tsx` | Risk: Over-abstraction breaks E2E input selectors -> Mitigation: forward `id`, `name`, `data-testid`, and native `<input>`/`<select>` props directly. |
| **3. App Shell & Navigation Redesign** | Upgrade `AppShell.tsx` with collapsible desktop sidebar, top bar with theme + language toggle, mobile drawer + 5-tab bar (`>=44px` targets), and role-aware staff link grouping. | `web/src/components/AppShell.tsx`, `web/src/context/SaathiProvider.tsx` (optional sidebar/toast helpers) | Risk: Breaking `Toggle language` button or mobile overflow -> Mitigation: retain `<span className="sr-only"> Toggle language</span>` and `data-testid="active-role-badge"`. |
| **4. Auth & Onboarding Pages (`/`, `/login`)** | Redesign Landing hero, trust strip, 3-step explainer, role cards, scheme explorer cards, FAQ, footer; redesign `/login` with segmented tabs and 6-box synced OTP UX. | `web/src/app/page.tsx`, `web/src/app/login/page.tsx` | Risk: Changing button text breaks `saathi.spec.ts` -> Mitigation: verify exact E2E button copy & `#otp-*`/`#auth-*` IDs after edits. |
| **5. Core Citizen Flows (`/profile`, `/chat`, `/apply`)** | Redesign `/profile` stepped/grouped layout & DOB widget; `/chat` typing indicator, prompt chips & designed busy/network/generic error cards; `/apply` & `HumanConfirmationGateModal` visual stepper and prominent declarations. | `web/src/app/profile/page.tsx`, `web/src/app/chat/page.tsx`, `web/src/app/apply/page.tsx`, `web/src/components/HumanConfirmationGateModal.tsx` | Risk: Modal keyboard trap / contrast regressions -> Mitigation: implement `Esc` + focus return + dark-mode `--saathi-on-secondary` check. |
| **6. Staff & Utility Screens (`/alerts`, `/operator`, `/settings`)** | Polish task cards & EmptyState on `/alerts`, add KPI cards + dense audit table on `/operator`, group preferences & DPDP erasure confirmation cleanly on `/settings`. | `web/src/app/alerts/page.tsx`, `web/src/app/operator/page.tsx`, `web/src/app/settings/page.tsx` | Risk: Hiding `Approve` / `Status: APPROVED` strings -> Mitigation: preserve exact E2E test labels. |
| **7. Verification Pass** | Run `npm run lint`, `npm run build`, and `npm run test:e2e` (Playwright + `@axe-core/playwright` WCAG AA across light + dark modes, `360`/`768`/`1280` viewports) and Lighthouse audit. | `web/tests/e2e/saathi.spec.ts` (add dark-mode & mobile viewport checks only if desired) | Risk: Visual regression or contrast dip -> Mitigation: test both `hi` and `en` on all 8 routes cleanly before completion. |

---

## 12. Honest Assumptions & Open Questions

### Assumptions Made in This Spec
1. Updating `shared/design-tokens/tokens.{json,css}` to the new Navy/Indigo + Warm Saffron palette is acceptable across the web PWA; the native Android app (`/app`) keeps its own Material 3 `Color.kt` theme unless you ask to align Android colors afterward.
2. Keeping all profile sections mounted in the DOM (styled visually with step headers/jump navigation or client tabs) is preferred over unmounting fields between steps so single-click `Save Profile & Recalculate` works identically to today.
3. Google Fonts (`Plus Jakarta Sans`, `Noto Sans Devanagari`, `JetBrains Mono`) can be fetched at Next.js build time via `next/font/google`.
4. Local `OTP` demo hints (`devOtpHint`) remain visible in development/test environments so judges and Playwright tests can sign in without a paid SMS gateway.

### Open Questions for You Before `"proceed"`
1. **Color Alignment Scope**: Should `shared/design-tokens/tokens.{json,css}` switch to the new Navy/Indigo + Warm Saffron palette (affecting Web first), or do you want Android `Color.kt` updated later to match?
2. **Dark Mode Default**: Should Light Mode stay the default for first-time visitors (matching today), or follow OS `prefers-color-scheme`?
3. **Destructive DPDP Erasure UX**: On `/settings`, should clicking `"Erase Personal Profile & Checklists"` open a confirmation dialog before calling `deleteMyProfile()`, or trigger immediately as it does today?
4. **Google Fonts Offline CI Check**: Does your build environment always allow `next/font/google` downloads during `next build`, or should we keep system-font fallback safety in `globals.css` if offline builds ever fail?

