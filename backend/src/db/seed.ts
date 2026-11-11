import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createDatabase, type DatabaseClient } from './database.js';
import { runMigrations } from './migrate.js';
import { DEMO_ACCOUNTS, SEEDED_SCHEMES } from './seedData.js';
import { computeSnapshotHash, encryptPii, hashPassword } from '../security/crypto.js';

const __filename = fileURLToPath(import.meta.url);

export async function seedDatabase(db: DatabaseClient): Promise<void> {
  const now = new Date().toISOString();

  for (const scheme of SEEDED_SCHEMES) {
    await db.run(
      `INSERT INTO schemes (id, code, name_en, name_hi, category, rule_version, payload_json, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT (id) DO UPDATE SET
         code = excluded.code,
         name_en = excluded.name_en,
         name_hi = excluded.name_hi,
         category = excluded.category,
         rule_version = excluded.rule_version,
         payload_json = excluded.payload_json,
         updated_at = excluded.updated_at`,
      [
        scheme.id,
        scheme.code,
        scheme.nameEn,
        scheme.nameHi,
        scheme.category,
        scheme.ruleVersion,
        JSON.stringify(scheme),
        now
      ]
    );
  }

  const defaultPassHash = hashPassword('SaathiDemo@2026');
  for (const acct of DEMO_ACCOUNTS) {
    await db.run(
      `INSERT INTO users (id, email, phone, display_name, password_hash, role, preferred_language, is_active, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
       ON CONFLICT (id) DO NOTHING`,
      [
        acct.id,
        acct.email,
        acct.phone,
        acct.displayName,
        defaultPassHash,
        acct.role,
        acct.preferredLanguage,
        now,
        now
      ]
    );
  }

  // Seed default citizen profile for Ramesh Kumar (15/06/1964, UP farmer, BPL)
  const encDob = encryptPii('15/06/1964', 'usr_citizen_ramesh');
  const encPhone = encryptPii('+919800000001', 'usr_citizen_ramesh');
  await db.run(
    `INSERT INTO citizen_profiles (
      id, user_id, display_name, encrypted_phone, encrypted_dob, birth_year, age_confirmed_via_voice,
      gender, state, district, urban_or_rural, annual_income, bpl_status, social_category,
      disability_status, owns_agricultural_land, has_bank_account, is_income_tax_payer,
      has_lpg_connection, occupation, preferred_language, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    ON CONFLICT (user_id) DO NOTHING`,
    [
      'prof_ramesh',
      'usr_citizen_ramesh',
      'Ramesh Kumar',
      encPhone,
      encDob,
      1964,
      'male',
      'Uttar Pradesh',
      'Varanasi',
      'rural',
      45000,
      'yes',
      'obc',
      'no',
      'yes',
      'yes',
      'no',
      'yes',
      'Farmer',
      'hi',
      now
    ]
  );

  // Seed initial document checklists for PM-KISAN & NSAP-IGNOAPS
  await db.run(
    `INSERT INTO document_checklists (id, user_id, scheme_id, document_id, status, updated_at)
     VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, scheme_id, document_id) DO NOTHING`,
    ['chk_1', 'usr_citizen_ramesh', 'PM-KISAN', 'doc_land_record', 'HAVE', now]
  );
  await db.run(
    `INSERT INTO document_checklists (id, user_id, scheme_id, document_id, status, updated_at)
     VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, scheme_id, document_id) DO NOTHING`,
    ['chk_2', 'usr_citizen_ramesh', 'PM-KISAN', 'doc_bank_passbook', 'HAVE', now]
  );
  await db.run(
    `INSERT INTO document_checklists (id, user_id, scheme_id, document_id, status, updated_at)
     VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (user_id, scheme_id, document_id) DO NOTHING`,
    ['chk_3', 'usr_citizen_ramesh', 'PM-KISAN', 'doc_identity_card', 'MISSING', now]
  );

  // Seed sample application & collaborative task
  const snapshotHash = computeSnapshotHash({
    schemeId: 'PM-KISAN',
    ruleVersion: 'v1.2.0-illustrative',
    applicantName: 'Ramesh Kumar',
    stateDistrict: 'Uttar Pradesh / Varanasi',
    bankIfscSummary: 'SBIN0001234',
    checklistSummary: '2 Have, 1 Missing'
  });

  await db.run(
    `INSERT INTO applications (
      id, user_id, scheme_id, scheme_name_en, status, eligibility_outcome,
      human_confirmed, human_confirmed_timestamp, applicant_snapshot_hash,
      applicant_name, state_district, bank_ifsc_summary, checklist_summary, notes,
      handoff_pack_json, reviewer_id, review_note, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?)
    ON CONFLICT (id) DO NOTHING`,
    [
      'app_demo_pmkisan',
      'usr_citizen_ramesh',
      'PM-KISAN',
      'PM-KISAN (Pradhan Mantri Kisan Samman Nidhi)',
      'UNDER_REVIEW',
      'ELIGIBLE',
      now,
      snapshotHash,
      'Ramesh Kumar',
      'Uttar Pradesh / Varanasi',
      'SBIN0001234',
      '2 Have, 1 Missing',
      'Citizen verified land Khatauni details with CSC operator.',
      JSON.stringify({
        schemeId: 'PM-KISAN',
        applicantName: 'Ramesh Kumar',
        stateDistrict: 'Uttar Pradesh / Varanasi',
        bankIfscSummary: 'SBIN0001234',
        checklistSummary: '2 Have, 1 Missing',
        generatedAt: now
      }),
      now,
      now
    ]
  );

  await db.run(
    `INSERT INTO tasks (
      id, user_id, application_id, scheme_id, scheme_name,
      title_en, title_hi, description_en, description_hi,
      status, assigned_to_role, assigned_operator_name, due_date_text,
      is_notification_unread, created_at, updated_at
    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)
    ON CONFLICT (id) DO NOTHING`,
    [
      'tsk_demo_voter_id',
      'usr_citizen_ramesh',
      'app_demo_pmkisan',
      'PM-KISAN',
      'PM-KISAN',
      'Carry Voter ID / Ration Card to CSC Center',
      'CSC केंद्र पर मतदाता पहचान पत्र या राशन कार्ड साथ लाएं',
      'Two documents are ready. Bring one valid government photo ID (no Aadhaar stored in app) to complete PM-KISAN DBT verification.',
      'दो दस्तावेज़ तैयार हैं। पीएम-किसान सत्यापन पूरा करने के लिए कृपया एक मान्य पहचान पत्र (ऐप में आधार संग्रहीत नहीं) लाएं।',
      'OPEN',
      'CSC_OPERATOR',
      'Anita Verma (CSC Operator)',
      'Within 3 days',
      now,
      now
    ]
  );
}

if (process.argv[1] && path.resolve(process.argv[1]) === path.resolve(__filename)) {
  const db = createDatabase();
  runMigrations(db)
    .then(() => seedDatabase(db))
    .then(async () => {
      console.info('[seed] database seeded successfully');
      await db.close();
    })
    .catch(async (err) => {
      console.error('[seed] failed:', err);
      await db.close();
      process.exit(1);
    });
}
