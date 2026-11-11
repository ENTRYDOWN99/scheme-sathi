'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { Mic, Send, Volume2, ShieldAlert } from 'lucide-react';
import { useSaathi } from '@/context/SaathiProvider';
import { apiClient } from '@/lib/api';

interface Message {
  id: string;
  sender: 'user' | 'assistant';
  text: string;
  meta?: string;
  blockedBySafety?: boolean;
}

export default function ChatPage() {
  const { user, language, t } = useSaathi();
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'welcome',
      sender: 'assistant',
      text:
        language === 'hi'
          ? 'नमस्ते! मैं स्कीम साथी सहायक हूँ। मुझसे PM-KISAN, वृद्धावस्था पेंशन, उज्ज्वला या आयुष्मान भारत के बारे में पूछें। (आधार नंबर साझा न करें)'
          : 'Namaste! Ask me about PM-KISAN, Old Age Pension, Ujjwala, PM-JAY, or Atal Pension Yojana. Never enter an Aadhaar number.'
    }
  ]);
  const [prompt, setPrompt] = useState('');
  const [sending, setSending] = useState(false);
  const [listening, setListening] = useState(false);

  if (!user) {
    return (
      <div className="saathi-card p-6 text-center space-y-3">
        <p className="font-semibold">
          {language === 'hi'
            ? 'AI सहायक का उपयोग करने के लिए साइन इन करें।'
            : 'Please sign in to chat with grounded scheme guidance.'}
        </p>
        <Link href="/login" className="saathi-btn-primary inline-flex items-center px-5 py-2.5 rounded-xl font-bold text-sm">
          {t.nav.login}
        </Link>
      </div>
    );
  }

  const sendMessage = async (textToSend?: string) => {
    const clean = (textToSend ?? prompt).trim();
    if (!clean || sending) return;
    setPrompt('');
    setMessages((prev) => [...prev, { id: `u_${Date.now()}`, sender: 'user', text: clean }]);
    setSending(true);
    try {
      const res = await apiClient.chatAi(clean, language);
      setMessages((prev) => [
        ...prev,
        {
          id: `a_${Date.now()}`,
          sender: 'assistant',
          text: res.reply,
          meta: res.errorCategory ? t.aiErrors[res.errorCategory] : res.modelUsed,
          blockedBySafety: res.blockedBySafety
        }
      ]);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          id: `e_${Date.now()}`,
          sender: 'assistant',
          text: err instanceof Error ? err.message : t.aiErrors.generic
        }
      ]);
    } finally {
      setSending(false);
    }
  };

  const speakText = (text: string) => {
    if (typeof window === 'undefined' || !('speechSynthesis' in window)) return;
    window.speechSynthesis.cancel();
    const utter = new SpeechSynthesisUtterance(text);
    utter.lang = language === 'hi' ? 'hi-IN' : 'en-IN';
    window.speechSynthesis.speak(utter);
  };

  const startVoiceInput = () => {
    const win = window as unknown as {
      SpeechRecognition?: new () => {
        lang: string;
        onresult: (e: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void;
        onend: () => void;
        start: () => void;
      };
      webkitSpeechRecognition?: new () => {
        lang: string;
        onresult: (e: { results: ArrayLike<ArrayLike<{ transcript: string }>> }) => void;
        onend: () => void;
        start: () => void;
      };
    };
    const SpeechRec = win.SpeechRecognition || win.webkitSpeechRecognition;
    if (!SpeechRec) return;
    const recognition = new SpeechRec();
    recognition.lang = language === 'hi' ? 'hi-IN' : 'en-IN';
    setListening(true);
    recognition.onresult = (event) => {
      const spoken = event.results[0]?.[0]?.transcript || '';
      setPrompt(spoken);
    };
    recognition.onend = () => setListening(false);
    recognition.start();
  };

  return (
    <div className="max-w-3xl mx-auto space-y-4">
      <div className="saathi-card p-4 flex items-center justify-between gap-3">
        <div>
          <h1 className="text-lg font-bold">{t.nav.chat}</h1>
          <p className="text-xs" style={{ color: 'var(--saathi-on-surface-muted)' }}>
            {t.noAadhaarNotice}
          </p>
        </div>
        <button
          type="button"
          onClick={() =>
            void sendMessage(
              language === 'hi'
                ? 'मेरी प्रोफ़ाइल के अनुसार कौन-सी योजनाएं पात्र हैं?'
                : 'Which schemes am I eligible for based on my profile?'
            )
          }
          className="px-3 py-2 rounded-xl text-xs font-bold border cursor-pointer"
          style={{ borderColor: 'var(--saathi-outline)' }}
        >
          {language === 'hi' ? 'पात्रता पूछें' : 'Check My Summary'}
        </button>
      </div>

      <div className="saathi-card p-4 space-y-3 min-h-[360px] max-h-[55vh] overflow-y-auto">
        {messages.map((m) => (
          <div
            key={m.id}
            className={`flex ${m.sender === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className="max-w-[85%] rounded-2xl p-3.5 text-sm space-y-1.5"
              style={{
                backgroundColor:
                  m.sender === 'user'
                    ? 'var(--saathi-primary)'
                    : m.blockedBySafety
                      ? 'var(--saathi-not-eligible-bg)'
                      : 'var(--saathi-surface-variant)',
                color:
                  m.sender === 'user'
                    ? 'var(--saathi-on-primary)'
                    : m.blockedBySafety
                      ? 'var(--saathi-not-eligible-fg)'
                      : 'var(--saathi-on-surface)'
              }}
            >
              {m.blockedBySafety && (
                <div className="flex items-center gap-1 text-xs font-bold">
                  <ShieldAlert className="w-4 h-4" />
                  <span>Safety Policy</span>
                </div>
              )}
              <p className="whitespace-pre-wrap">{m.text}</p>
              {m.sender === 'assistant' && (
                <div className="flex items-center justify-between gap-2 pt-1 text-[11px] opacity-75">
                  <span>{m.meta || ''}</span>
                  <button
                    type="button"
                    onClick={() => speakText(m.text)}
                    aria-label="Listen"
                    className="inline-flex items-center gap-1 font-semibold cursor-pointer"
                  >
                    <Volume2 className="w-3.5 h-3.5" />
                    <span>{language === 'hi' ? 'सुनें' : 'Speak'}</span>
                  </button>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>

      <form
        onSubmit={(e) => {
          e.preventDefault();
          void sendMessage();
        }}
        className="flex gap-2"
      >
        <input
          type="text"
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder={
            language === 'hi'
              ? 'अपना प्रश्न लिखें या माइक दबाएं...'
              : 'Type your scheme question...'
          }
          className="flex-1 px-4 py-3 rounded-xl border text-sm"
          style={{
            backgroundColor: 'var(--saathi-surface)',
            borderColor: 'var(--saathi-outline)'
          }}
        />
        <button
          type="button"
          onClick={startVoiceInput}
          aria-label="Voice input"
          className="px-3.5 py-3 rounded-xl border cursor-pointer"
          style={{
            borderColor: 'var(--saathi-outline)',
            backgroundColor: listening ? 'var(--saathi-secondary-container)' : 'var(--saathi-surface)'
          }}
        >
          <Mic className="w-5 h-5" />
        </button>
        <button
          type="submit"
          disabled={sending}
          className="saathi-btn-primary px-5 py-3 rounded-xl text-sm font-bold inline-flex items-center gap-1.5 cursor-pointer"
        >
          <Send className="w-4 h-4" />
          <span>{sending ? '...' : language === 'hi' ? 'भेजें' : 'Send'}</span>
        </button>
      </form>
    </div>
  );
}
