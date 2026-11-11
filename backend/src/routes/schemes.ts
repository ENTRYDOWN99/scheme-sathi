import { Router } from 'express';
import { z } from 'zod';
import type { DatabaseClient } from '../db/database.js';
import { evaluateAllSchemes, evaluateScheme } from '../domain/eligibilityEngine.js';
import {
  authenticateRequest,
  resolveAccessibleUserId,
  type AuthenticatedRequest
} from '../middleware/auth.js';
import { AppError } from '../middleware/errorHandler.js';
import { getOrCreateProfile } from '../services/profileService.js';
import type { GovernmentScheme } from '../types/models.js';

interface SchemeRow {
  id: string;
  payload_json: string;
}

const evaluateRequestSchema = z.object({
  userId: z.string().optional(),
  schemeId: z.string().optional()
});

export function createSchemesRouter(db: DatabaseClient): Router {
  const router = Router();

  router.get('/', async (_req, res, next) => {
    try {
      const rows = await db.query<SchemeRow>('SELECT id, payload_json FROM schemes ORDER BY id ASC');
      const schemes = rows.map((r) => JSON.parse(r.payload_json) as GovernmentScheme);
      res.json({ schemes });
    } catch (err) {
      next(err);
    }
  });

  router.get('/:id', async (req, res, next) => {
    try {
      const row = await db.get<SchemeRow>('SELECT id, payload_json FROM schemes WHERE id = ? OR code = ?', [
        req.params.id,
        req.params.id
      ]);
      if (!row) {
        throw new AppError(404, 'SCHEME_NOT_FOUND', `Scheme '${req.params.id}' not found.`);
      }
      res.json({ scheme: JSON.parse(row.payload_json) as GovernmentScheme });
    } catch (err) {
      next(err);
    }
  });

  router.post(
    '/evaluate',
    authenticateRequest,
    async (req: AuthenticatedRequest, res, next) => {
      try {
        const parsed = evaluateRequestSchema.parse(req.body ?? {});
        const access = resolveAccessibleUserId(req, parsed.userId);
        if ('errorStatus' in access) {
          throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
        }

        const profile = await getOrCreateProfile(db, access.userId, req.user?.displayName);
        const rows = await db.query<SchemeRow>('SELECT id, payload_json FROM schemes ORDER BY id ASC');
        const schemes = rows.map((r) => JSON.parse(r.payload_json) as GovernmentScheme);

        if (parsed.schemeId) {
          const scheme = schemes.find(
            (s) => s.id.toLowerCase() === parsed.schemeId!.toLowerCase()
          );
          if (!scheme) {
            throw new AppError(404, 'SCHEME_NOT_FOUND', `Scheme '${parsed.schemeId}' not found.`);
          }
          res.json({
            userId: access.userId,
            results: [evaluateScheme(profile, scheme)]
          });
          return;
        }

        res.json({
          userId: access.userId,
          results: evaluateAllSchemes(profile, schemes)
        });
      } catch (err) {
        next(err);
      }
    }
  );

  return router;
}
