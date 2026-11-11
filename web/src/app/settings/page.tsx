'use client';

import React, { useState } from 'react';
import { Download, Moon, Sun, Trash2, Type } from 'lucide-react';
import { useSaathi } from '@/context/SaathiProvider';
import { apiClient } from '@/lib/api';

export default function SettingsPage() {
  const {
    user,
    language,
    setLanguage,
    darkMode,
    toggleDarkMode,
    largeText,
    toggleLargeText,
    t,
    refreshSessionAndData
  } = useSaathi();

  const [statusMsg, setStatusMsg] = useState('');

  const handleExport = async () => {
    if (!user) return;
    const bundle = await apiClient.exportMyData();
    const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `schemesaathi-dpdp-export-${user.id}.json`;
    a.click();
    URL.revokeObjectURL(url);
    setStatusMsg(language === 'hi' ? 'डेटा JSON डाउनलोड हो गया।' : 'Personal data bundle downloaded.');
  };

  const handleDeleteProfile = async () => {
    if (!user) return;
    await apiClient.deleteMyProfile();
    await refreshSessionAndData();
    setStatusMsg(
      language === 'hi'
        ? 'प्रोफ़ाइल और दस्तावेज़ सूची सफलतापूर्वक मिटा दी गई।'
        : 'Profile & checklist PII erased cleanly.'
    );
  };

  return (
    <div className="max-w-2xl mx-auto space-y-5">
      <section className="saathi-card p-6 space-y-4">
        <h1 className="text-xl font-bold">{t.nav.settings}</h1>

        <div className="flex items-center justify-between py-2 border-b" style={{ borderColor: 'var(--saathi-surface-variant)' }}>
          <div>
            <div className="font-semibold text-sm">{language === 'hi' ? 'भाषा / Language' : 'Language'}</div>
            <div className="text-xs" style={{ color: 'var(--saathi-on-surface-muted)' }}>
              {language === 'hi' ? 'हिन्दी (देवनागरी)' : 'English (India)'}
            </div>
          </div>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setLanguage('hi')}
              className="px-3.5 py-2 rounded-xl text-xs font-bold border cursor-pointer"
              style={{
                backgroundColor: language === 'hi' ? 'var(--saathi-primary)' : 'transparent',
                color: language === 'hi' ? 'var(--saathi-on-primary)' : 'var(--saathi-on-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              हिन्दी
            </button>
            <button
              type="button"
              onClick={() => setLanguage('en')}
              className="px-3.5 py-2 rounded-xl text-xs font-bold border cursor-pointer"
              style={{
                backgroundColor: language === 'en' ? 'var(--saathi-primary)' : 'transparent',
                color: language === 'en' ? 'var(--saathi-on-primary)' : 'var(--saathi-on-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              English
            </button>
          </div>
        </div>

        <div className="flex items-center justify-between py-2 border-b" style={{ borderColor: 'var(--saathi-surface-variant)' }}>
          <div className="flex items-center gap-2.5">
            {darkMode ? <Moon className="w-5 h-5" /> : <Sun className="w-5 h-5" />}
            <div>
              <div className="font-semibold text-sm">
                {language === 'hi' ? 'डार्क / लाइट थीम' : 'Theme Mode (Dark / Light)'}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={toggleDarkMode}
            className="px-4 py-2 rounded-xl text-xs font-bold border cursor-pointer"
            style={{ borderColor: 'var(--saathi-outline)' }}
          >
            {darkMode ? 'Dark On' : 'Light On'}
          </button>
        </div>

        <div className="flex items-center justify-between py-2">
          <div className="flex items-center gap-2.5">
            <Type className="w-5 h-5" />
            <div>
              <div className="font-semibold text-sm">
                {language === 'hi' ? 'बड़ा टेक्स्ट मोड (सुगम्यता)' : 'Large Text Accessibility Mode'}
              </div>
            </div>
          </div>
          <button
            type="button"
            onClick={toggleLargeText}
            className="px-4 py-2 rounded-xl text-xs font-bold border cursor-pointer"
            style={{ borderColor: 'var(--saathi-outline)' }}
          >
            {largeText ? 'Large (20px)' : 'Standard (16px)'}
          </button>
        </div>
      </section>

      {user && (
        <section className="saathi-card p-6 space-y-4">
          <h2 className="text-lg font-bold">
            {language === 'hi' ? 'DPDP गोपनीयता एवं डेटा नियंत्रण' : 'DPDP Privacy & Data Controls'}
          </h2>
          <div className="flex flex-wrap gap-3">
            <button
              type="button"
              onClick={() => void handleExport()}
              className="saathi-btn-primary px-4 py-2.5 rounded-xl text-xs font-bold inline-flex items-center gap-2 cursor-pointer"
            >
              <Download className="w-4 h-4" />
              <span>{t.dpdp.exportTitle}</span>
            </button>
            <button
              type="button"
              onClick={() => void handleDeleteProfile()}
              className="px-4 py-2.5 rounded-xl text-xs font-bold border inline-flex items-center gap-2 cursor-pointer"
              style={{
                borderColor: 'var(--saathi-error)',
                color: 'var(--saathi-error)'
              }}
            >
              <Trash2 className="w-4 h-4" />
              <span>{t.dpdp.deleteTitle}</span>
            </button>
          </div>
          {statusMsg && (
            <p className="text-sm font-semibold" style={{ color: 'var(--saathi-eligible-fg)' }}>
              {statusMsg}
            </p>
          )}
        </section>
      )}
    </div>
  );
}
