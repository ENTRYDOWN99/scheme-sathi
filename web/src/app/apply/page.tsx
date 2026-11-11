'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { ShieldCheck, Copy, FileText } from 'lucide-react';
import type { SchemeApplication } from '@shared/api-client';
import { useSaathi } from '@/context/SaathiProvider';
import { apiClient } from '@/lib/api';
import { HumanConfirmationGateModal } from '@/components/HumanConfirmationGateModal';
import { OutcomeBadge } from '@/components/StatusBadges';

export default function ApplyPage() {
  const { user, schemes, profile, checklists, applications, language, t, refreshSessionAndData } =
    useSaathi();

  const [schemeId, setSchemeId] = useState('PM-KISAN');
  const [applicantName, setApplicantName] = useState('');
  const [stateDistrict, setStateDistrict] = useState('');
  const [bankIfscSummary, setBankIfscSummary] = useState('SBIN0001234');
  const [notes, setNotes] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [confirmTarget, setConfirmTarget] = useState<SchemeApplication | null>(null);
  const [copiedId, setCopiedId] = useState('');

  useEffect(() => {
    if (profile) {
      setApplicantName(profile.displayName || '');
      setStateDistrict(`${profile.state}${profile.district ? ` / ${profile.district}` : ''}`);
    }
  }, [profile]);

  if (!user) {
    return (
      <div className="saathi-card p-6 text-center space-y-3">
        <p className="font-semibold">
          {language === 'hi'
            ? 'आवेदन और CSC हैंडऑफ़ पैक के लिए साइन इन करें।'
            : 'Please sign in to create a Guided Handoff application draft.'}
        </p>
        <Link href="/login" className="saathi-btn-primary inline-flex items-center px-5 py-2.5 rounded-xl font-bold text-sm">
          {t.nav.login}
        </Link>
      </div>
    );
  }

  const schemeChecklist = checklists.filter((c) => c.schemeId === schemeId);
  const haveCount = schemeChecklist.filter((c) => c.status === 'HAVE').length;
  const missingCount = schemeChecklist.filter((c) => c.status === 'MISSING').length;
  const checklistSummary = `${haveCount} Have, ${missingCount} Missing`;

  const handleCreateDraft = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      const res = await apiClient.createApplication({
        schemeId,
        applicantName,
        stateDistrict,
        bankIfscSummary,
        checklistSummary,
        notes
      });
      await refreshSessionAndData();
      setConfirmTarget(res.application);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Could not create draft');
    } finally {
      setBusy(false);
    }
  };

  const copyPack = async (app: SchemeApplication) => {
    const pack = app.handoffPackJson || JSON.stringify(app, null, 2);
    await navigator.clipboard.writeText(pack);
    setCopiedId(app.id);
    setTimeout(() => setCopiedId(''), 2000);
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <section className="saathi-card p-6 space-y-4">
        <div className="flex items-start gap-3">
          <FileText className="w-6 h-6 shrink-0" style={{ color: 'var(--saathi-primary)' }} />
          <div>
            <h1 className="text-xl font-bold">
              {language === 'hi'
                ? 'मार्गदर्शित आवेदन ड्राफ़्ट एवं CSC हैंडऑफ़ पैक'
                : 'Guided Application Draft & Human Confirmation Gate'}
            </h1>
            <p className="text-sm" style={{ color: 'var(--saathi-on-surface-muted)' }}>
              {t.confirmationGate.subtitle}
            </p>
          </div>
        </div>

        <form onSubmit={(e) => void handleCreateDraft(e)} className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'योजना चुनें' : 'Select Scheme'}
            </label>
            <select
              value={schemeId}
              onChange={(e) => setSchemeId(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{
                backgroundColor: 'var(--saathi-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              {schemes.map((s) => (
                <option key={s.id} value={s.id}>
                  {language === 'hi' ? s.nameHi : s.nameEn}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'आवेदक का नाम' : 'Applicant Name'}
            </label>
            <input
              type="text"
              value={applicantName}
              onChange={(e) => setApplicantName(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{ borderColor: 'var(--saathi-outline)' }}
              required
            />
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'राज्य / जिला' : 'State / District'}
            </label>
            <input
              type="text"
              value={stateDistrict}
              onChange={(e) => setStateDistrict(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{ borderColor: 'var(--saathi-outline)' }}
              required
            />
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'बैंक IFSC / शाखा सारांश' : 'Bank IFSC Summary (No full account PII)'}
            </label>
            <input
              type="text"
              value={bankIfscSummary}
              onChange={(e) => setBankIfscSummary(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{ borderColor: 'var(--saathi-outline)' }}
              required
            />
          </div>

          <div className="md:col-span-2">
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'अतिरिक्त नोट्स (आधार न लिखें)' : 'Optional Notes (Do NOT enter Aadhaar)'}
            </label>
            <input
              type="text"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{ borderColor: 'var(--saathi-outline)' }}
            />
          </div>

          {error && (
            <p role="alert" className="md:col-span-2 text-sm font-semibold" style={{ color: 'var(--saathi-error)' }}>
              {error}
            </p>
          )}

          <div className="md:col-span-2">
            <button
              type="submit"
              disabled={busy}
              className="saathi-btn-primary px-5 py-3 rounded-xl text-sm font-bold cursor-pointer"
            >
              {busy
                ? '...'
                : language === 'hi'
                  ? 'ड्राफ़्ट बनाएं और पुष्टि द्वार खोलें'
                  : 'Create Draft & Open Human Confirmation Gate'}
            </button>
          </div>
        </form>
      </section>

      {/* Applications Timeline */}
      <section className="space-y-3">
        <h2 className="text-lg font-bold">
          {language === 'hi' ? 'आवेदन ट्रैकिंग और हैंडऑफ़ पैक' : 'Tracked Applications & Handoff Packs'}
        </h2>
        {applications.map((app) => (
          <article key={app.id} className="saathi-card p-5 space-y-3">
            <div className="flex flex-wrap items-start justify-between gap-2">
              <div>
                <h3 className="font-bold text-base">{app.schemeNameEn}</h3>
                <p className="text-xs" style={{ color: 'var(--saathi-on-surface-muted)' }}>
                  {app.applicantName} • {app.stateDistrict} • {app.checklistSummary}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <OutcomeBadge outcome={app.eligibilityOutcome} />
                <span
                  className="px-3 py-1 rounded-full text-xs font-bold"
                  style={{
                    backgroundColor: 'var(--saathi-surface-variant)',
                    color: 'var(--saathi-on-surface)'
                  }}
                >
                  {app.status}
                </span>
              </div>
            </div>

            {app.reviewNote && (
              <p className="text-xs font-semibold">Reviewer Note: {app.reviewNote}</p>
            )}

            <div className="flex flex-wrap gap-2">
              {!app.humanConfirmed ? (
                <button
                  type="button"
                  onClick={() => setConfirmTarget(app)}
                  className="saathi-btn-secondary px-4 py-2 rounded-xl text-xs font-bold inline-flex items-center gap-1.5 cursor-pointer"
                >
                  <ShieldCheck className="w-4 h-4" />
                  <span>{t.confirmationGate.title}</span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={() => void copyPack(app)}
                  className="px-4 py-2 rounded-xl text-xs font-bold border inline-flex items-center gap-1.5 cursor-pointer"
                  style={{ borderColor: 'var(--saathi-outline)' }}
                >
                  <Copy className="w-4 h-4" />
                  <span>
                    {copiedId === app.id
                      ? language === 'hi'
                        ? 'कॉपी हो गया!'
                        : 'Copied!'
                      : language === 'hi'
                        ? 'CSC हैंडऑफ़ पैक कॉपी करें'
                        : 'Copy Guided Handoff Pack'}
                  </span>
                </button>
              )}
            </div>
          </article>
        ))}
      </section>

      {confirmTarget && (
        <HumanConfirmationGateModal
          application={confirmTarget}
          onClose={() => setConfirmTarget(null)}
          onConfirmed={async () => {
            setConfirmTarget(null);
            await refreshSessionAndData();
          }}
        />
      )}
    </div>
  );
}
