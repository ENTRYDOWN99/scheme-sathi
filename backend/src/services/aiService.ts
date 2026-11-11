import { config } from '../config/env.js';
import { evaluateAllSchemes, resolveProfileAge } from '../domain/eligibilityEngine.js';
import { detectPromptInjectionOrAutoSubmit, redactPiiForLlm } from '../security/piiGuard.js';
import type { CitizenProfile, GovernmentScheme, LanguageCode } from '../types/models.js';

export type AiErrorCategory = 'busy' | 'network' | 'generic';

export interface AiChatResponse {
  reply: string;
  modelUsed: string;
  usedFallback: boolean;
  errorCategory?: AiErrorCategory;
  blockedBySafety: boolean;
  groundingSummary: string;
}

export function classifyAiError(err: unknown): AiErrorCategory {
  const msg =
    err instanceof Error
      ? `${err.name} ${err.message}`
      : typeof err === 'string'
        ? err
        : JSON.stringify(err);
  const lower = msg.toLowerCase();

  if (
    lower.includes('429') ||
    lower.includes('quota') ||
    lower.includes('resource_exhausted') ||
    lower.includes('rate limit')
  ) {
    return 'busy';
  }
  if (
    lower.includes('network') ||
    lower.includes('fetch failed') ||
    lower.includes('enotfound') ||
    lower.includes('etimedout') ||
    lower.includes('timeout') ||
    lower.includes('abort') ||
    lower.includes('econnrefused')
  ) {
    return 'network';
  }
  return 'generic';
}

export function getFriendlyAiErrorMessage(
  category: AiErrorCategory,
  language: LanguageCode
): string {
  if (language === 'hi') {
    switch (category) {
      case 'busy':
        return 'AI अभी व्यस्त है, कृपया एक मिनट में पुनः प्रयास करें। नीचे ऑफ़लाइन सारांश देखें।';
      case 'network':
        return 'कृपया अपना इंटरनेट कनेक्शन जांचें और पुनः प्रयास करें। नीचे ऑफ़लाइन सारांश देखें।';
      case 'generic':
      default:
        return 'क्षमा करें, अभी उत्तर प्राप्त नहीं हो सका। नीचे ऑफ़लाइन सारांश देखें।';
    }
  }
  switch (category) {
    case 'busy':
      return 'AI is busy right now, please try again in a minute. Showing offline deterministic summary below.';
    case 'network':
      return 'Please check your internet connection and try again. Showing offline deterministic summary below.';
    case 'generic':
    default:
      return 'Unable to reach AI right now. Showing offline deterministic summary below.';
  }
}

export function buildDeterministicChatSummary(
  profile: CitizenProfile,
  schemes: GovernmentScheme[],
  language: LanguageCode
): { summaryText: string; groundingContext: string } {
  const results = evaluateAllSchemes(profile, schemes);
  const computedAge = resolveProfileAge(profile);
  const eligible = results.filter((r) => r.outcome === 'ELIGIBLE');
  const unknown = results.filter((r) => r.outcome === 'UNKNOWN');

  const schemeName = (id: string) => {
    const s = schemes.find((item) => item.id === id);
    if (!s) return id;
    return language === 'hi' ? s.nameHi : s.nameEn;
  };

  const groundingLines = results.map((r) => {
    const missing = r.missingFields.length > 0 ? ` missing=[${r.missingFields.join(', ')}]` : '';
    const override = r.stateOverrideApplied ? ` (${r.stateOverrideApplied})` : '';
    return `- ${r.schemeCode}: ${r.outcome}${missing}${override}`;
  });

  const groundingContext = [
    `State=${profile.state}, District=${profile.district || 'N/A'}, Age=${computedAge >= 0 ? computedAge : 'Unknown'}, BPL=${profile.bplStatus}, FarmerLand=${profile.ownsAgriculturalLand}, Bank=${profile.hasBankAccount}, TaxPayer=${profile.isIncomeTaxPayer}`,
    ...groundingLines
  ].join('\n');

  if (language === 'hi') {
    const parts: string[] = [
      `निर्धारित नियम इंजन सारांश (आपकी आयु: ${computedAge >= 0 ? `${computedAge} वर्ष` : 'अज्ञात'}, राज्य: ${profile.state}):`
    ];
    if (eligible.length > 0) {
      parts.push(`पात्र योजनाएं: ${eligible.map((r) => schemeName(r.schemeId)).join(', ')}।`);
    } else {
      parts.push('अभी किसी योजना की सभी शर्तें पूर्ण रूप से पात्र नहीं हैं।');
    }
    if (unknown.length > 0) {
      parts.push(
        `अतिरिक्त जानकारी आवश्यक: ${unknown.map((r) => schemeName(r.schemeId)).join(', ')}।`
      );
    }
    parts.push(
      'यह केवल प्रारंभिक मार्गदर्शन है; अंतिम निर्णय संबंधित सरकारी पोर्टल या कार्यालय द्वारा किया जाएगा।'
    );
    return { summaryText: parts.join('\n\n'), groundingContext };
  }

  const partsEn: string[] = [
    `Deterministic Rule Engine Summary (Age: ${computedAge >= 0 ? `${computedAge} yrs` : 'Unknown'}, State: ${profile.state}):`
  ];
  if (eligible.length > 0) {
    partsEn.push(`Eligible schemes: ${eligible.map((r) => schemeName(r.schemeId)).join(', ')}.`);
  } else {
    partsEn.push('No scheme is currently marked fully ELIGIBLE based on provided fields.');
  }
  if (unknown.length > 0) {
    partsEn.push(
      `Needs more information / verification: ${unknown.map((r) => schemeName(r.schemeId)).join(', ')}.`
    );
  }
  partsEn.push(
    'Note: Informational guidance only; final eligibility rests with the issuing government authority.'
  );
  return { summaryText: partsEn.join('\n\n'), groundingContext };
}

async function callGeminiModel(
  model: string,
  systemInstruction: string,
  userPrompt: string,
  apiKey: string,
  timeoutMs: number
): Promise<string> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(
      model
    )}:generateContent?key=${encodeURIComponent(apiKey)}`;

    const response = await fetch(endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      signal: controller.signal,
      body: JSON.stringify({
        systemInstruction: {
          parts: [{ text: systemInstruction }]
        },
        contents: [
          {
            role: 'user',
            parts: [{ text: userPrompt }]
          }
        ],
        generationConfig: {
          temperature: 0.25,
          maxOutputTokens: 700
        }
      })
    });

    if (!response.ok) {
      const errorText = await response.text().catch(() => '');
      throw new Error(`HTTP ${response.status}: ${errorText.slice(0, 200)}`);
    }

    const json = (await response.json()) as {
      candidates?: Array<{
        content?: { parts?: Array<{ text?: string }> };
      }>;
    };
    const text = json.candidates?.[0]?.content?.parts?.[0]?.text?.trim();
    if (!text) {
      throw new Error('Empty response from Gemini model');
    }
    return text;
  } finally {
    clearTimeout(timer);
  }
}

export async function generateGroundedChatReply(params: {
  prompt: string;
  profile: CitizenProfile;
  schemes: GovernmentScheme[];
  language: LanguageCode;
  fetchImpl?: (model: string, systemInstruction: string, userPrompt: string) => Promise<string>;
}): Promise<AiChatResponse> {
  const { prompt, profile, schemes, language, fetchImpl } = params;

  const { summaryText, groundingContext } = buildDeterministicChatSummary(
    profile,
    schemes,
    language
  );

  // Guard 1: Prompt injection or AI auto-submission attempts
  const safetyCheck = detectPromptInjectionOrAutoSubmit(prompt);
  if (safetyCheck.blocked) {
    const reply =
      language === 'hi'
        ? 'सुरक्षा नीति: SchemeSaathi कोई भी आवेदन स्वतः जमा नहीं कर सकता और न ही पहचान/आधार डेटा मांग सकता है। कृपया आवेदन स्वयं समीक्षा करके Human Confirmation Gate से पुष्टि करें।'
        : 'Safety Policy: SchemeSaathi cannot auto-submit applications or override official eligibility rules. Please review your details and use the Human Confirmation Gate.';
    return {
      reply,
      modelUsed: 'policy-guard',
      usedFallback: true,
      blockedBySafety: true,
      groundingSummary: groundingContext
    };
  }

  // Always redact PII (including DOB/phone/name/any Aadhaar-like digits) before LLM prompt
  const redactedPrompt = redactPiiForLlm(
    prompt,
    profile.displayName,
    profile.phoneNumber,
    profile.dateOfBirth
  );

  const systemInstruction = [
    'You are SchemeSaathi Assistant helping Indian citizens understand public welfare schemes.',
    `Respond clearly in ${language === 'hi' ? 'simple respectful Hindi (Devanagari)' : 'simple clear English'}.`,
    'STRICT GROUNDING RULES:',
    '1. Never decide eligibility on your own; rely ONLY on the deterministic rule results below.',
    '2. Never ask for or accept a 12-digit Aadhaar number.',
    '3. Never claim you can submit an application on the citizen behalf; human confirmation is mandatory.',
    '4. Always mention this is informational guidance and final decision rests with the issuing authority.',
    '',
    'DETERMINISTIC PROFILE & RULE RESULTS (NO RAW DOB/PII):',
    groundingContext
  ].join('\n');

  const modelsToTry = [config.geminiModel, config.geminiFallbackModel].filter(
    (m, idx, arr) => m && arr.indexOf(m) === idx
  );

  if (!config.geminiApiKey && !fetchImpl) {
    return {
      reply: summaryText,
      modelUsed: 'deterministic-offline',
      usedFallback: true,
      blockedBySafety: false,
      groundingSummary: groundingContext
    };
  }

  let lastCategory: AiErrorCategory = 'generic';
  for (const model of modelsToTry) {
    try {
      const reply = fetchImpl
        ? await fetchImpl(model, systemInstruction, redactedPrompt)
        : await callGeminiModel(
            model,
            systemInstruction,
            redactedPrompt,
            config.geminiApiKey,
            config.geminiTimeoutMs
          );
      return {
        reply,
        modelUsed: model,
        usedFallback: false,
        blockedBySafety: false,
        groundingSummary: groundingContext
      };
    } catch (err) {
      lastCategory = classifyAiError(err);
      console.error(`[aiService] model=${model} category=${lastCategory}`);
    }
  }

  const friendlyPrefix = getFriendlyAiErrorMessage(lastCategory, language);
  return {
    reply: `${friendlyPrefix}\n\n${summaryText}`,
    modelUsed: 'deterministic-fallback',
    usedFallback: true,
    errorCategory: lastCategory,
    blockedBySafety: false,
    groundingSummary: groundingContext
  };
}
