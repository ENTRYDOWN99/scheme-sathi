import type {
  CitizenProfile,
  ConditionResult,
  EligibilityEvaluationResult,
  EligibilityOutcome,
  GovernmentScheme,
  RuleCondition,
  RuleGroup
} from '../types/models.js';
import { calculateAge } from './dateUtils.js';

/**
 * Resolves effective age identically to CitizenProfile.resolveAge() on Android:
 * 1. Returns -1 immediately when ageConfirmedViaVoice is false.
 * 2. Computes calendar-accurate age from DD/MM/YYYY if valid.
 * 3. Falls back to legacy birthYear when DOB is absent or incomplete.
 */
export function resolveProfileAge(profile: CitizenProfile, refDate: Date = new Date()): number {
  if (!profile.ageConfirmedViaVoice) {
    return -1;
  }
  if (profile.dateOfBirth && profile.dateOfBirth.trim().length > 0) {
    const dobAge = calculateAge(profile.dateOfBirth, refDate);
    if (dobAge >= 0) {
      return dobAge;
    }
  }
  if (profile.birthYear > 0) {
    return Math.max(0, refDate.getFullYear() - profile.birthYear);
  }
  return -1;
}

function resolveProfileField(
  profile: CitizenProfile,
  field: string,
  refDate: Date
): string | number | null {
  switch (field) {
    case 'age': {
      const age = resolveProfileAge(profile, refDate);
      return age >= 0 ? age : null;
    }
    case 'birthYear':
      return profile.birthYear > 0 ? profile.birthYear : null;
    case 'gender':
      return profile.gender && profile.gender.toLowerCase() !== 'unknown' ? profile.gender : null;
    case 'state':
      return profile.state && profile.state.trim().length > 0 ? profile.state : null;
    case 'district':
      return profile.district && profile.district.trim().length > 0 ? profile.district : null;
    case 'urbanOrRural':
      return profile.urbanOrRural && profile.urbanOrRural.toLowerCase() !== 'unknown'
        ? profile.urbanOrRural
        : null;
    case 'annualIncome':
      return profile.annualIncome >= 0 ? profile.annualIncome : null;
    case 'bplStatus':
      return profile.bplStatus && profile.bplStatus.toLowerCase() !== 'unknown'
        ? profile.bplStatus
        : null;
    case 'socialCategory':
      return profile.socialCategory && profile.socialCategory.toLowerCase() !== 'unknown'
        ? profile.socialCategory
        : null;
    case 'disabilityStatus':
      return profile.disabilityStatus && profile.disabilityStatus.toLowerCase() !== 'unknown'
        ? profile.disabilityStatus
        : null;
    case 'ownsAgriculturalLand':
      return profile.ownsAgriculturalLand &&
        profile.ownsAgriculturalLand.toLowerCase() !== 'unknown'
        ? profile.ownsAgriculturalLand
        : null;
    case 'hasBankAccount':
      return profile.hasBankAccount && profile.hasBankAccount.toLowerCase() !== 'unknown'
        ? profile.hasBankAccount
        : null;
    case 'isIncomeTaxPayer':
      return profile.isIncomeTaxPayer && profile.isIncomeTaxPayer.toLowerCase() !== 'unknown'
        ? profile.isIncomeTaxPayer
        : null;
    case 'hasLpgConnection':
      return profile.hasLpgConnection && profile.hasLpgConnection.toLowerCase() !== 'unknown'
        ? profile.hasLpgConnection
        : null;
    case 'occupation':
      return profile.occupation && profile.occupation.toLowerCase() !== 'unknown'
        ? profile.occupation
        : null;
    default:
      return null;
  }
}

function compareNumbers(actual: string | number, target: unknown): number {
  const a = typeof actual === 'number' ? actual : Number.parseFloat(String(actual));
  const b = typeof target === 'number' ? target : Number.parseFloat(String(target));
  const numA = Number.isNaN(a) ? 0 : a;
  const numB = Number.isNaN(b) ? 0 : b;
  return numA === numB ? 0 : numA < numB ? -1 : 1;
}

function evaluateCondition(
  condition: RuleCondition,
  profile: CitizenProfile,
  scheme: GovernmentScheme,
  refDate: Date,
  missingFields: Set<string>,
  externalGuidanceEn: string[],
  externalGuidanceHi: string[],
  onStateOverrideUsed: (note: string) => void
): ConditionResult {
  if (condition.operator === 'EXTERNAL_LOOKUP') {
    if (condition.externalGuidanceEn && condition.externalGuidanceEn.trim().length > 0) {
      externalGuidanceEn.push(condition.externalGuidanceEn);
    }
    if (condition.externalGuidanceHi && condition.externalGuidanceHi.trim().length > 0) {
      externalGuidanceHi.push(condition.externalGuidanceHi);
    }
    missingFields.add(condition.field);
    return {
      conditionId: condition.id,
      field: condition.field,
      outcome: 'UNKNOWN',
      reasonEn: condition.descriptionEn,
      reasonHi: condition.descriptionHi
    };
  }

  const rawValue = resolveProfileField(profile, condition.field, refDate);
  if (rawValue === null || String(rawValue).toLowerCase() === 'unknown') {
    missingFields.add(condition.field);
    return {
      conditionId: condition.id,
      field: condition.field,
      outcome: 'UNKNOWN',
      reasonEn: `Missing profile field '${condition.field}' for: ${condition.descriptionEn}`,
      reasonHi: `जानकारी अधूरी है ('${condition.field}'): ${condition.descriptionHi}`
    };
  }

  let effectiveTarget: string | number | string[] | undefined = condition.targetValue;
  if (
    condition.field === 'annualIncome' &&
    profile.state &&
    Object.prototype.hasOwnProperty.call(scheme.stateOverrideIncomeCap, profile.state)
  ) {
    const stateCap = scheme.stateOverrideIncomeCap[profile.state];
    effectiveTarget = stateCap;
    onStateOverrideUsed(`${profile.state} state threshold override applied: ₹${stateCap}`);
  }

  let passed = false;
  switch (condition.operator) {
    case 'EQ':
      passed = String(rawValue).toLowerCase() === String(effectiveTarget ?? '').toLowerCase();
      break;
    case 'NEQ':
      passed = String(rawValue).toLowerCase() !== String(effectiveTarget ?? '').toLowerCase();
      break;
    case 'LT':
      passed = compareNumbers(rawValue, effectiveTarget) < 0;
      break;
    case 'LTE':
      passed = compareNumbers(rawValue, effectiveTarget) <= 0;
      break;
    case 'GT':
      passed = compareNumbers(rawValue, effectiveTarget) > 0;
      break;
    case 'GTE':
      passed = compareNumbers(rawValue, effectiveTarget) >= 0;
      break;
    case 'BETWEEN': {
      const lowerCmp = compareNumbers(rawValue, effectiveTarget);
      const upperCmp = compareNumbers(rawValue, condition.secondaryTargetValue);
      passed = lowerCmp >= 0 && upperCmp <= 0;
      break;
    }
    case 'IN': {
      const list = Array.isArray(effectiveTarget)
        ? effectiveTarget.map((v) => String(v).toLowerCase())
        : [];
      passed = list.includes(String(rawValue).toLowerCase());
      break;
    }
    case 'NOT_IN': {
      const list = Array.isArray(effectiveTarget)
        ? effectiveTarget.map((v) => String(v).toLowerCase())
        : [];
      passed = !list.includes(String(rawValue).toLowerCase());
      break;
    }
    case 'EXISTS':
      passed = true;
      break;
    default:
      passed = false;
  }

  if (passed) {
    return {
      conditionId: condition.id,
      field: condition.field,
      outcome: 'ELIGIBLE',
      reasonEn: `Met: ${condition.descriptionEn} (Value: ${rawValue})`,
      reasonHi: `पूरा हुआ: ${condition.descriptionHi} (मान: ${rawValue})`
    };
  }

  const rangeSuffix =
    condition.secondaryTargetValue !== undefined ? `..${condition.secondaryTargetValue}` : '';
  return {
    conditionId: condition.id,
    field: condition.field,
    outcome: 'NOT_ELIGIBLE',
    reasonEn: `Not met: ${condition.descriptionEn} (Current: ${rawValue}, Required: ${effectiveTarget}${rangeSuffix})`,
    reasonHi: `पूरा नहीं हुआ: ${condition.descriptionHi} (वर्तमान: ${rawValue}, आवश्यक: ${effectiveTarget}${rangeSuffix})`
  };
}

function evaluateGroup(
  group: RuleGroup,
  profile: CitizenProfile,
  scheme: GovernmentScheme,
  refDate: Date,
  conditionResults: ConditionResult[],
  missingFields: Set<string>,
  externalGuidanceEn: string[],
  externalGuidanceHi: string[],
  onStateOverrideUsed: (note: string) => void
): EligibilityOutcome {
  const outcomes: EligibilityOutcome[] = [];

  for (const condition of group.conditions) {
    const res = evaluateCondition(
      condition,
      profile,
      scheme,
      refDate,
      missingFields,
      externalGuidanceEn,
      externalGuidanceHi,
      onStateOverrideUsed
    );
    conditionResults.push(res);
    outcomes.push(res.outcome);
  }

  for (const nested of group.nestedGroups ?? []) {
    const nestedOutcome = evaluateGroup(
      nested,
      profile,
      scheme,
      refDate,
      conditionResults,
      missingFields,
      externalGuidanceEn,
      externalGuidanceHi,
      onStateOverrideUsed
    );
    outcomes.push(nestedOutcome);
  }

  if (group.combinator === 'ALL') {
    if (outcomes.includes('NOT_ELIGIBLE')) return 'NOT_ELIGIBLE';
    if (outcomes.includes('UNKNOWN')) return 'UNKNOWN';
    return 'ELIGIBLE';
  }

  if (outcomes.includes('ELIGIBLE')) return 'ELIGIBLE';
  if (outcomes.length > 0 && outcomes.every((o) => o === 'NOT_ELIGIBLE')) return 'NOT_ELIGIBLE';
  return 'UNKNOWN';
}

export function evaluateScheme(
  profile: CitizenProfile,
  scheme: GovernmentScheme,
  refDate: Date = new Date()
): EligibilityEvaluationResult {
  const conditionResults: ConditionResult[] = [];
  const missingFields = new Set<string>();
  const externalGuidanceEn: string[] = [];
  const externalGuidanceHi: string[] = [];
  let stateOverrideNote: string | null = null;

  const outcome = evaluateGroup(
    scheme.rootRuleGroup,
    profile,
    scheme,
    refDate,
    conditionResults,
    missingFields,
    externalGuidanceEn,
    externalGuidanceHi,
    (note) => {
      stateOverrideNote = note;
    }
  );

  return {
    schemeId: scheme.id,
    schemeCode: scheme.code,
    ruleVersion: scheme.ruleVersion,
    outcome,
    conditionResults,
    missingFields: Array.from(missingFields),
    externalLookupGuidanceEn: externalGuidanceEn,
    externalLookupGuidanceHi: externalGuidanceHi,
    stateOverrideApplied: stateOverrideNote,
    evaluatedAt: refDate.toISOString()
  };
}

export function evaluateAllSchemes(
  profile: CitizenProfile,
  schemes: GovernmentScheme[],
  refDate: Date = new Date()
): EligibilityEvaluationResult[] {
  return schemes.map((scheme) => evaluateScheme(profile, scheme, refDate));
}
