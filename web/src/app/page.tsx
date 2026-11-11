'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import {
  ArrowRight,
  CheckCircle2,
  FileCheck2,
  Lock,
  Search,
  Sparkles,
  UserCheck
} from 'lucide-react';
import type { DocumentStatus, EligibilityOutcome, Role } from '@shared/api-client';
import { useSaathi } from '@/context/SaathiProvider';
import { apiClient } from '@/lib/api';
import { OutcomeBadge, DocumentStatusBadge } from '@/components/StatusBadges';

export default function HomePage() {
  const {
    user,
    language,
    t,
    schemes,
    profile,
    evaluations,
    checklists,
    demoSignIn,
    refreshSessionAndData
  } = useSaathi();

  const [query, setQuery] = useState('');
  const [filterOutcome, setFilterOutcome] = useState<'ALL' | EligibilityOutcome>('ALL');
  const [expandedSchemeId, setExpandedSchemeId] = useState<string | null>('PM-KISAN');
  const [busyRole, setBusyRole] = useState<Role | null>(null);

  const evalByScheme = useMemo(() => {
    const map = new Map<string, (typeof evaluations)[number]>();
    for (const ev of evaluations) {
      map.set(ev.schemeId, ev);
    }
    return map;
  }, [evaluations]);

  const checklistStatusMap = useMemo(() => {
    const map = new Map<string, DocumentStatus>();
    for (const item of checklists) {
      map.set(`${item.schemeId}:${item.documentId}`, item.status);
    }
    return map;
  }, [checklists]);

  const filteredSchemes = useMemo(() => {
    return schemes.filter((s) => {
      const q = query.trim().toLowerCase();
      const matchesText =
        !q ||
        s.nameEn.toLowerCase().includes(q) ||
        s.nameHi.toLowerCase().includes(q) ||
        s.category.toLowerCase().includes(q);
      if (!matchesText) return false;
      if (filterOutcome === 'ALL' || !user) return true;
      return evalByScheme.get(s.id)?.outcome === filterOutcome;
    });
  }, [schemes, query, filterOutcome, user, evalByScheme]);

  const handleSetDoc = async (schemeId: string, docId: string, status: DocumentStatus) => {
    if (!user) return;
    await apiClient.setDocumentStatus(schemeId, docId, status);
    await refreshSessionAndData();
  };

  const handleDemo = async (role: Role) => {
    setBusyRole(role);
    try {
      await demoSignIn(role);
    } finally {
      setBusyRole(null);
    }
  };

  return (
    <div className="space-y-6">
      {/* Privacy & No-Aadhaar Banner */}
      <section
        className="saathi-card p-5 flex flex-col md:flex-row md:items-center justify-between gap-4"
        style={{
          backgroundColor: 'var(--saathi-primary-container)',
          color: 'var(--saathi-on-primary-container)'
        }}
      >
        <div className="space-y-1">
          <div className="inline-flex items-center gap-2 text-xs font-bold uppercase tracking-wide">
            <Lock className="w-4 h-4" aria-hidden="true" />
            <span>{t.noAadhaarNotice}</span>
          </div>
          <h1 className="text-2xl md:text-3xl font-extrabold">
            {user
              ? language === 'hi'
                ? `नमस्ते, ${profile?.displayName || user.displayName}`
                : `Welcome, ${profile?.displayName || user.displayName}`
              : t.appName}
          </h1>
          <p className="text-sm md:text-base opacity-90">{t.tagline}</p>
        </div>

        <div className="flex flex-wrap gap-2">
          {user ? (
            <>
              <Link
                href="/profile"
                className="saathi-btn-primary px-4 py-2.5 rounded-xl text-sm font-bold inline-flex items-center gap-2"
              >
                <UserCheck className="w-4 h-4" />
                <span>{language === 'hi' ? 'प्रोफ़ाइल / DOB सुधारें' : 'Edit Profile & DOB'}</span>
              </Link>
              <Link
                href="/chat"
                className="saathi-btn-secondary px-4 py-2.5 rounded-xl text-sm font-bold inline-flex items-center gap-2"
              >
                <Sparkles className="w-4 h-4" />
                <span>{t.nav.chat}</span>
              </Link>
            </>
          ) : (
            <Link
              href="/login"
              className="saathi-btn-primary px-5 py-3 rounded-xl text-sm font-bold inline-flex items-center gap-2"
            >
              <span>{language === 'hi' ? 'लॉग इन / OTP से शुरू करें' : 'Sign In / OTP Access'}</span>
              <ArrowRight className="w-4 h-4" />
            </Link>
          )}
        </div>
      </section>

      {/* Role Demo Quick Access when signed out */}
      {!user && (
        <section className="saathi-card p-5 space-y-3">
          <h2 className="text-lg font-bold">
            {language === 'hi'
              ? 'तुरंत भूमिका-आधारित डेमो आज़माएं (RBAC Demo)'
              : 'Instant Role-Based Access Demo'}
          </h2>
          <p className="text-sm" style={{ color: 'var(--saathi-on-surface-muted)' }}>
            {language === 'hi'
              ? 'एक ही बैकएंड और डेटा सुरक्षा नियमों के साथ सभी 4 भूमिकाओं (Citizen, CSC Operator, Supervisor, Reviewer) का अनुभव करें।'
              : 'Test server-enforced RBAC, encrypted DOB storage, eligibility rules, and approval gates across all 4 roles.'}
          </p>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
            {(['CITIZEN', 'CSC_OPERATOR', 'REVIEWER', 'SUPERVISOR'] as Role[]).map((role) => (
              <button
                key={role}
                type="button"
                disabled={busyRole !== null}
                onClick={() => void handleDemo(role)}
                className="saathi-card p-4 text-left hover:shadow-md transition-shadow cursor-pointer flex flex-col justify-between gap-2"
              >
                <span className="text-xs font-bold uppercase" style={{ color: 'var(--saathi-primary)' }}>
                  {t.roles[role]}
                </span>
                <span className="text-sm font-semibold">
                  {busyRole === role
                    ? '...'
                    : language === 'hi'
                      ? `${t.roles[role]} के रूप में प्रवेश करें`
                      : `Sign in as ${t.roles[role]}`}
                </span>
              </button>
            ))}
          </div>
        </section>
      )}

      {/* Search and Outcome Filters */}
      <section className="flex flex-col md:flex-row gap-3 items-stretch md:items-center justify-between">
        <div className="relative flex-1">
          <Search
            className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2"
            style={{ color: 'var(--saathi-on-surface-muted)' }}
          />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={
              language === 'hi'
                ? 'योजना खोजें (जैसे PM-KISAN, पेंशन, उज्ज्वला)...'
                : 'Search schemes (PM-KISAN, pension, LPG, health)...'
            }
            className="w-full pl-10 pr-4 py-3 rounded-xl border text-sm"
            style={{
              backgroundColor: 'var(--saathi-surface)',
              borderColor: 'var(--saathi-outline)'
            }}
          />
        </div>

        {user && (
          <div className="flex flex-wrap gap-2" role="group" aria-label="Filter by outcome">
            {(['ALL', 'ELIGIBLE', 'UNKNOWN', 'NOT_ELIGIBLE'] as const).map((status) => (
              <button
                key={status}
                type="button"
                onClick={() => setFilterOutcome(status)}
                className="px-3.5 py-2 rounded-xl text-xs font-bold border cursor-pointer"
                style={{
                  backgroundColor:
                    filterOutcome === status ? 'var(--saathi-primary)' : 'var(--saathi-surface)',
                  color:
                    filterOutcome === status
                      ? 'var(--saathi-on-primary)'
                      : 'var(--saathi-on-surface)',
                  borderColor: 'var(--saathi-outline)'
                }}
              >
                {status === 'ALL' ? (language === 'hi' ? 'सभी' : 'All') : t.outcomes[status]}
              </button>
            ))}
          </div>
        )}
      </section>

      {/* Schemes & Persistent Document Checklists */}
      <section className="space-y-4">
        {filteredSchemes.map((scheme) => {
          const ev = evalByScheme.get(scheme.id);
          const isOpen = expandedSchemeId === scheme.id;
          return (
            <article key={scheme.id} className="saathi-card p-5 space-y-4">
              <div className="flex flex-col md:flex-row md:items-start justify-between gap-3">
                <div className="space-y-1">
                  <span
                    className="text-xs font-bold uppercase tracking-wide"
                    style={{ color: 'var(--saathi-secondary)' }}
                  >
                    {scheme.category} • {scheme.ruleVersion}
                  </span>
                  <h2 className="text-lg md:text-xl font-bold">
                    {language === 'hi' ? scheme.nameHi : scheme.nameEn}
                  </h2>
                  <p className="text-sm" style={{ color: 'var(--saathi-on-surface-muted)' }}>
                    {language === 'hi' ? scheme.benefitSummaryHi : scheme.benefitSummaryEn}
                  </p>
                </div>

                <div className="flex flex-wrap items-center gap-2 shrink-0">
                  {ev ? <OutcomeBadge outcome={ev.outcome} /> : null}
                  <button
                    type="button"
                    onClick={() => setExpandedSchemeId(isOpen ? null : scheme.id)}
                    className="px-3.5 py-2 rounded-xl text-xs font-bold border cursor-pointer"
                    style={{ borderColor: 'var(--saathi-outline)' }}
                  >
                    {isOpen
                      ? language === 'hi'
                        ? 'छुपाएं'
                        : 'Hide Details'
                      : language === 'hi'
                        ? 'नियम और दस्तावेज़ देखें'
                        : 'Rules & Documents'}
                  </button>
                </div>
              </div>

              {isOpen && (
                <div className="pt-3 border-t space-y-4" style={{ borderColor: 'var(--saathi-surface-variant)' }}>
                  {ev?.stateOverrideApplied && (
                    <div
                      className="px-3 py-2 rounded-xl text-xs font-semibold"
                      style={{
                        backgroundColor: 'var(--saathi-unknown-bg)',
                        color: 'var(--saathi-unknown-fg)'
                      }}
                    >
                      {ev.stateOverrideApplied}
                    </div>
                  )}

                  {ev && ev.conditionResults.length > 0 && (
                    <div className="space-y-2">
                      <h3 className="text-sm font-bold flex items-center gap-1.5">
                        <CheckCircle2 className="w-4 h-4" />
                        <span>{language === 'hi' ? 'नियम जांच परिणाम' : 'Deterministic Rule Trace'}</span>
                      </h3>
                      <ul className="space-y-1.5 text-sm">
                        {ev.conditionResults.map((c) => (
                          <li
                            key={c.conditionId}
                            className="p-2.5 rounded-xl flex items-start justify-between gap-3"
                            style={{ backgroundColor: 'var(--saathi-surface-variant)' }}
                          >
                            <span>{language === 'hi' ? c.reasonHi : c.reasonEn}</span>
                            <OutcomeBadge outcome={c.outcome} />
                          </li>
                        ))}
                      </ul>
                    </div>
                  )}

                  {/* Document Checklist (No AI upload needed, manual persisted Have / Missing / Unknown) */}
                  <div className="space-y-2">
                    <h3 className="text-sm font-bold flex items-center gap-1.5">
                      <FileCheck2 className="w-4 h-4" />
                      <span>
                        {language === 'hi'
                          ? 'दस्तावेज़ जांच सूची (डेटाबेस में सुरक्षित)'
                          : 'Document Readiness Checklist (Saved to Profile)'}
                      </span>
                    </h3>
                    <div className="space-y-2">
                      {scheme.requiredDocuments.map((doc) => {
                        const status =
                          checklistStatusMap.get(`${scheme.id}:${doc.id}`) ?? 'UNKNOWN';
                        return (
                          <div
                            key={doc.id}
                            className="p-3 rounded-xl border flex flex-col lg:flex-row lg:items-center justify-between gap-3"
                            style={{ borderColor: 'var(--saathi-surface-variant)' }}
                          >
                            <div className="space-y-0.5">
                              <div className="flex flex-wrap items-center gap-2">
                                <span className="font-semibold text-sm">
                                  {language === 'hi' ? doc.nameHi : doc.nameEn}
                                </span>
                                <DocumentStatusBadge status={status} />
                              </div>
                              <p className="text-xs" style={{ color: 'var(--saathi-on-surface-muted)' }}>
                                {language === 'hi' ? doc.howToObtainHi : doc.howToObtainEn}
                              </p>
                            </div>

                            {user && (
                              <div className="flex items-center gap-1.5 shrink-0">
                                {(['HAVE', 'MISSING', 'UNKNOWN'] as DocumentStatus[]).map((opt) => (
                                  <button
                                    key={opt}
                                    type="button"
                                    onClick={() => void handleSetDoc(scheme.id, doc.id, opt)}
                                    className="px-3 py-1.5 rounded-lg text-xs font-bold border cursor-pointer"
                                    style={{
                                      backgroundColor:
                                        status === opt ? 'var(--saathi-primary)' : 'transparent',
                                      color:
                                        status === opt
                                          ? 'var(--saathi-on-primary)'
                                          : 'var(--saathi-on-surface)',
                                      borderColor: 'var(--saathi-outline)'
                                    }}
                                  >
                                    {t.documents[opt]}
                                  </button>
                                ))}
                              </div>
                            )}
                          </div>
                        );
                      })}
                    </div>
                  </div>

                  <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
                    <a
                      href={scheme.officialSourceUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-xs font-bold underline"
                      style={{ color: 'var(--saathi-primary)' }}
                    >
                      {scheme.officialSourceTitle} ↗
                    </a>
                    {user && (
                      <Link
                        href={`/apply?scheme=${encodeURIComponent(scheme.id)}`}
                        className="saathi-btn-primary px-4 py-2 rounded-xl text-xs font-bold inline-flex items-center gap-1.5"
                      >
                        <span>
                          {language === 'hi'
                            ? 'आवेदन व हैंडऑफ़ पैक तैयार करें'
                            : 'Prepare Guided Handoff'}
                        </span>
                        <ArrowRight className="w-4 h-4" />
                      </Link>
                    )}
                  </div>
                </div>
              )}
            </article>
          );
        })}
      </section>
    </div>
  );
}
