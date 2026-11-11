'use client';

import React from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  Bell,
  ClipboardCheck,
  Home,
  LogIn,
  MessageSquare,
  Settings,
  ShieldCheck,
  UserCheck
} from 'lucide-react';
import { useSaathi } from '@/context/SaathiProvider';

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const { user, language, setLanguage, t, tasks, signOut } = useSaathi();

  const unreadAlerts = tasks.filter((item) => item.isNotificationUnread).length;
  const isStaff =
    user && (user.role === 'CSC_OPERATOR' || user.role === 'SUPERVISOR' || user.role === 'REVIEWER');

  const navItems = [
    { href: '/', label: t.nav.home, icon: Home },
    { href: '/chat', label: t.nav.chat, icon: MessageSquare },
    { href: '/apply', label: t.nav.apply, icon: ClipboardCheck },
    { href: '/alerts', label: t.nav.alerts, icon: Bell, badge: unreadAlerts },
    { href: '/settings', label: t.nav.settings, icon: Settings }
  ];

  return (
    <div className="min-h-screen flex flex-col">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:px-4 focus:py-2 focus:rounded-lg focus:bg-[var(--saathi-primary)] focus:text-[var(--saathi-on-primary)]"
      >
        Skip to main content
      </a>

      {/* Non-official safety disclaimer strip */}
      <div
        role="note"
        className="px-4 py-2 text-xs md:text-sm font-medium text-center"
        style={{
          backgroundColor: 'var(--saathi-secondary-container)',
          color: 'var(--saathi-on-secondary-container)'
        }}
      >
        {t.disclaimer}
      </div>

      {/* Top Navigation Header */}
      <header
        className="sticky top-0 z-30 border-b backdrop-blur"
        style={{
          backgroundColor: 'color-mix(in srgb, var(--saathi-surface) 92%, transparent)',
          borderColor: 'color-mix(in srgb, var(--saathi-outline) 25%, transparent)'
        }}
      >
        <div className="max-w-6xl mx-auto px-4 h-16 flex items-center justify-between gap-3">
          <Link href="/" className="flex items-center gap-2.5 font-bold text-lg">
            <span
              className="w-9 h-9 rounded-xl flex items-center justify-center text-white text-base font-extrabold"
              style={{ backgroundColor: 'var(--saathi-primary)' }}
            >
              सा
            </span>
            <span className="truncate">{t.appName}</span>
          </Link>

          <nav aria-label="Primary navigation" className="hidden md:flex items-center gap-1">
            {navItems.map((item) => {
              const active = pathname === item.href;
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className="relative flex items-center gap-1.5 px-3 py-2 rounded-xl text-sm font-semibold transition-colors"
                  style={{
                    backgroundColor: active ? 'var(--saathi-primary-container)' : 'transparent',
                    color: active ? 'var(--saathi-on-primary-container)' : 'var(--saathi-on-surface)'
                  }}
                >
                  <Icon className="w-4 h-4" aria-hidden="true" />
                  <span>{item.label}</span>
                  {item.badge ? (
                    <span
                      className="ml-1 px-1.5 py-0.5 text-[11px] rounded-full font-bold text-white"
                      style={{ backgroundColor: 'var(--saathi-secondary)' }}
                    >
                      {item.badge}
                    </span>
                  ) : null}
                </Link>
              );
            })}
            {user && (
              <Link
                href="/profile"
                className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-sm font-semibold"
                style={{
                  backgroundColor:
                    pathname === '/profile' ? 'var(--saathi-primary-container)' : 'transparent',
                  color:
                    pathname === '/profile'
                      ? 'var(--saathi-on-primary-container)'
                      : 'var(--saathi-on-surface)'
                }}
              >
                <UserCheck className="w-4 h-4" aria-hidden="true" />
                <span>{t.nav.profile}</span>
              </Link>
            )}
            {isStaff && (
              <Link
                href="/operator"
                className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-sm font-semibold"
                style={{
                  backgroundColor:
                    pathname === '/operator' ? 'var(--saathi-secondary-container)' : 'transparent',
                  color:
                    pathname === '/operator'
                      ? 'var(--saathi-on-secondary-container)'
                      : 'var(--saathi-on-surface)'
                }}
              >
                <ShieldCheck className="w-4 h-4" aria-hidden="true" />
                <span>{t.nav.operator}</span>
              </Link>
            )}
          </nav>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={() => setLanguage(language === 'hi' ? 'en' : 'hi')}
              aria-label="Toggle language Hindi or English"
              className="px-3 py-2 rounded-xl text-xs font-bold border cursor-pointer"
              style={{
                borderColor: 'var(--saathi-outline)',
                backgroundColor: 'var(--saathi-surface-variant)'
              }}
            >
              {language === 'hi' ? 'EN' : 'हिन्दी'}
            </button>

            {user ? (
              <div className="flex items-center gap-2">
                <span
                  data-testid="active-role-badge"
                  className="hidden sm:inline-flex items-center px-2.5 py-1 rounded-full text-xs font-bold"
                  style={{
                    backgroundColor: 'var(--saathi-primary-container)',
                    color: 'var(--saathi-on-primary-container)'
                  }}
                >
                  {t.roles[user.role]}
                </span>
                <button
                  type="button"
                  onClick={() => void signOut()}
                  className="px-3 py-2 rounded-xl text-xs font-semibold border cursor-pointer"
                  style={{ borderColor: 'var(--saathi-outline)' }}
                >
                  {language === 'hi' ? 'बाहर' : 'Sign Out'}
                </button>
              </div>
            ) : (
              <Link
                href="/login"
                className="flex items-center gap-1.5 px-3.5 py-2 rounded-xl text-xs font-bold"
                style={{
                  backgroundColor: 'var(--saathi-primary)',
                  color: 'var(--saathi-on-primary)'
                }}
              >
                <LogIn className="w-4 h-4" aria-hidden="true" />
                <span>{t.nav.login}</span>
              </Link>
            )}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main id="main-content" className="flex-1 max-w-6xl w-full mx-auto px-4 py-6 pb-24 md:pb-10">
        {children}
      </main>

      {/* Mobile Bottom 5-Tab Navigation Bar */}
      <nav
        aria-label="Mobile bottom navigation"
        className="md:hidden fixed bottom-0 inset-x-0 z-30 border-t grid grid-cols-5"
        style={{
          backgroundColor: 'var(--saathi-surface)',
          borderColor: 'color-mix(in srgb, var(--saathi-outline) 30%, transparent)'
        }}
      >
        {navItems.map((item) => {
          const active = pathname === item.href;
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              className="relative flex flex-col items-center justify-center py-2 text-[11px] font-semibold"
              style={{
                color: active ? 'var(--saathi-primary)' : 'var(--saathi-on-surface-muted)'
              }}
            >
              <Icon className="w-5 h-5 mb-0.5" aria-hidden="true" />
              <span>{item.label}</span>
              {item.badge ? (
                <span
                  className="absolute top-1 right-4 px-1.5 rounded-full text-[10px] font-bold text-white"
                  style={{ backgroundColor: 'var(--saathi-secondary)' }}
                >
                  {item.badge}
                </span>
              ) : null}
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
