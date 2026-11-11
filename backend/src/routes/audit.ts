import { Router } from 'express';
import type { DatabaseClient } from '../db/database.js';
import { authenticateRequest, requireRole } from '../middleware/auth.js';

export function createAuditRouter(db: DatabaseClient): Router {
  const router = Router();

  router.get(
    '/',
    authenticateRequest,
    requireRole('SUPERVISOR', 'REVIEWER'),
    async (req, res, next) => {
      try {
        const limit = Math.min(200, Math.max(1, Number(req.query.limit) || 50));
        const logs = await db.query(
          `SELECT id, actor_id, actor_role, action, entity_type, entity_id, details, created_at
           FROM audit_logs ORDER BY created_at DESC LIMIT ?`,
          [limit]
        );
        res.json({ logs });
      } catch (err) {
        next(err);
      }
    }
  );

  // Supervisor summary dashboard analytics across roles/applications/tasks
  router.get(
    '/metrics',
    authenticateRequest,
    requireRole('SUPERVISOR', 'REVIEWER', 'CSC_OPERATOR'),
    async (_req, res, next) => {
      try {
        const appStats = await db.query<{ status: string; count: number }>(
          'SELECT status, COUNT(*) as count FROM applications GROUP BY status'
        );
        const taskStats = await db.query<{ status: string; count: number }>(
          'SELECT status, COUNT(*) as count FROM tasks GROUP BY status'
        );
        const totalUsersRow = await db.get<{ count: number }>(
          'SELECT COUNT(*) as count FROM users WHERE is_active = 1'
        );
        res.json({
          applicationsByStatus: appStats,
          tasksByStatus: taskStats,
          activeUsers: Number(totalUsersRow?.count ?? 0)
        });
      } catch (err) {
        next(err);
      }
    }
  );

  return router;
}
