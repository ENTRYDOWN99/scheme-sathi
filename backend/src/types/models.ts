export type Role = 'CITIZEN' | 'CSC_OPERATOR' | 'SUPERVISOR' | 'REVIEWER';

export type LanguageCode = 'en' | 'hi';

export type EligibilityOutcome = 'ELIGIBLE' | 'NOT_ELIGIBLE' | 'UNKNOWN';

export type DocumentStatus = 'HAVE' | 'MISSING' | 'UNKNOWN';

export type ApplicationStatus =
  | 'DRAFT'
  | 'AWAITING_HUMAN_CONFIRMATION'
  | 'READY_FOR_HANDOFF'
  | 'SUBMITTED'
  | 'UNDER_REVIEW'
  | 'APPROVED'
  | 'REJECTED';

export type TaskStatus = 'OPEN' | 'IN_PROGRESS' | 'COMPLETED';

export type RuleOperator =
  | 'EQ'
  | 'NEQ'
  | 'LT'
  | 'LTE'
  | 'GT'
  | 'GTE'
  | 'BETWEEN'
  | 'IN'
  | 'NOT_IN'
  | 'EXISTS'
  | 'EXTERNAL_LOOKUP';

export type LogicalCombinator = 'ALL' | 'ANY';

export interface RuleCondition {
  id: string;
  field: string;
  operator: RuleOperator;
  targetValue?: string | number | string[];
  secondaryTargetValue?: string | number;
  descriptionEn: string;
  descriptionHi: string;
  externalGuidanceEn?: string;
  externalGuidanceHi?: string;
}

export interface RuleGroup {
  combinator: LogicalCombinator;
  conditions: RuleCondition[];
  nestedGroups?: RuleGroup[];
}

export interface DocumentRequirement {
  id: string;
  nameEn: string;
  nameHi: string;
  mandatory: boolean;
  alternativesEn: string;
  alternativesHi: string;
  howToObtainEn: string;
  howToObtainHi: string;
}

export interface GovernmentScheme {
  id: string;
  code: string;
  nameEn: string;
  nameHi: string;
  category: string;
  benefitSummaryEn: string;
  benefitSummaryHi: string;
  descriptionEn: string;
  descriptionHi: string;
  officialSourceTitle: string;
  officialSourceUrl: string;
  verificationDate: string;
  isThresholdVerifiedOfficial: boolean;
  isRuleStaleWarning: boolean;
  ruleVersion: string;
  rootRuleGroup: RuleGroup;
  stateOverrideIncomeCap: Record<string, number>;
  requiredDocuments: DocumentRequirement[];
  applicationInstructionsEn: string;
  applicationInstructionsHi: string;
  cscOfficeGuidanceEn: string;
  cscOfficeGuidanceHi: string;
}

export interface CitizenProfile {
  id: string;
  userId: string;
  displayName: string;
  phoneNumber?: string;
  dateOfBirth?: string; // DD/MM/YYYY decrypted when authorized
  birthYear: number;
  ageConfirmedViaVoice: boolean;
  gender: string;
  state: string;
  district: string;
  urbanOrRural: string;
  annualIncome: number;
  bplStatus: string;
  socialCategory: string;
  disabilityStatus: string;
  ownsAgriculturalLand: string;
  hasBankAccount: string;
  isIncomeTaxPayer: string;
  hasLpgConnection: string;
  occupation: string;
  preferredLanguage: LanguageCode;
  updatedAt: string;
}

export interface ConditionResult {
  conditionId: string;
  field: string;
  outcome: EligibilityOutcome;
  reasonEn: string;
  reasonHi: string;
}

export interface EligibilityEvaluationResult {
  schemeId: string;
  schemeCode: string;
  ruleVersion: string;
  outcome: EligibilityOutcome;
  conditionResults: ConditionResult[];
  missingFields: string[];
  externalLookupGuidanceEn: string[];
  externalLookupGuidanceHi: string[];
  stateOverrideApplied?: string | null;
  evaluatedAt: string;
}

export interface DocumentChecklistItem {
  id: string;
  userId: string;
  schemeId: string;
  documentId: string;
  status: DocumentStatus;
  updatedAt: string;
}

export interface SchemeApplication {
  id: string;
  userId: string;
  schemeId: string;
  schemeNameEn: string;
  status: ApplicationStatus;
  eligibilityOutcome: EligibilityOutcome;
  humanConfirmed: boolean;
  humanConfirmedTimestamp?: string | null;
  applicantSnapshotHash: string;
  applicantName: string;
  stateDistrict: string;
  bankIfscSummary: string;
  checklistSummary: string;
  notes: string;
  handoffPackJson?: string | null;
  reviewerId?: string | null;
  reviewNote?: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CollaborativeTask {
  id: string;
  userId: string;
  applicationId?: string | null;
  schemeId: string;
  schemeName: string;
  titleEn: string;
  titleHi: string;
  descriptionEn: string;
  descriptionHi: string;
  status: TaskStatus;
  assignedToRole: Role;
  assignedOperatorName: string;
  dueDateText: string;
  isNotificationUnread: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AuditLogEntry {
  id: string;
  actorId: string;
  actorRole: Role;
  action: string;
  entityType: string;
  entityId: string;
  details: string;
  createdAt: string;
}

export interface AuthUser {
  id: string;
  email: string;
  phone: string;
  displayName: string;
  role: Role;
  preferredLanguage: LanguageCode;
}
