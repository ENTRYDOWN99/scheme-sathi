import crypto from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import type { DatabaseClient } from '../db/database.js';
import {
  authenticateRequest,
  resolveAccessibleUserId,
  type AuthenticatedRequest
} from '../middleware/auth.js';
import { AppError } from '../middleware/errorHandler.js';
import type { DocumentChecklistItem, DocumentStatus } from '../types/models.js';

interface ChecklistRow {
  id: string;
  user_id: string;
  scheme_id: string;
  document_id: string;
  status: DocumentStatus;
  updated_at: string;
}

const upsertChecklistSchema = z.object({
  userId: z.string().optional(),
  schemeId: z.string().min(1),
  documentId: z.string().min(1),
  status: z.enum(['HAVE', 'MISSING', 'UNKNOWN'])
});

function rowToItem(row: ChecklistRow): DocumentChecklistItem {
  return {
    id: row.id,
    userId: row.user_id,
    schemeId: row.scheme_id,
    documentId: row.document_id,
    status: row.status,
    updatedAt: row.updated_at
  };
}

export function createChecklistsRouter(db: DatabaseClient): Router {
  const router = Router();

  router.get('/', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const requested = typeof req.query.userId === 'string' ? req.query.userId : undefined;
      const schemeId = typeof req.query.schemeId === 'string' ? req.query.schemeId : undefined;
      const access = resolveAccessibleUserId(req, requested);
      if ('errorStatus' in access) {
        throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
      }

      const rows = schemeId
        ? await db.query<ChecklistRow>(
            'SELECT * FROM document_checklists WHERE user_id = ? AND scheme_id = ? ORDER BY document_id ASC',
            [access.userId, schemeId]
          )
        : await db.query<ChecklistRow>(
            'SELECT * FROM document_checklists WHERE user_id = ? ORDER BY scheme_id, document_id ASC',
            [access.userId]
          );

      res.json({ items: rows.map(rowToItem) });
    } catch (err) {
      next(err);
    }
  });

  router.put('/', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const parsed = upsertChecklistSchema.parse(req.body);
      const access = resolveAccessibleUserId(req, parsed.userId);
      if ('errorStatus' in access) {
        throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
      }

      const now = new Date().toISOString();
      const id = `chk_${crypto.randomUUID()}`;
      await db.run(
        `INSERT INTO document_checklists (id, user_id, scheme_id, document_id, status, updated_at)
         VALUES (?, ?, ?, ?, ?, ?)
         ON CONFLICT (user_id, scheme_id, document_id) DO UPDATE SET
           status = excluded.status,
           updated_at = excluded.updated_at`,
        [id, access.userId, parsed.schemeId, parsed.documentId, parsed.status, now]
      );

      const saved = await db.get<ChecklistRow>(
        'SELECT * FROM document_checklists WHERE user_id = ? AND scheme_id = ? AND document_id = ?',
        [access.userId, parsed.schemeId, parsed.documentId]
      );
      res.json({ item: rowToItem(saved!) });
    } catch (err) {
      next(err);
    }
  });

  return router;
}
