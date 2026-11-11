import crypto from 'node:crypto';
import type { DatabaseClient } from '../db/database.js';
import type { Role } from '../types/models.js';
import { redactPiiForLlm } from '../security/piiGuard.js';

export async function recordAuditLog(
  db: DatabaseClient,
  params: {
    actorId: string;
    actorRole: Role;
    action: string;
    entityType: string;
    entityId: string;
    details: string;
  }
): Promise<void> {
  const id = `aud_${crypto.randomUUID()}`;
  const safeDetails = redactPiiForLlm(params.details);
  await db.run(
    `INSERT INTO audit_logs (id, actor_id, actor_role, action, entity_type, entity_id, details, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
    [
      id,
      params.actorId,
      params.actorRole,
      params.action,
      params.entityType,
      params.entityId,
      safeDetails,
      new Date().toISOString()
    ]
  );
}
