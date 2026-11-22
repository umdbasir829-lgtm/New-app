import React, { useState } from 'react';
import { KeyRound, Lock, ShieldCheck, CheckCircle2 } from 'lucide-react';
import type { VaultState } from '../types';

interface PasswordSettingsTabProps {
  state: VaultState;
  onStateChange: (next: VaultState) => void;
  onLockVaultNow: () => void;
}

export function PasswordSettingsTab({
  state,
  onStateChange,
  onLockVaultNow,
}: PasswordSettingsTabProps) {
  const [currentVaultPassword, setCurrentVaultPassword] = useState('');
  const [newVaultPassword, setNewVaultPassword] = useState('');
  const [newAppLockPassword, setNewAppLockPassword] = useState('');
  const [separateAppLockEnabled, setSeparateAppLockEnabled] = useState(
    state.separateAppLockEnabled
  );
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<{ type: 'ok' | 'err'; text: string } | null>(null);

  const handleSave = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage(null);
    if (!currentVaultPassword) {
      setMessage({ type: 'err', text: 'পরিবর্তন সেভ করতে বর্তমান ভল্ট পাসওয়ার্ড লিখুন।' });
      return;
    }

    setSaving(true);
    try {
      const res = await fetch('/api/auth/update-passwords', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          currentVaultPassword,
          newVaultPassword: newVaultPassword || undefined,
          newAppLockPassword: newAppLockPassword || undefined,
          separateAppLockEnabled,
        }),
      });
      const data = await res.json();
      if (!res.ok) {
        setMessage({ type: 'err', text: data.error || 'আপডেট ব্যর্থ হয়েছে।' });
        return;
      }
      onStateChange(data.state);
      setCurrentVaultPassword('');
      setNewVaultPassword('');
      setNewAppLockPassword('');
      setMessage({
        type: 'ok',
        text: 'পাসওয়ার্ড ও নিরাপত্তা নীতি সফলভাবে আপডেট হয়েছে — ফোনেও সিঙ্ক হবে।',
      });
    } catch {
      setMessage({ type: 'err', text: 'সার্ভারের সাথে সংযোগ করা যায়নি।' });
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <div className="lg:col-span-7 bg-white border border-slate-200 rounded-2xl p-6 sm:p-8">
        <div className="flex items-center justify-between gap-4 pb-6 border-b border-slate-200">
          <div>
            <h2 className="font-display text-lg font-bold text-slate-900">
              ডুয়াল পাসওয়ার্ড নিয়ন্ত্রণ
            </h2>
            <p className="text-sm text-slate-500 mt-0.5">
              অ্যাপের ভেতরে প্রবেশ ও মোবাইলের লক করা অ্যাপ খোলার পাসওয়ার্ড আলাদা রাখুন।
            </p>
          </div>
          <button
            type="button"
            onClick={onLockVaultNow}
            className="h-11 px-4 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0"
          >
            <Lock className="w-3.5 h-3.5" />
            <span>এখনই ভল্ট লক করুন</span>
          </button>
        </div>

        <form onSubmit={handleSave} className="mt-6 space-y-5">
          <div className="flex items-center justify-between gap-4 p-4 rounded-xl bg-slate-50 border border-slate-200">
            <div>
              <p className="text-sm font-semibold text-slate-900">
                আলাদা অ্যাপ-লক পাসওয়ার্ড সক্রিয় রাখুন
              </p>
              <p className="text-xs text-slate-500 mt-0.5">
                {separateAppLockEnabled
                  ? 'চালু আছে: লক করা মোবাইল অ্যাপ খুলতে আলাদা অ্যাপ-লক পাসওয়ার্ড লাগবে।'
                  : 'বন্ধ আছে: ভল্ট পাসওয়ার্ড দিয়েই লক করা অ্যাপ আনলক হবে।'}
              </p>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={separateAppLockEnabled}
              onClick={() => setSeparateAppLockEnabled(!separateAppLockEnabled)}
              className={`relative inline-flex h-7 w-12 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors ${
                separateAppLockEnabled ? 'bg-blue-600' : 'bg-slate-300'
              }`}
            >
              <span
                className={`pointer-events-none inline-block h-6 w-6 transform rounded-full bg-white shadow transition ${
                  separateAppLockEnabled ? 'translate-x-5' : 'translate-x-0'
                }`}
              />
            </button>
          </div>

          <div>
            <label className="block text-sm font-semibold text-slate-700 mb-1.5">
              বর্তমান ভল্ট মাস্টার পাসওয়ার্ড (যাচাইয়ের জন্য আবশ্যক)
            </label>
            <input
              type="password"
              value={currentVaultPassword}
              onChange={(e) => setCurrentVaultPassword(e.target.value)}
              placeholder="বর্তমান ভল্ট পাসওয়ার্ড লিখুন"
              className="w-full h-11 px-4 rounded-xl border border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
            />
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">
                নতুন ভল্ট পাসওয়ার্ড (ঐচ্ছিক)
              </label>
              <input
                type="password"
                value={newVaultPassword}
                onChange={(e) => setNewVaultPassword(e.target.value)}
                placeholder="পরিবর্তন করতে চাইলে লিখুন"
                className="w-full h-11 px-4 rounded-xl border border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600"
              />
            </div>

            <div>
              <label className="block text-sm font-semibold text-slate-700 mb-1.5">
                নতুন অ্যাপ-লক পাসওয়ার্ড (ঐচ্ছিক)
              </label>
              <input
                type="password"
                value={newAppLockPassword}
                onChange={(e) => setNewAppLockPassword(e.target.value)}
                disabled={!separateAppLockEnabled}
                placeholder={
                  separateAppLockEnabled
                    ? 'লক করা অ্যাপের আলাদা পাসওয়ার্ড'
                    : 'আলাদা পাসওয়ার্ড বন্ধ করা আছে'
                }
                className="w-full h-11 px-4 rounded-xl border border-slate-300 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 disabled:bg-slate-100 disabled:text-slate-400"
              />
            </div>
          </div>

          {message && (
            <div
              className={`p-3.5 rounded-xl text-sm font-medium flex items-center gap-2 border ${
                message.type === 'ok'
                  ? 'bg-emerald-50 text-emerald-800 border-emerald-200'
                  : 'bg-rose-50 text-rose-700 border-rose-200'
              }`}
            >
              {message.type === 'ok' && <CheckCircle2 className="w-4 h-4 shrink-0" />}
              <span>{message.text}</span>
            </div>
          )}

          <button
            type="submit"
            disabled={saving}
            className="h-12 px-6 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold transition-colors disabled:opacity-60"
          >
            {saving ? 'সেভ হচ্ছে...' : 'পাসওয়ার্ড সেটিংস সেভ করুন'}
          </button>
        </form>
      </div>

      <div className="lg:col-span-5 space-y-4">
        <div className="bg-white border border-slate-200 rounded-2xl p-6 space-y-4">
          <div className="w-10 h-10 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center">
            <KeyRound className="w-5 h-5" />
          </div>
          <h3 className="font-display text-base font-bold text-slate-900">
            দুটি আলাদা পাসওয়ার্ড কীভাবে কাজ করে?
          </h3>
          <ul className="space-y-3 text-sm text-slate-600 leading-relaxed">
            <li>
              <strong className="text-slate-900">১. ভল্ট মাস্টার পাসওয়ার্ড:</strong> ShieldGuard অ্যাপ খোলার সময়, নতুন অ্যাপ লক/লিমিট সেট করার সময় এবং কোনো অ্যাপের দৈনিক সময়সীমা শেষ হয়ে গেলে সেটি ওভাররাইড করার জন্য প্রয়োজন হয়।
            </li>
            <li>
              <strong className="text-slate-900">২. মোবাইল অ্যাপ-লক পাসওয়ার্ড:</strong> আপনি যদি বন্ধুকে বা বাচ্চাকে ফোন দেন এবং লক করা WhatsApp বা Gallery খুলতে আলাদা পিন দিতে চান, তখন শুধু এই আলাদা পাসওয়ার্ডটি দিয়েই লক করা অ্যাপ আনলক হবে — কিন্তু তারা ShieldGuard সেটিংস খুলতে পারবে না।
            </li>
          </ul>
        </div>

        <div className="bg-white border border-slate-200 rounded-2xl p-6 flex items-start gap-3.5">
          <ShieldCheck className="w-5 h-5 text-emerald-600 shrink-0 mt-0.5" />
          <div className="text-xs text-slate-600 leading-relaxed">
            <p className="font-semibold text-slate-900 text-sm mb-1">
              এনক্রিপ্টেড স্টোরেজ (AES-256 & Scrypt)
            </p>
            ওয়েব সার্ভারে পাসওয়ার্ড <span className="font-mono-tabular">crypto.scryptSync</span> সল্টেড হ্যাশ হিসেবে এবং Android APK-তে <span className="font-mono-tabular">EncryptedSharedPreferences (AES256-GCM)</span> দিয়ে সংরক্ষিত থাকে।
          </div>
        </div>
      </div>
    </div>
  );
}
