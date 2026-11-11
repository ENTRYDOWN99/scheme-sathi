'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { KeyRound, Phone, Shield } from 'lucide-react';
import type { Role } from '@shared/api-client';
import { useSaathi } from '@/context/SaathiProvider';
import { apiClient } from '@/lib/api';

export default function LoginPage() {
  const router = useRouter();
  const { language, t, demoSignIn, refreshSessionAndData } = useSaathi();

  const [mode, setMode] = useState<'otp' | 'password' | 'register'>('otp');
  const [identifier, setIdentifier] = useState('+919800000001');
  const [otpCode, setOtpCode] = useState('');
  const [devOtpHint, setDevOtpHint] = useState('');
  const [otpSent, setOtpSent] = useState(false);

  const [password, setPassword] = useState('SaathiDemo@2026');
  const [displayName, setDisplayName] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const handleRequestOtp = async () => {
    setError('');
    setBusy(true);
    try {
      const res = await apiClient.requestOtp(identifier);
      setOtpSent(true);
      if (res.devOtpHint) {
        setDevOtpHint(res.devOtpHint);
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'OTP failed');
    } finally {
      setBusy(false);
    }
  };

  const handleVerifyOtp = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      await apiClient.verifyOtp(identifier, otpCode);
      await refreshSessionAndData();
      router.push('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'OTP verification failed');
    } finally {
      setBusy(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setBusy(true);
    try {
      if (mode === 'register') {
        const res = await fetch(
          `${process.env.NEXT_PUBLIC_API_URL || 'http://localhost:4000/api/v1'}/auth/register`,
          {
            method: 'POST',
            credentials: 'include',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              phone: identifier.includes('@') ? undefined : identifier,
              email: identifier.includes('@') ? identifier : undefined,
              displayName: displayName || 'Citizen User',
              password,
              preferredLanguage: language
            })
          }
        );
        if (!res.ok) {
          const data = (await res.json()) as { message?: string };
          throw new Error(data.message || 'Registration failed');
        }
      } else {
        await apiClient.login(identifier, password);
      }
      await refreshSessionAndData();
      router.push('/');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Authentication failed');
    } finally {
      setBusy(false);
    }
  };

  const handleDemoRole = async (role: Role) => {
    setError('');
    setBusy(true);
    try {
      await demoSignIn(role);
      router.push(role === 'CITIZEN' ? '/' : '/operator');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Demo sign-in failed');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="max-w-3xl mx-auto space-y-6">
      <div className="saathi-card p-6 space-y-4">
        <div className="flex flex-wrap gap-2">
          <button
            type="button"
            onClick={() => setMode('otp')}
            className="px-4 py-2 rounded-xl text-xs font-bold border cursor-pointer"
            style={{
              backgroundColor: mode === 'otp' ? 'var(--saathi-primary)' : 'transparent',
              color: mode === 'otp' ? 'var(--saathi-on-primary)' : 'var(--saathi-on-surface)',
              borderColor: 'var(--saathi-outline)'
            }}
          >
            <Phone className="w-3.5 h-3.5 inline mr-1" />
            {language === 'hi' ? 'मोबाइल OTP लॉग इन' : 'Phone / Email OTP'}
          </button>
          <button
            type="button"
            onClick={() => setMode('password')}
            className="px-4 py-2 rounded-xl text-xs font-bold border cursor-pointer"
            style={{
              backgroundColor: mode === 'password' ? 'var(--saathi-primary)' : 'transparent',
              color: mode === 'password' ? 'var(--saathi-on-primary)' : 'var(--saathi-on-surface)',
              borderColor: 'var(--saathi-outline)'
            }}
          >
            <KeyRound className="w-3.5 h-3.5 inline mr-1" />
            {language === 'hi' ? 'पासवर्ड लॉग इन' : 'Password Login'}
          </button>
          <button
            type="button"
            onClick={() => setMode('register')}
            className="px-4 py-2 rounded-xl text-xs font-bold border cursor-pointer"
            style={{
              backgroundColor: mode === 'register' ? 'var(--saathi-primary)' : 'transparent',
              color: mode === 'register' ? 'var(--saathi-on-primary)' : 'var(--saathi-on-surface)',
              borderColor: 'var(--saathi-outline)'
            }}
          >
            {language === 'hi' ? 'नया खाता' : 'Register Citizen'}
          </button>
        </div>

        {mode === 'otp' ? (
          <form onSubmit={(e) => void handleVerifyOtp(e)} className="space-y-4">
            <h1 className="text-xl font-bold">
              {language === 'hi' ? 'OTP से सुरक्षित प्रवेश' : 'Sign In with One-Time Passcode'}
            </h1>
            <div className="space-y-1">
              <label className="block text-sm font-semibold">
                {language === 'hi' ? 'मोबाइल नंबर (+91...) या ईमेल' : 'Phone (+91...) or Email'}
              </label>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={identifier}
                  onChange={(e) => setIdentifier(e.target.value)}
                  className="flex-1 px-3 py-2.5 rounded-xl border text-sm"
                  style={{ borderColor: 'var(--saathi-outline)' }}
                  required
                />
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void handleRequestOtp()}
                  className="saathi-btn-secondary px-4 py-2.5 rounded-xl text-xs font-bold cursor-pointer"
                >
                  {language === 'hi' ? 'OTP भेजें' : 'Send OTP'}
                </button>
              </div>
            </div>

            {devOtpHint && (
              <div
                className="p-3 rounded-xl text-xs font-semibold"
                style={{
                  backgroundColor: 'var(--saathi-eligible-bg)',
                  color: 'var(--saathi-eligible-fg)'
                }}
              >
                Development OTP Code: <strong>{devOtpHint}</strong>
              </div>
            )}

            {otpSent && (
              <div className="space-y-2">
                <label className="block text-sm font-semibold">
                  {language === 'hi' ? '6-अंकीय OTP दर्ज करें' : 'Enter 6-digit OTP'}
                </label>
                <input
                  type="text"
                  value={otpCode}
                  onChange={(e) => setOtpCode(e.target.value)}
                  className="w-full px-3 py-2.5 rounded-xl border text-sm"
                  style={{ borderColor: 'var(--saathi-outline)' }}
                  required
                />
                <button
                  type="submit"
                  disabled={busy}
                  className="saathi-btn-primary w-full py-3 rounded-xl text-sm font-bold cursor-pointer"
                >
                  {language === 'hi' ? 'सत्यापित करें और आगे बढ़ें' : 'Verify & Sign In'}
                </button>
              </div>
            )}
          </form>
        ) : (
          <form onSubmit={(e) => void handlePasswordSubmit(e)} className="space-y-4">
            <h1 className="text-xl font-bold">
              {mode === 'register'
                ? language === 'hi'
                  ? 'नया नागरिक पंजीकरण'
                  : 'Create Citizen Account'
                : language === 'hi'
                  ? 'ईमेल / फोन से साइन इन'
                  : 'Sign In with Password'}
            </h1>
            {mode === 'register' && (
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
            )}
            <div>
              <label className="block text-sm font-semibold mb-1">
                {language === 'hi' ? 'ईमेल या फोन नंबर' : 'Email or Phone'}
              </label>
              <input
                type="text"
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                className="w-full px-3 py-2.5 rounded-xl border text-sm"
                style={{ borderColor: 'var(--saathi-outline)' }}
                required
              />
            </div>
            <div>
              <label className="block text-sm font-semibold mb-1">
                {language === 'hi' ? 'पासवर्ड' : 'Password'}
              </label>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className="w-full px-3 py-2.5 rounded-xl border text-sm"
                style={{ borderColor: 'var(--saathi-outline)' }}
                required
              />
            </div>
            <button
              type="submit"
              disabled={busy}
              className="saathi-btn-primary w-full py-3 rounded-xl text-sm font-bold cursor-pointer"
            >
              {mode === 'register'
                ? language === 'hi'
                  ? 'पंजीकरण करें'
                  : 'Create Account'
                : language === 'hi'
                  ? 'साइन इन करें'
                  : 'Sign In'}
            </button>
          </form>
        )}

        {error && (
          <p role="alert" className="text-sm font-semibold" style={{ color: 'var(--saathi-error)' }}>
            {error}
          </p>
        )}
      </div>

      {/* Role Demo Accounts */}
      <div className="saathi-card p-6 space-y-3">
        <h2 className="text-base font-bold flex items-center gap-2">
          <Shield className="w-5 h-5" style={{ color: 'var(--saathi-primary)' }} />
          <span>
            {language === 'hi' ? 'भूमिका-वार डेमो खाते (एक क्लिक)' : 'One-Click Role Demo Access'}
          </span>
        </h2>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {(['CITIZEN', 'CSC_OPERATOR', 'REVIEWER', 'SUPERVISOR'] as Role[]).map((role) => (
            <button
              key={role}
              type="button"
              disabled={busy}
              onClick={() => void handleDemoRole(role)}
              className="p-3.5 rounded-xl border text-left hover:opacity-90 cursor-pointer"
              style={{
                borderColor: 'var(--saathi-outline)',
                backgroundColor: 'var(--saathi-surface-variant)'
              }}
            >
              <div className="text-xs font-bold uppercase" style={{ color: 'var(--saathi-primary)' }}>
                {t.roles[role]}
              </div>
              <div className="text-sm font-semibold mt-0.5">
                {language === 'hi' ? `${t.roles[role]} के रूप में लॉग इन करें` : `Continue as ${t.roles[role]}`}
              </div>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}
