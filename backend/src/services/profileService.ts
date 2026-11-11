import type { DatabaseClient } from '../db/database.js';
import { decryptPii, encryptPii } from '../security/crypto.js';
import type { CitizenProfile, LanguageCode } from '../types/models.js';

interface ProfileRow {
  id: string;
  user_id: string;
  display_name: string;
  encrypted_phone: string | null;
  encrypted_dob: string | null;
  birth_year: number;
  age_confirmed_via_voice: number;
  gender: string;
  state: string;
  district: string;
  urban_or_rural: string;
  annual_income: number;
  bpl_status: string;
  social_category: string;
  disability_status: string;
  owns_agricultural_land: string;
  has_bank_account: string;
  is_income_tax_payer: string;
  has_lpg_connection: string;
  occupation: string;
  preferred_language: string;
  updated_at: string;
}

export function rowToCitizenProfile(row: ProfileRow): CitizenProfile {
  return {
    id: row.id,
    userId: row.user_id,
    displayName: row.display_name,
    phoneNumber: row.encrypted_phone ? decryptPii(row.encrypted_phone, row.user_id) : '',
    dateOfBirth: row.encrypted_dob ? decryptPii(row.encrypted_dob, row.user_id) : '',
    birthYear: row.birth_year,
    ageConfirmedViaVoice: Boolean(row.age_confirmed_via_voice),
    gender: row.gender,
    state: row.state,
    district: row.district,
    urbanOrRural: row.urban_or_rural,
    annualIncome: row.annual_income,
    bplStatus: row.bpl_status,
    socialCategory: row.social_category,
    disabilityStatus: row.disability_status,
    ownsAgriculturalLand: row.owns_agricultural_land,
    hasBankAccount: row.has_bank_account,
    isIncomeTaxPayer: row.is_income_tax_payer,
    hasLpgConnection: row.has_lpg_connection,
    occupation: row.occupation,
    preferredLanguage: (row.preferred_language === 'en' ? 'en' : 'hi') as LanguageCode,
    updatedAt: row.updated_at
  };
}

export async function getOrCreateProfile(
  db: DatabaseClient,
  userId: string,
  fallbackDisplayName = 'Citizen'
): Promise<CitizenProfile> {
  const existing = await db.get<ProfileRow>('SELECT * FROM citizen_profiles WHERE user_id = ?', [
    userId
  ]);
  if (existing) {
    return rowToCitizenProfile(existing);
  }

  const now = new Date().toISOString();
  const id = `prof_${userId}`;
  await db.run(
    `INSERT INTO citizen_profiles (
      id, user_id, display_name, encrypted_phone, encrypted_dob, birth_year, age_confirmed_via_voice,
      gender, state, district, urban_or_rural, annual_income, bpl_status, social_category,
      disability_status, owns_agricultural_land, has_bank_account, is_income_tax_payer,
      has_lpg_connection, occupation, preferred_language, updated_at
    ) VALUES (?, ?, ?, ?, ?, -1, 1, 'unknown', 'Uttar Pradesh', '', 'unknown', -1, 'unknown', 'unknown', 'unknown', 'unknown', 'unknown', 'unknown', 'unknown', 'unknown', 'hi', ?)`,
    [id, userId, fallbackDisplayName, encryptPii('', userId), encryptPii('', userId), now]
  );

  const created = await db.get<ProfileRow>('SELECT * FROM citizen_profiles WHERE user_id = ?', [
    userId
  ]);
  return rowToCitizenProfile(created!);
}
