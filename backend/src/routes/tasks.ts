import crypto from 'node:crypto';
import { Router } from 'express';
import { z } from 'zod';
import type { DatabaseClient } from '../db/database.js';
import {
  authenticateRequest,
  requireRole,
  resolveAccessibleUserId,
  type AuthenticatedRequest
} from '../middleware/auth.js';
import { AppError } from '../middleware/errorHandler.js';
import { recordAuditLog } from '../services/auditService.js';
import type { CollaborativeTask, Role, TaskStatus } from '../types/models.js';

interface TaskRow {
  id: string;
  user_id: string;
  application_id: string | null;
  scheme_id: string;
  scheme_name: string;
  title_en: string;
  title_hi: string;
  description_en: string;
  description_hi: string;
  status: TaskStatus;
  assigned_to_role: Role;
  assigned_operator_name: string;
  due_date_text: string;
  is_notification_unread: number;
  created_at: string;
  updated_at: string;
}

const createTaskSchema = z.object({
  userId: z.string().optional(),
  applicationId: z.string().optional(),
  schemeId: z.string().min(1),
  schemeName: z.string().min(1),
  titleEn: z.string().min(2).max(160),
  titleHi: z.string().max(160).optional(),
  descriptionEn: z.string().max(600).optional(),
  descriptionHi: z.string().max(600).optional(),
  assignedToRole: z.enum(['CITIZEN', 'CSC_OPERATOR', 'SUPERVISOR', 'REVIEWER']).optional(),
  assignedOperatorName: z.string().max(100).optional(),
  dueDateText: z.string().max(60).optional()
});

const updateTaskSchema = z.object({
  status: z.enum(['OPEN', 'IN_PROGRESS', 'COMPLETED']).optional(),
  isNotificationUnread: z.boolean().optional(),
  assignedToRole: z.enum(['CITIZEN', 'CSC_OPERATOR', 'SUPERVISOR', 'REVIEWER']).optional()
});

function rowToTask(row: TaskRow): CollaborativeTask {
  return {
    id: row.id,
    userId: row.user_id,
    applicationId: row.application_id,
    schemeId: row.scheme_id,
    schemeName: row.scheme_name,
    titleEn: row.title_en,
    titleHi: row.title_hi,
    descriptionEn: row.description_en,
    descriptionHi: row.description_hi,
    status: row.status,
    assignedToRole: row.assigned_to_role,
    assignedOperatorName: row.assigned_operator_name,
    dueDateText: row.due_date_text,
    isNotificationUnread: Boolean(row.is_notification_unread),
    createdAt: row.created_at,
    updatedAt: row.updated_at
  };
}

export function createTasksRouter(db: DatabaseClient): Router {
  const router = Router();

  router.get('/', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const requestedUserId = typeof req.query.userId === 'string' ? req.query.userId : undefined;
      let rows: TaskRow[];
      if (req.user!.role === 'CITIZEN') {
        rows = await db.query<TaskRow>(
          'SELECT * FROM tasks WHERE user_id = ? ORDER BY updated_at DESC',
          [req.user!.id]
        );
      } else if (requestedUserId) {
        rows = await db.query<TaskRow>(
          'SELECT * FROM tasks WHERE user_id = ? ORDER BY updated_at DESC',
          [requestedUserId]
        );
      } else {
        rows = await db.query<TaskRow>('SELECT * FROM tasks ORDER BY updated_at DESC');
      }

      res.json({ tasks: rows.map(rowToTask) });
    } catch (err) {
      next(err);
    }
  });

  router.post(
    '/',
    authenticateRequest,
    requireRole('CITIZEN', 'CSC_OPERATOR', 'SUPERVISOR', 'REVIEWER'),
    async (req: AuthenticatedRequest, res, next) => {
      try {
        const parsed = createTaskSchema.parse(req.body);
        const access = resolveAccessibleUserId(req, parsed.userId);
        if ('errorStatus' in access) {
          throw new AppError(access.errorStatus, 'FORBIDDEN', access.message);
        }

        const id = `tsk_${crypto.randomUUID()}`;
        const now = new Date().toISOString();
        const assignedRole = parsed.assignedToRole ?? 'CITIZEN';

        await db.run(
          `INSERT INTO tasks (
            id, user_id, application_id, scheme_id, scheme_name,
            title_en, title_hi, description_en, description_hi,
            status, assigned_to_role, assigned_operator_name, due_date_text,
            is_notification_unread, created_at, updated_at
          ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, 1, ?, ?)`,
          [
            id,
            access.userId,
            parsed.applicationId ?? null,
            parsed.schemeId,
            parsed.schemeName,
            parsed.titleEn.trim(),
            parsed.titleHi?.trim() || parsed.titleEn.trim(),
            parsed.descriptionEn?.trim() || '',
            parsed.descriptionHi?.trim() || parsed.descriptionEn?.trim() || '',
            assignedRole,
            parsed.assignedOperatorName?.trim() || 'CSC Desk',
            parsed.dueDateText?.trim() || 'Pending action',
            now,
            now
          ]
        );

        const created = await db.get<TaskRow>('SELECT * FROM tasks WHERE id = ?', [id]);
        await recordAuditLog(db, {
          actorId: req.user!.id,
          actorRole: req.user!.role,
          action: 'TASK_CREATED',
          entityType: 'TASK',
          entityId: id,
          details: `Created task for ${parsed.schemeId} assigned to ${assignedRole}`
        });
        res.status(201).json({ task: rowToTask(created!) });
      } catch (err) {
        next(err);
      }
    }
  );

  router.patch('/:id', authenticateRequest, async (req: AuthenticatedRequest, res, next) => {
    try {
      const parsed = updateTaskSchema.parse(req.body);
      const existing = await db.get<TaskRow>('SELECT * FROM tasks WHERE id = ?', [
        String(req.params.id)
      ]);
      if (!existing) {
        throw new AppError(404, 'TASK_NOT_FOUND', 'Task not found.');
      }

      if (req.user!.role === 'CITIZEN' && existing.user_id !== req.user!.id) {
        throw new AppError(403, 'FORBIDDEN', 'Citizens can only update their own tasks.');
      }
      if (parsed.assignedToRole && req.user!.role === 'CITIZEN') {
        throw new AppError(403, 'FORBIDDEN', 'Only CSC staff or Supervisors can reassign tasks.');
      }

      const now = new Date().toISOString();
      await db.run(
        `UPDATE tasks SET
          status = ?,
          is_notification_unread = ?,
          assigned_to_role = ?,
          updated_at = ?
         WHERE id = ?`,
        [
          parsed.status ?? existing.status,
          parsed.isNotificationUnread !== undefined
            ? parsed.isNotificationUnread
              ? 1
              : 0
            : existing.is_notification_unread,
          parsed.assignedToRole ?? existing.assigned_to_role,
          now,
          existing.id
        ]
      );

      const updated = await db.get<TaskRow>('SELECT * FROM tasks WHERE id = ?', [existing.id]);
      res.json({ task: rowToTask(updated!) });
    } catch (err) {
      next(err);
    }
  });

  return router;
}
