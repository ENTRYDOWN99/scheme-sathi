import { Router } from 'express';
import { z } from 'zod';
import type { DatabaseClient } from '../db/database.js';
import {
  authenticateRequest,
  resolveAccessibleUserId,
  type AuthenticatedRequest
} from '../middleware/auth.js';
import { aiRateLimiter } from '../middleware/rateLimiter.js';
import { AppError } from '../middleware/errorHandler.js';
import { formatAgeEchoConfirmation, parseHindiOrEnglishNumber } from '../security/piiGuard.js';
import { generateGroundedChatReply } from '../services/aiService.js';
import { getOrCreateProfile } from '../services/profileService.js';
import type { GovernmentScheme } from '../types/models.js';

const chatRequestSchema = z.object({
  userId: z.string().optional(),
  prompt: z.string().min(1).max(2000),
  language: z.enum(['en', 'hi']).optional()
});

const voiceAgeSchema = z.object({
  utterance: z.string().min(1).max(200),
  language: z.enum(['en', 'hi']).optional()
});

export function createAiRouter(db: DatabaseClient): Router {
  const router = Router();

  router.post('/chat', authenticateRequest, aiRateLimiter, async (req: AuthenticatedRequest, res, next) => {
    try {
      const parsed = chatRequestSchema.parse(req.body);
      const access = resolveAccessibleUserId(req, parsed.userId);
      if ('errorStatus' in access) {
        throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
      }

      const profile = await getOrCreateProfile(db, access.userId, req.user?.displayName);
      const language = parsed.language ?? profile.preferredLanguage;
      const rows = await db.query<{ payload_json: string }>(
        'SELECT payload_json FROM schemes ORDER BY id ASC'
      );
      const schemes = rows.map((r) => JSON.parse(r.payload_json) as GovernmentScheme);

      const result = await generateGroundedChatReply({
        prompt: parsed.prompt,
        profile,
        schemes,
        language
      });

      res.json(result);
    } catch (err) {
      next(err);
    }
  });

  router.post('/voice-age', authenticateRequest, async (req: AuthenticatedRequest, res) => {
    const parsed = voiceAgeSchema.parse(req.body);
    const language = parsed.language ?? req.user?.preferredLanguage ?? 'hi';
    const age = parseHindiOrEnglishNumber(parsed.utterance);
    if (age === null || age < 1 || age > 120) {
      res.status(400).json({
        error: 'AGE_NOT_RECOGNIZED',
        message:
          language === 'hi'
            ? 'आयु स्पष्ट नहीं हो सकी। कृपया 1 से 120 के बीच संख्या बोलें या दर्ज करें।'
            : 'Could not recognize age between 1 and 120. Please repeat or enter DD/MM/YYYY.'
      });
      return;
    }

    res.json({
      detectedAge: age,
      estimatedBirthYear: new Date().getFullYear() - age,
      confirmationPrompt: formatAgeEchoConfirmation(age, language)
    });
  });

  return router;
}
