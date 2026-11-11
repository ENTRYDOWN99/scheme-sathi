'use client';

import React, { useEffect, useState } from 'react';
import Link from 'next/link';
import { Calendar, CheckCircle2, Lock } from 'lucide-react';
import { useSaathi } from '@/context/SaathiProvider';
import { apiClient } from '@/lib/api';
import {
  calculateAgeFromDob,
  ddMmYyyyToIsoDate,
  formatDobWithSlashes,
  isoDateToDdMmYyyy,
  validateDob
} from '@/lib/dob';

const STATES = [
  'Uttar Pradesh',
  'Bihar',
  'Maharashtra',
  'Madhya Pradesh',
  'Rajasthan',
  'Other State / Union Territory'
];

export default function ProfilePage() {
  const { user, profile, language, t, refreshSessionAndData } = useSaathi();

  const [displayName, setDisplayName] = useState('');
  const [dobInput, setDobInput] = useState('');
  const [gender, setGender] = useState('unknown');
  const [state, setState] = useState('Uttar Pradesh');
  const [district, setDistrict] = useState('');
  const [annualIncome, setAnnualIncome] = useState('45000');
  const [bplStatus, setBplStatus] = useState('unknown');
  const [socialCategory, setSocialCategory] = useState('unknown');
  const [ownsLand, setOwnsLand] = useState('unknown');
  const [hasBank, setHasBank] = useState('unknown');
  const [isTaxPayer, setIsTaxPayer] = useState('unknown');
  const [hasLpg, setHasLpg] = useState('unknown');

  const [saving, setSaving] = useState(false);
  const [saveMessage, setSaveMessage] = useState('');
  const [errorMessage, setErrorMessage] = useState('');

  useEffect(() => {
    if (!profile) return;
    setDisplayName(profile.displayName || '');
    setDobInput(profile.dateOfBirth || '');
    setGender(profile.gender || 'unknown');
    setState(profile.state || 'Uttar Pradesh');
    setDistrict(profile.district || '');
    setAnnualIncome(profile.annualIncome >= 0 ? String(profile.annualIncome) : '');
    setBplStatus(profile.bplStatus || 'unknown');
    setSocialCategory(profile.socialCategory || 'unknown');
    setOwnsLand(profile.ownsAgriculturalLand || 'unknown');
    setHasBank(profile.hasBankAccount || 'unknown');
    setIsTaxPayer(profile.isIncomeTaxPayer || 'unknown');
    setHasLpg(profile.hasLpgConnection || 'unknown');
  }, [profile]);

  if (!user) {
    return (
      <div className="saathi-card p-6 text-center space-y-3">
        <p className="font-semibold">
          {language === 'hi'
            ? 'अपनी प्रोफ़ाइल और जन्म तिथि (DD/MM/YYYY) सहेजने के लिए कृपया साइन इन करें।'
            : 'Please sign in to manage your profile and DD/MM/YYYY date of birth.'}
        </p>
        <Link href="/login" className="saathi-btn-primary inline-flex items-center px-5 py-2.5 rounded-xl font-bold text-sm">
          {t.nav.login}
        </Link>
      </div>
    );
  }

  const dobStatus = dobInput.trim().length > 0 ? validateDob(dobInput) : 'INCOMPLETE';
  const computedAge =
    dobStatus === 'VALID'
      ? calculateAgeFromDob(dobInput)
      : profile && profile.birthYear > 0
        ? new Date().getFullYear() - profile.birthYear
        : null;

  const dobErrorText =
    dobInput.trim().length > 0 && dobStatus !== 'VALID' && dobStatus !== 'INCOMPLETE'
      ? t.dob.errors[dobStatus]
      : '';

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaveMessage('');
    setErrorMessage('');

    if (dobInput.trim().length > 0) {
      const status = validateDob(dobInput);
      if (status !== 'VALID') {
        setErrorMessage(
          status === 'INCOMPLETE' ? t.dob.errors.INVALID_FORMAT : t.dob.errors[status]
        );
        return;
      }
    }

    setSaving(true);
    try {
      await apiClient.updateProfile({
        displayName,
        dateOfBirth: dobInput.trim(),
        ageConfirmedViaVoice: true,
        gender,
        state,
        district,
        annualIncome: annualIncome.trim() ? Number.parseInt(annualIncome, 10) : -1,
        bplStatus,
        socialCategory,
        ownsAgriculturalLand: ownsLand,
        hasBankAccount: hasBank,
        isIncomeTaxPayer: isTaxPayer,
        hasLpgConnection: hasLpg,
        preferredLanguage: language
      });
      await refreshSessionAndData();
      setSaveMessage(
        language === 'hi'
          ? 'प्रोफ़ाइल और पात्रता परिणाम अपडेट हो गए!'
          : 'Profile saved and eligibility recalculated!'
      );
    } catch (err) {
      setErrorMessage(err instanceof Error ? err.message : 'Save failed');
    } finally {
      setSaving(false);
    }
  };

  return (
    <form onSubmit={(e) => void handleSave(e)} className="max-w-3xl mx-auto space-y-5">
      <div
        className="p-3.5 rounded-xl text-xs font-semibold flex items-center gap-2"
        style={{
          backgroundColor: 'var(--saathi-primary-container)',
          color: 'var(--saathi-on-primary-container)'
        }}
      >
        <Lock className="w-4 h-4 shrink-0" />
        <span>{t.noAadhaarNotice}</span>
      </div>

      <section className="saathi-card p-6 space-y-4">
        <h1 className="text-xl font-bold">
          {language === 'hi' ? 'नागरिक प्रोफ़ाइल एवं पात्रता विवरण' : 'Citizen Profile & Eligibility Details'}
        </h1>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'पूरा नाम' : 'Full Name'}
            </label>
            <input
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{ borderColor: 'var(--saathi-outline)' }}
              required
            />
          </div>

          {/* Date of Birth (DD/MM/YYYY auto-slash + native calendar sync + live age) */}
          <div>
            <div className="flex items-center justify-between mb-1">
              <label htmlFor="dob-input" className="text-sm font-semibold">
                {t.dob.label}
              </label>
              {computedAge !== null && (
                <span
                  data-testid="computed-age-badge"
                  className="text-xs font-bold px-2 py-0.5 rounded-full"
                  style={{
                    backgroundColor: 'var(--saathi-eligible-bg)',
                    color: 'var(--saathi-eligible-fg)'
                  }}
                >
                  ({t.dob.ageDisplay.replace('{{age}}', String(computedAge))})
                </span>
              )}
            </div>

            <div className="flex items-center gap-2">
              <input
                id="dob-input"
                type="text"
                inputMode="numeric"
                placeholder={t.dob.hint}
                value={dobInput}
                onChange={(e) => setDobInput(formatDobWithSlashes(e.target.value))}
                className="flex-1 px-3 py-2.5 rounded-xl border text-sm font-mono"
                style={{ borderColor: dobErrorText ? 'var(--saathi-error)' : 'var(--saathi-outline)' }}
              />
              <label
                className="px-3 py-2.5 rounded-xl border text-xs font-semibold cursor-pointer inline-flex items-center gap-1"
                style={{ borderColor: 'var(--saathi-outline)' }}
                title="Open Date Picker"
              >
                <Calendar className="w-4 h-4" />
                <input
                  type="date"
                  aria-label="Date picker"
                  value={ddMmYyyyToIsoDate(dobInput)}
                  max={new Date().toISOString().slice(0, 10)}
                  onChange={(e) => {
                    if (e.target.value) {
                      setDobInput(isoDateToDdMmYyyy(e.target.value));
                    }
                  }}
                  className="sr-only"
                />
              </label>
            </div>

            {dobErrorText && (
              <p role="alert" className="text-xs font-semibold mt-1" style={{ color: 'var(--saathi-error)' }}>
                {dobErrorText}
              </p>
            )}
            {!dobInput && profile && profile.birthYear > 0 && (
              <p className="text-xs mt-1" style={{ color: 'var(--saathi-unknown-fg)' }}>
                {t.dob.legacyNotice.replace('{{year}}', String(profile.birthYear))}
              </p>
            )}
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'राज्य (State)' : 'State'}
            </label>
            <select
              value={state}
              onChange={(e) => setState(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{
                backgroundColor: 'var(--saathi-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              {STATES.map((st) => (
                <option key={st} value={st}>
                  {st}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'जिला (District)' : 'District'}
            </label>
            <input
              type="text"
              value={district}
              onChange={(e) => setDistrict(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{ borderColor: 'var(--saathi-outline)' }}
            />
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'लिंग (Gender)' : 'Gender'}
            </label>
            <select
              value={gender}
              onChange={(e) => setGender(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{
                backgroundColor: 'var(--saathi-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              <option value="unknown">{language === 'hi' ? 'अज्ञात' : 'Unknown'}</option>
              <option value="female">{language === 'hi' ? 'महिला' : 'Female'}</option>
              <option value="male">{language === 'hi' ? 'पुरुष' : 'Male'}</option>
              <option value="other">{language === 'hi' ? 'अन्य' : 'Other'}</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'वार्षिक आय (₹)' : 'Annual Household Income (₹)'}
            </label>
            <input
              type="number"
              min="0"
              value={annualIncome}
              onChange={(e) => setAnnualIncome(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{ borderColor: 'var(--saathi-outline)' }}
            />
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'BPL / अंत्योदय कार्ड' : 'BPL / Antyodaya Status'}
            </label>
            <select
              value={bplStatus}
              onChange={(e) => setBplStatus(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{
                backgroundColor: 'var(--saathi-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              <option value="unknown">{language === 'hi' ? 'अज्ञात' : 'Unknown'}</option>
              <option value="yes">{language === 'hi' ? 'हाँ' : 'Yes'}</option>
              <option value="no">{language === 'hi' ? 'नहीं' : 'No'}</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'सामाजिक वर्ग' : 'Social Category'}
            </label>
            <select
              value={socialCategory}
              onChange={(e) => setSocialCategory(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{
                backgroundColor: 'var(--saathi-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              <option value="unknown">{language === 'hi' ? 'अज्ञात' : 'Unknown'}</option>
              <option value="general">General</option>
              <option value="obc">OBC</option>
              <option value="sc">SC</option>
              <option value="st">ST</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'कृषि भूमि स्वामित्व' : 'Owns Agricultural Land'}
            </label>
            <select
              value={ownsLand}
              onChange={(e) => setOwnsLand(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{
                backgroundColor: 'var(--saathi-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              <option value="unknown">{language === 'hi' ? 'अज्ञात' : 'Unknown'}</option>
              <option value="yes">{language === 'hi' ? 'हाँ' : 'Yes'}</option>
              <option value="no">{language === 'hi' ? 'नहीं' : 'No'}</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'सक्रिय बैंक खाता' : 'Active Bank Account'}
            </label>
            <select
              value={hasBank}
              onChange={(e) => setHasBank(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{
                backgroundColor: 'var(--saathi-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              <option value="unknown">{language === 'hi' ? 'अज्ञात' : 'Unknown'}</option>
              <option value="yes">{language === 'hi' ? 'हाँ' : 'Yes'}</option>
              <option value="no">{language === 'hi' ? 'नहीं' : 'No'}</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'क्या आयकर दाता हैं?' : 'Income Tax Payer?'}
            </label>
            <select
              value={isTaxPayer}
              onChange={(e) => setIsTaxPayer(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{
                backgroundColor: 'var(--saathi-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              <option value="unknown">{language === 'hi' ? 'अज्ञात' : 'Unknown'}</option>
              <option value="yes">{language === 'hi' ? 'हाँ' : 'Yes'}</option>
              <option value="no">{language === 'hi' ? 'नहीं' : 'No'}</option>
            </select>
          </div>

          <div>
            <label className="block text-sm font-semibold mb-1">
              {language === 'hi' ? 'घर में पहले से LPG गैस?' : 'Existing Household LPG?'}
            </label>
            <select
              value={hasLpg}
              onChange={(e) => setHasLpg(e.target.value)}
              className="w-full px-3 py-2.5 rounded-xl border text-sm"
              style={{
                backgroundColor: 'var(--saathi-surface)',
                borderColor: 'var(--saathi-outline)'
              }}
            >
              <option value="unknown">{language === 'hi' ? 'अज्ञात' : 'Unknown'}</option>
              <option value="yes">{language === 'hi' ? 'हाँ' : 'Yes'}</option>
              <option value="no">{language === 'hi' ? 'नहीं' : 'No'}</option>
            </select>
          </div>
        </div>

        {errorMessage && (
          <p role="alert" className="text-sm font-semibold" style={{ color: 'var(--saathi-error)' }}>
            {errorMessage}
          </p>
        )}
        {saveMessage && (
          <p
            className="text-sm font-semibold flex items-center gap-2"
            style={{ color: 'var(--saathi-eligible-fg)' }}
          >
            <CheckCircle2 className="w-4 h-4" />
            <span>{saveMessage}</span>
          </p>
        )}

        <button
          type="submit"
          disabled={saving}
          className="saathi-btn-primary px-6 py-3 rounded-xl text-sm font-bold cursor-pointer"
        >
          {saving
            ? '...'
            : language === 'hi'
              ? 'प्रोफ़ाइल सहेजें और पात्रता जांचें'
              : 'Save Profile & Recalculate'}
        </button>
      </section>
    </form>
  );
}
