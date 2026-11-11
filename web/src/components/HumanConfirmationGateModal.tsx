'use client';

import React, { useState } from 'react';
import { ShieldAlert, CheckCircle2 } from 'lucide-react';
import type { SchemeApplication } from '@shared/api-client';
import { useSaathi } from '@/context/SaathiProvider';
import { apiClient } from '@/lib/api';

export function HumanConfirmationGateModal({
  application,
  onConfirmed,
  onClose
}: {
  application: SchemeApplication;
  onConfirmed: () => Promise<void>;
  onClose: () => void;
}) {
  const { t, language } = useSaathi();
  const [c1, setC1] = useState(false);
  const [c2, setC2] = useState(false);
  const [c3, setC3] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');

  const allChecked = c1 && c2 && c3;

  const handleConfirm = async () => {
    if (!allChecked) return;
    setSubmitting(true);
    setError('');
    try {
      await apiClient.confirmApplication(application.id, application.applicantSnapshotHash);
      await onConfirmed();
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Confirmation failed');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      role="dialog"
      aria-modal="true"
      aria-labelledby="gate-title"
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60"
    >
      <div className="saathi-card max-w-lg w-full p-6 space-y-4 shadow-2xl">
        <div className="flex items-start gap-3">
          <ShieldAlert className="w-7 h-7 shrink-0" style={{ color: 'var(--saathi-secondary)' }} />
          <div>
            <h2 id="gate-title" className="text-lg font-bold">
              {t.confirmationGate.title}
            </h2>
            <p className="text-sm" style={{ color: 'var(--saathi-on-surface-muted)' }}>
              {t.confirmationGate.subtitle}
            </p>
          </div>
        </div>

        <div
          className="p-3 rounded-xl text-sm space-y-1"
          style={{ backgroundColor: 'var(--saathi-surface-variant)' }}
        >
          <p>
            <strong>Scheme:</strong> {application.schemeNameEn}
          </p>
          <p>
            <strong>Applicant:</strong> {application.applicantName} ({application.stateDistrict})
          </p>
          <p>
            <strong>Bank/IFSC:</strong> {application.bankIfscSummary}
          </p>
          <p>
            <strong>Checklist:</strong> {application.checklistSummary}
          </p>
          <p className="text-xs font-mono break-all pt-1">
            <strong>Snapshot SHA-256:</strong> {application.applicantSnapshotHash}
          </p>
        </div>

        <div className="space-y-3 text-sm">
          <label className="flex items-start gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={c1}
              onChange={(e) => setC1(e.target.checked)}
              className="mt-1 w-5 h-5"
            />
            <span>{t.confirmationGate.checkReviewed}</span>
          </label>
          <label className="flex items-start gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={c2}
              onChange={(e) => setC2(e.target.checked)}
              className="mt-1 w-5 h-5"
            />
            <span>{t.confirmationGate.checkOfficialPortal}</span>
          </label>
          <label className="flex items-start gap-2.5 cursor-pointer">
            <input
              type="checkbox"
              checked={c3}
              onChange={(e) => setC3(e.target.checked)}
              className="mt-1 w-5 h-5"
            />
            <span>{t.confirmationGate.checkHuman}</span>
          </label>
        </div>

        {error && (
          <p role="alert" className="text-sm font-semibold" style={{ color: 'var(--saathi-error)' }}>
            {error}
          </p>
        )}

        <div className="flex flex-wrap justify-end gap-3 pt-2">
          <button
            type="button"
            onClick={onClose}
            className="px-4 py-2.5 rounded-xl border text-sm font-semibold cursor-pointer"
            style={{ borderColor: 'var(--saathi-outline)' }}
          >
            {language === 'hi' ? 'रद्द करें' : 'Cancel'}
          </button>
          <button
            type="button"
            disabled={!allChecked || submitting}
            onClick={() => void handleConfirm()}
            className="saathi-btn-primary px-5 py-2.5 rounded-xl text-sm font-bold flex items-center gap-2 disabled:opacity-40 cursor-pointer"
          >
            <CheckCircle2 className="w-4 h-4" />
            <span>{submitting ? '...' : t.confirmationGate.submitButton}</span>
          </button>
        </div>
      </div>
    </div>
  );
}
