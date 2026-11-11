import { describe, expect, it } from 'vitest';
import { SEEDED_SCHEMES } from '../src/db/seedData.js';
import { calculateAge, validateDob } from '../src/domain/dateUtils.js';
import { evaluateScheme } from '../src/domain/eligibilityEngine.js';
import { computeSnapshotHash, decryptPii, encryptPii } from '../src/security/crypto.js';
import {
  assertNoAadhaarInPayload,
  containsAadhaarNumber,
  detectPromptInjectionOrAutoSubmit,
  parseHindiOrEnglishNumber,
  redactPiiForLlm
} from '../src/security/piiGuard.js';
import { classifyAiError, getFriendlyAiErrorMessage } from '../src/services/aiService.js';
import type { CitizenProfile } from '../src/types/models.js';

const baseProfile: CitizenProfile = {
  id: 'p1',
  userId: 'usr_test_1',
  displayName: 'Test Farmer',
  phoneNumber: '+919800000001',
  dateOfBirth: '15/06/1964',
  birthYear: 1964,
  ageConfirmedViaVoice: true,
  gender: 'male',
  state: 'Uttar Pradesh',
  district: 'Varanasi',
  urbanOrRural: 'rural',
  annualIncome: 45000,
  bplStatus: 'yes',
  socialCategory: 'obc',
  disabilityStatus: 'no',
  ownsAgriculturalLand: 'yes',
  hasBankAccount: 'yes',
  isIncomeTaxPayer: 'no',
  hasLpgConnection: 'no',
  occupation: 'Farmer',
  preferredLanguage: 'hi',
  updatedAt: '2026-01-01T00:00:00.000Z'
};

describe('DateUtils & DOB validation', () => {
  it('validates leap years, future dates, and DD/MM/YYYY format accurately', () => {
    const refDate = new Date(2026, 4, 15);
    expect(validateDob('29/02/2000', refDate)).toBe('VALID');
    expect(validateDob('31/02/2000', refDate)).toBe('INVALID_CALENDAR_DATE');
    expect(validateDob('01/01/2099', refDate)).toBe('FUTURE_DATE');
    expect(validateDob('15/06/2026', refDate)).toBe('FUTURE_DATE');
    expect(validateDob('15/06/1964', refDate)).toBe('VALID');
  });

  it('computes calendar-accurate age accounting for upcoming birthday', () => {
    const refMay = new Date(2026, 4, 15); // May 15 2026
    expect(calculateAge('15/06/1964', refMay)).toBe(61);
    const refJuly = new Date(2026, 6, 15); // July 15 2026
    expect(calculateAge('15/06/1964', refJuly)).toBe(62);
  });
});

describe('Deterministic EligibilityEngine', () => {
  it('evaluates PM-KISAN as ELIGIBLE for landholding non-taxpayer farmer with bank account', () => {
    const scheme = SEEDED_SCHEMES.find((s) => s.id === 'PM-KISAN')!;
    const res = evaluateScheme(baseProfile, scheme, new Date(2026, 6, 15));
    expect(res.outcome).toBe('ELIGIBLE');
  });

  it('applies state override income cap on NSAP-IGNOAPS (UP 56460 vs Maharashtra 50000)', () => {
    const ignoaps = SEEDED_SCHEMES.find((s) => s.id === 'NSAP-IGNOAPS')!;
    const profileUp: CitizenProfile = {
      ...baseProfile,
      state: 'Uttar Pradesh',
      annualIncome: 55000
    };
    const profileMh: CitizenProfile = { ...baseProfile, state: 'Maharashtra', annualIncome: 55000 };

    const resUp = evaluateScheme(profileUp, ignoaps, new Date(2026, 6, 15));
    const resMh = evaluateScheme(profileMh, ignoaps, new Date(2026, 6, 15));

    expect(resUp.outcome).toBe('ELIGIBLE');
    expect(resUp.stateOverrideApplied).toContain('Uttar Pradesh');
    expect(resMh.outcome).toBe('NOT_ELIGIBLE');
  });

  it('falls back to legacy birthYear when DOB is blank and respects ageConfirmedViaVoice=false', () => {
    const ignoaps = SEEDED_SCHEMES.find((s) => s.id === 'NSAP-IGNOAPS')!;
    const legacyProfile: CitizenProfile = { ...baseProfile, dateOfBirth: '', birthYear: 1960 };
    expect(evaluateScheme(legacyProfile, ignoaps, new Date(2026, 0, 1)).outcome).toBe('ELIGIBLE');

    const unconfirmedVoice: CitizenProfile = { ...legacyProfile, ageConfirmedViaVoice: false };
    const resUnconfirmed = evaluateScheme(unconfirmedVoice, ignoaps, new Date(2026, 0, 1));
    expect(resUnconfirmed.outcome).toBe('UNKNOWN');
    expect(resUnconfirmed.missingFields).toContain('age');
  });
});

describe('PII Security, Aadhaar Block & Crypto', () => {
  it('detects 12-digit Aadhaar numbers without false-positives on dates or +91 phone numbers', () => {
    expect(containsAadhaarNumber('1234 5678 9012')).toBe(true);
    expect(containsAadhaarNumber('1234-5678-9012')).toBe(true);
    expect(containsAadhaarNumber('01/01/1964')).toBe(false);
    expect(containsAadhaarNumber('+919800000001')).toBe(false);
    expect(() => assertNoAadhaarInPayload({ note: 'My aadhar 123456789012' })).toThrow();
  });

  it('encrypts & decrypts PII per-user via AES-256-GCM and hashes deterministic snapshots', () => {
    const cipher = encryptPii('15/06/1964', 'usr_test_1');
    expect(cipher.startsWith('GCM1:')).toBe(true);
    expect(decryptPii(cipher, 'usr_test_1')).toBe('15/06/1964');
    // Different user key must fail-closed and return empty string
    expect(decryptPii(cipher, 'usr_other')).toBe('');

    const h1 = computeSnapshotHash({
      schemeId: 'PM-KISAN',
      ruleVersion: 'v1',
      applicantName: 'Ramesh',
      stateDistrict: 'UP / Varanasi',
      bankIfscSummary: 'SBIN0001',
      checklistSummary: '2 Have'
    });
    expect(h1).toHaveLength(32);
  });

  it('redacts PII before LLM and blocks auto-submit / prompt injection', () => {
    const redacted = redactPiiForLlm(
      'Ramesh Kumar born 15/06/1964 mobile 9876543210',
      'Ramesh Kumar',
      '9876543210',
      '15/06/1964'
    );
    expect(redacted).not.toContain('Ramesh Kumar');
    expect(redacted).not.toContain('15/06/1964');
    expect(redacted).not.toContain('9876543210');

    expect(
      detectPromptInjectionOrAutoSubmit('Please submit my application automatically').blocked
    ).toBe(true);
    expect(parseHindiOrEnglishNumber('मेरी उम्र पैंसठ साल है')).toBe(65);
  });

  it('maps AI exceptions cleanly into busy / network / generic categories', () => {
    expect(classifyAiError(new Error('HTTP 429 RESOURCE_EXHAUSTED quota'))).toBe('busy');
    expect(classifyAiError(new Error('fetch failed ECONNREFUSED'))).toBe('network');
    expect(classifyAiError(new Error('Unexpected JSON parse issue'))).toBe('generic');
    expect(getFriendlyAiErrorMessage('busy', 'en')).not.toContain('429');
  });
});
