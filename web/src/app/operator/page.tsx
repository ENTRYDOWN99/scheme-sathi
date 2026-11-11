'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { ShieldCheck } from 'lucide-react';
import type { AuditLogRow } from '@shared/api-client';
import { useSaathi } from '@/context/SaathiProvider';
import { apiClient } from '@/lib/api';

export default function OperatorWorkbenchPage() {
  const { user, applications, language, t, refreshSessionAndData } = useSaathi();
  const [auditLogs, setAuditLogs] = useState<AuditLogRow[]>([]);
  const [metrics, setMetrics] = useState<{
    activeUsers: number;
    applicationsByStatus: Array<{ status: string; count: number }>;
  } | null>(null);
  const [noteByApp, setNoteByApp] = useState<Record<string, string>>({});
  const [error, setError] = useState('');

  const canAccess =
    user && (user.role === 'CSC_OPERATOR' || user.role === 'SUPERVISOR' || user.role === 'REVIEWER');
  const canReview = user && (user.role === 'REVIEWER' || user.role === 'SUPERVISOR');

  useEffect(() => {
    if (!canAccess) return;
    apiClient
      .getAuditMetrics()
      .then(setMetrics)
      .catch(() => undefined);
    if (canReview) {
      apiClient
        .getAuditLogs(30)
        .then((r) => setAuditLogs(r.logs))
        .catch(() => undefined);
    }
  }, [canAccess, canReview]);

  if (!user || !canAccess) {
    return (
      <div className="saathi-card p-6 text-center space-y-3">
        <p className="font-semibold">
          {language === 'hi'
            ? 'यह पृष्ठ केवल CSC ऑपरेटर, समीक्षक और पर्यवेक्षक भूमिकाओं के लिए है।'
            : 'RBAC Protected: Sign in as CSC Operator, Reviewer, or Supervisor to access this workbench.'}
        </p>
        <Link href="/login" className="saathi-btn-primary inline-flex items-center px-5 py-2.5 rounded-xl font-bold text-sm">
          {t.nav.login}
        </Link>
      </div>
    );
  }

  const handleDecision = async (appId: string, decision: 'APPROVED' | 'REJECTED') => {
    setError('');
    try {
      const note = noteByApp[appId] || `${decision} by ${user.role}`;
      await apiClient.reviewApplication(appId, decision, note);
      await refreshSessionAndData();
      if (canReview) {
        const res = await apiClient.getAuditLogs(30);
        setAuditLogs(res.logs);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Review update failed');
    }
  };

  return (
    <div className="space-y-6">
      <div className="saathi-card p-6 flex flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-3">
          <ShieldCheck className="w-7 h-7" style={{ color: 'var(--saathi-primary)' }} />
          <div>
            <h1 className="text-xl font-bold">
              {t.roles[user.role]} Workbench
            </h1>
            <p className="text-xs" style={{ color: 'var(--saathi-on-surface-muted)' }}>
              {canReview
                ? 'Authorized to verify applications, approve/reject handoffs, and inspect audit logs.'
                : 'CSC Operator view: monitor applications and coordinate handoffs (approvals restricted to Reviewer/Supervisor).'}
            </p>
          </div>
        </div>

        {metrics && (
          <div className="flex gap-4 text-sm font-bold">
            <span>Active Users: {metrics.activeUsers}</span>
            <span>Applications: {applications.length}</span>
          </div>
        )}
      </div>

      {error && (
        <p role="alert" className="text-sm font-semibold" style={{ color: 'var(--saathi-error)' }}>
          {error}
        </p>
      )}

      <section className="space-y-3">
        <h2 className="text-lg font-bold">Applications Queue</h2>
        {applications.map((app) => (
          <div key={app.id} className="saathi-card p-5 space-y-3">
            <div className="flex flex-wrap justify-between gap-2">
              <div>
                <h3 className="font-bold">
                  {app.schemeId} — {app.applicantName}
                </h3>
                <p className="text-xs" style={{ color: 'var(--saathi-on-surface-muted)' }}>
                  {app.stateDistrict} • Confirmed: {app.humanConfirmed ? 'Yes' : 'No'} • Status:{' '}
                  <strong>{app.status}</strong>
                </p>
              </div>
              <span className="text-xs font-mono">{app.id}</span>
            </div>

            {canReview && app.humanConfirmed && (
              <div className="flex flex-wrap gap-2 items-center">
                <input
                  type="text"
                  placeholder="Verification / review note..."
                  value={noteByApp[app.id] || ''}
                  onChange={(e) => setNoteByApp((prev) => ({ ...prev, [app.id]: e.target.value }))}
                  className="flex-1 min-w-[200px] px-3 py-2 rounded-xl border text-xs"
                  style={{ borderColor: 'var(--saathi-outline)' }}
                />
                <button
                  type="button"
                  onClick={() => void handleDecision(app.id, 'APPROVED')}
                  className="saathi-btn-primary px-3.5 py-2 rounded-xl text-xs font-bold cursor-pointer"
                >
                  Approve
                </button>
                <button
                  type="button"
                  onClick={() => void handleDecision(app.id, 'REJECTED')}
                  className="px-3.5 py-2 rounded-xl text-xs font-bold border cursor-pointer"
                  style={{ borderColor: 'var(--saathi-error)', color: 'var(--saathi-error)' }}
                >
                  Reject
                </button>
              </div>
            )}
          </div>
        ))}
      </section>

      {canReview && (
        <section className="saathi-card p-5 space-y-3">
          <h2 className="text-lg font-bold">Security Audit Trail (PII Redacted)</h2>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b" style={{ borderColor: 'var(--saathi-surface-variant)' }}>
                  <th className="py-2 pr-3">Time</th>
                  <th className="py-2 pr-3">Role</th>
                  <th className="py-2 pr-3">Action</th>
                  <th className="py-2 pr-3">Details</th>
                </tr>
              </thead>
              <tbody>
                {auditLogs.map((log) => (
                  <tr
                    key={log.id}
                    className="border-b"
                    style={{ borderColor: 'var(--saathi-surface-variant)' }}
                  >
                    <td className="py-2 pr-3 font-mono">{log.created_at.slice(0, 19)}</td>
                    <td className="py-2 pr-3 font-bold">{log.actor_role}</td>
                    <td className="py-2 pr-3">{log.action}</td>
                    <td className="py-2 pr-3">{log.details}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
