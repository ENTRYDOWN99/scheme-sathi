CREATE TABLE IF NOT EXISTS schema_migrations (
  id TEXT PRIMARY KEY,
  applied_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  email TEXT UNIQUE,
  phone TEXT UNIQUE,
  display_name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  role TEXT NOT NULL,
  preferred_language TEXT NOT NULL DEFAULT 'hi',
  is_active INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS refresh_tokens (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  token_hash TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  revoked_at TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS otp_codes (
  id TEXT PRIMARY KEY,
  phone_or_email TEXT NOT NULL,
  code_hash TEXT NOT NULL,
  expires_at TEXT NOT NULL,
  consumed_at TEXT,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS citizen_profiles (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL UNIQUE,
  display_name TEXT NOT NULL,
  encrypted_phone TEXT,
  encrypted_dob TEXT,
  birth_year INTEGER NOT NULL DEFAULT -1,
  age_confirmed_via_voice INTEGER NOT NULL DEFAULT 1,
  gender TEXT NOT NULL DEFAULT 'unknown',
  state TEXT NOT NULL DEFAULT 'Uttar Pradesh',
  district TEXT NOT NULL DEFAULT '',
  urban_or_rural TEXT NOT NULL DEFAULT 'unknown',
  annual_income INTEGER NOT NULL DEFAULT -1,
  bpl_status TEXT NOT NULL DEFAULT 'unknown',
  social_category TEXT NOT NULL DEFAULT 'unknown',
  disability_status TEXT NOT NULL DEFAULT 'unknown',
  owns_agricultural_land TEXT NOT NULL DEFAULT 'unknown',
  has_bank_account TEXT NOT NULL DEFAULT 'unknown',
  is_income_tax_payer TEXT NOT NULL DEFAULT 'unknown',
  has_lpg_connection TEXT NOT NULL DEFAULT 'unknown',
  occupation TEXT NOT NULL DEFAULT 'unknown',
  preferred_language TEXT NOT NULL DEFAULT 'hi',
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS schemes (
  id TEXT PRIMARY KEY,
  code TEXT NOT NULL UNIQUE,
  name_en TEXT NOT NULL,
  name_hi TEXT NOT NULL,
  category TEXT NOT NULL,
  rule_version TEXT NOT NULL,
  payload_json TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS document_checklists (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  scheme_id TEXT NOT NULL,
  document_id TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'UNKNOWN',
  updated_at TEXT NOT NULL,
  UNIQUE(user_id, scheme_id, document_id)
);

CREATE TABLE IF NOT EXISTS applications (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  scheme_id TEXT NOT NULL,
  scheme_name_en TEXT NOT NULL,
  status TEXT NOT NULL,
  eligibility_outcome TEXT NOT NULL,
  human_confirmed INTEGER NOT NULL DEFAULT 0,
  human_confirmed_timestamp TEXT,
  applicant_snapshot_hash TEXT NOT NULL,
  applicant_name TEXT NOT NULL,
  state_district TEXT NOT NULL,
  bank_ifsc_summary TEXT NOT NULL,
  checklist_summary TEXT NOT NULL,
  notes TEXT NOT NULL DEFAULT '',
  handoff_pack_json TEXT,
  reviewer_id TEXT,
  review_note TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS tasks (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  application_id TEXT,
  scheme_id TEXT NOT NULL,
  scheme_name TEXT NOT NULL,
  title_en TEXT NOT NULL,
  title_hi TEXT NOT NULL,
  description_en TEXT NOT NULL,
  description_hi TEXT NOT NULL,
  status TEXT NOT NULL DEFAULT 'OPEN',
  assigned_to_role TEXT NOT NULL,
  assigned_operator_name TEXT NOT NULL DEFAULT '',
  due_date_text TEXT NOT NULL DEFAULT '',
  is_notification_unread INTEGER NOT NULL DEFAULT 1,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS audit_logs (
  id TEXT PRIMARY KEY,
  actor_id TEXT NOT NULL,
  actor_role TEXT NOT NULL,
  action TEXT NOT NULL,
  entity_type TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  details TEXT NOT NULL,
  created_at TEXT NOT NULL
);
