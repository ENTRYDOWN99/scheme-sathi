/**
 * Enforces Scheme Saathi's strict Aadhaar prohibition, PII redaction,
 * prompt-injection / auto-submit defense, and Hindi/English voice numeral parsing.
 */

export function containsAadhaarNumber(input: string): boolean {
  if (!input) return false;
  // Exclude valid E.164 Indian mobile numbers (+91 followed by 10 digits)
  const withoutE164India = input.replace(/\+91[\s-]?\d{5}[\s-]?\d{5}\b/g, ' ');
  // Collapse whitespace/hyphens only between digits so adjacent words remain separated
  const collapsedDigits = withoutE164India.replace(/(?<=\d)[\s-]+(?=\d)/g, '');
  return /(?<!\d)\d{12}(?!\d)/.test(collapsedDigits);
}

export function assertNoAadhaarInPayload(obj: unknown): void {
  const stack: unknown[] = [obj];
  while (stack.length > 0) {
    const current = stack.pop();
    if (typeof current === 'string') {
      if (containsAadhaarNumber(current)) {
        const err = new Error(
          'Security Rule: Entering or storing a 12-digit Aadhaar number is strictly prohibited.'
        ) as Error & { statusCode?: number; code?: string };
        err.statusCode = 400;
        err.code = 'AADHAAR_FORBIDDEN';
        throw err;
      }
    } else if (Array.isArray(current)) {
      stack.push(...current);
    } else if (current && typeof current === 'object') {
      const record = current as Record<string, unknown>;
      for (const [k, v] of Object.entries(record)) {
        if (k.toLowerCase().includes('aadhaar') || k.toLowerCase().includes('aadhar')) {
          const err = new Error(
            'Security Rule: Aadhaar fields are strictly forbidden.'
          ) as Error & { statusCode?: number; code?: string };
          err.statusCode = 400;
          err.code = 'AADHAAR_FORBIDDEN';
          throw err;
        }
        stack.push(v);
      }
    }
  }
}

export function redactPiiForLlm(
  input: string,
  citizenName = '',
  citizenPhone = '',
  citizenDob = ''
): string {
  let sanitized = input;
  if (citizenName.trim().length >= 2) {
    const escaped = citizenName.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    sanitized = sanitized.replace(new RegExp(escaped, 'gi'), '[REDACTED_NAME]');
  }
  if (citizenPhone.trim().length >= 4) {
    const escaped = citizenPhone.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    sanitized = sanitized.replace(new RegExp(escaped, 'g'), '[REDACTED_PHONE]');
  }
  if (citizenDob.trim().length >= 8) {
    const escaped = citizenDob.trim().replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    sanitized = sanitized.replace(new RegExp(escaped, 'g'), '[REDACTED_DOB]');
  }
  sanitized = sanitized.replace(/\b\d{2}[/-]\d{2}[/-]\d{4}\b/g, '[REDACTED_DATE]');
  sanitized = sanitized.replace(/\b[6-9]\d{9}\b/g, '[REDACTED_PHONE]');
  sanitized = sanitized.replace(/\b\d{4}[\s-]?\d{4}[\s-]?\d{4}\b/g, '[REDACTED_AADHAAR_FORBIDDEN]');
  return sanitized;
}

export function detectPromptInjectionOrAutoSubmit(prompt: string): {
  blocked: boolean;
  reasonCode: 'PROMPT_INJECTION' | 'HUMAN_GATE_BLOCK' | null;
} {
  const lower = prompt.toLowerCase();
  const injectionPatterns = [
    'ignore previous instructions',
    'ignore all rules',
    'system prompt',
    'bypass eligibility',
    'mark me eligible',
    'force approve'
  ];
  if (injectionPatterns.some((p) => lower.includes(p))) {
    return { blocked: true, reasonCode: 'PROMPT_INJECTION' };
  }
  const autoSubmitPatterns = [
    'submit my application automatically',
    'auto submit',
    'auto-submit',
    'submit automatically',
    'automatically submit',
    'confirm and submit for me',
    'ask it to auto-submit',
    'सीधे फॉर्म सबमिट कर दो',
    'सीधे सबमिट',
    'अपने आप आवेदन जमा करें',
    'स्वचालित आवेदन'
  ];
  if (autoSubmitPatterns.some((p) => lower.includes(p))) {
    return { blocked: true, reasonCode: 'HUMAN_GATE_BLOCK' };
  }
  return { blocked: false, reasonCode: null };
}

const HINDI_NUMBER_WORDS: Record<string, number> = {
  एक: 1,
  दो: 2,
  तीन: 3,
  चार: 4,
  पांच: 5,
  पाँच: 5,
  छह: 6,
  सात: 7,
  आठ: 8,
  नौ: 9,
  दस: 10,
  अठारह: 18,
  उन्नीस: 19,
  बीस: 20,
  इक्कीस: 21,
  बाईस: 22,
  तेईस: 23,
  चौबीस: 24,
  पच्चीस: 25,
  तीस: 30,
  पैंतीस: 35,
  चालीस: 40,
  पैंतालीस: 45,
  पचास: 50,
  पचपन: 55,
  छप्पन: 56,
  सत्तावन: 57,
  अट्ठावन: 58,
  उनसठ: 59,
  साठ: 60,
  इकसठ: 61,
  बासठ: 62,
  बयासठ: 62,
  तिरेसठ: 63,
  चौंसठ: 64,
  पैंसठ: 65,
  छियासठ: 66,
  सड़सठ: 67,
  अड़सठ: 68,
  उनहत्तर: 69,
  सत्तर: 70,
  पचहत्तर: 75,
  अस्सी: 80,
  पचासी: 85,
  नब्बे: 90,
  pacpan: 55,
  unsath: 59,
  saath: 60,
  iksath: 61,
  basath: 62,
  baasath: 62,
  bayasath: 62,
  painsath: 65,
  sattar: 70
};

export function parseHindiOrEnglishNumber(utterance: string): number | null {
  const devanagariMap: Record<string, string> = {
    '०': '0',
    '१': '1',
    '२': '2',
    '३': '3',
    '४': '4',
    '५': '5',
    '६': '6',
    '७': '7',
    '८': '8',
    '९': '9'
  };
  const normalized = [...utterance].map((ch) => devanagariMap[ch] ?? ch).join('');
  const tokens = normalized
    .toLowerCase()
    .split(/[\s,।.?]+/)
    .filter(Boolean);
  for (const token of tokens) {
    if (token in HINDI_NUMBER_WORDS) {
      return HINDI_NUMBER_WORDS[token];
    }
  }
  for (const [word, value] of Object.entries(HINDI_NUMBER_WORDS)) {
    if (normalized.toLowerCase().includes(word)) {
      return value;
    }
  }
  const match = /\b(\d{1,7})\b/.exec(normalized);
  return match ? Number(match[1]) : null;
}

export function formatAgeEchoConfirmation(age: number, language: string): string {
  if (language === 'hi') {
    return `आपकी उम्र ${age} साल है, सही है?`;
  }
  return `Your age is ${age} years, is that correct?`;
}
