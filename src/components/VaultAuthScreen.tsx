import React, { useState } from 'react';
import { Lock, Shield, Eye, EyeOff, KeyRound, ArrowRight } from 'lucide-react';
import type { VaultState } from '../types';

interface VaultAuthScreenProps {
  hasVaultPassword: boolean;
  onAuthenticated: (updatedState?: VaultState) => void;
}

export function VaultAuthScreen({ hasVaultPassword, onAuthenticated }: VaultAuthScreenProps) {
  const [vaultPassword, setVaultPassword] = useState('');
  const [appLockPassword, setAppLockPassword] = useState('');
  const [separateAppLockEnabled, setSeparateAppLockEnabled] = useState(true);
  const [showVaultPass, setShowVaultPass] = useState(false);
  const [showAppPass, setShowAppPass] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSetup = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (vaultPassword.length < 4) {
      setError('অ্যাপে ঢোকার ভল্ট পাসওয়ার্ড কমপক্ষে ৪ অক্ষরের দিন।');
      return;
    }
    if (separateAppLockEnabled && appLockPassword.length < 4) {
      setError('মোবাইলের লক করা অ্যাপের পাসওয়ার্ড কমপক্ষে ৪ অক্ষরের দিন।');
      return;
    }
    if (separateAppLockEnabled && vaultPassword === appLockPassword) {
      setError('আলাদা নিরাপত্তার জন্য দুটি পাসওয়ার্ড ভিন্ন দিলে ভালো হয় (অথবা নিচের টগল বন্ধ করুন)।');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch('/api/auth/setup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          vaultPassword,
          appLockPassword: separateAppLockEnabled ? appLockPassword : '',
          separateAppLockEnabled,
        }),
      });
      const data = await res.json();
      if (!res.ok) {
        setError(data.error || 'সেটআপ ব্যর্থ হয়েছে।');
        return;
      }
      onAuthenticated(data.state);
    } catch {
      setError('সার্ভারের সাথে সংযোগ করা যায়নি।');
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!vaultPassword) {
      setError('আপনার ভল্ট পাসওয়ার্ড লিখুন।');
      return;
    }

    setLoading(true);
    try {
      const res = await fetch('/api/auth/verify-vault', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ password: vaultPassword }),
      });
      const data = await res.json();
      if (!data.ok) {
        setError('ভুল ভল্ট পাসওয়ার্ড! অনুগ্রহ করে সঠিক পাসওয়ার্ড দিন।');
        return;
      }
      onAuthenticated();
    } catch {
      setError('সার্ভারের সাথে সংযোগ করা যায়নি।');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-[#f6f8fb] flex items-center justify-center p-4 sm:p-6">
      <div className="w-full max-w-4xl bg-white border border-slate-200 rounded-2xl shadow-sm overflow-hidden grid grid-cols-1 lg:grid-cols-12">
        <div className="lg:col-span-5 bg-slate-900 text-white p-8 flex flex-col justify-between">
          <div>
            <div className="w-11 h-11 rounded-xl bg-blue-600 flex items-center justify-center mb-6">
              <Shield className="w-6 h-6 text-white" />
            </div>
            <h1 className="font-display text-2xl font-bold tracking-tight text-balance">
              ShieldGuard AppLock
            </h1>
            <p className="mt-3 text-sm text-slate-300 leading-relaxed">
              মোবাইলের প্রতিটি অ্যাপ ডিটেক্ট করে লক ও দৈনিক ব্যবহারের সময়সীমা নিয়ন্ত্রণ করুন — দুটি সম্পূর্ণ আলাদা পাসওয়ার্ড স্তরের মাধ্যমে।
            </p>
          </div>

          <div className="mt-8 pt-6 border-t border-slate-800 space-y-3 text-xs text-slate-300">
            <div className="flex items-start gap-2.5">
              <KeyRound className="w-4 h-4 text-blue-400 shrink-0 mt-0.5" />
              <span>১. ভল্ট পাসওয়ার্ড: ShieldGuard অ্যাপের ভেতরে ঢোকা ও নিয়ম পরিবর্তনের জন্য।</span>
            </div>
            <div className="flex items-start gap-2.5">
              <Lock className="w-4 h-4 text-blue-400 shrink-0 mt-0.5" />
              <span>২. অ্যাপ-লক পাসওয়ার্ড: মোবাইলের লক করা অ্যাপগুলো আনলক করার জন্য আলাদা পাসওয়ার্ড।</span>
            </div>
          </div>
        </div>

        <div className="lg:col-span-7 p-6 sm:p-10 flex flex-col justify-center">
          {!hasVaultPassword ? (
            <form onSubmit={handleSetup} className="space-y-5">
              <div>
                <h2 className="font-display text-xl font-bold text-slate-900">
                  প্রাথমিক পাসওয়ার্ড সেটআপ
                </h2>
                <p className="mt-1 text-sm text-slate-500">
                  অ্যাপ ব্যবহার শুরু করতে আপনার ভল্ট ও মোবাইল অ্যাপ-লকের পাসওয়ার্ড নির্ধারণ করুন।
                </p>
              </div>

              <div>
                <label className="block text-sm font-semibold text-slate-700 mb-1.5">
                  ১. ShieldGuard অ্যাপে ঢোকার পাসওয়ার্ড (Vault Password)
                </label>
                <div className="relative">
                  <input
                    type={showVaultPass ? 'text' : 'password'}
                    value={vaultPassword}
                    onChange={(e) => setVaultPassword(e.target.value)}
                    placeholder="কমপক্ষে ৪ অক্ষর লিখুন"
                    className="w-full h-12 px-4 pr-12 rounded-xl border border-slate-300 bg-white text-slate-900 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent"
                  />
                  <button
                    type="button"
                    onClick={() => setShowVaultPass(!showVaultPass)}
                    className="absolute right-1.5 top-1/2 -translate-y-1/2 w-10 h-10 flex items-center justify-center text-slate-500 hover:text-slate-800"
                    aria-label="পাসওয়ার্ড দেখুন"
                  >
                    {showVaultPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              <div className="flex items-center justify-between gap-4 p-4 rounded-xl bg-slate-50 border border-slate-200">
                <div>
                  <p className="text-sm font-semibold text-slate-900">
                    লক করা মোবাইল অ্যাপের জন্য আলাদা পাসওয়ার্ড ব্যবহার করুন
                  </p>
                  <p className="text-xs text-slate-500 mt-0.5">
                    বন্ধ করলে ভল্ট পাসওয়ার্ড দিয়েই সব লক করা অ্যাপ খোলা যাবে।
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

              {separateAppLockEnabled && (
                <div>
                  <label className="block text-sm font-semibold text-slate-700 mb-1.5">
                    ২. মোবাইলের লক করা অ্যাপ খোলার পাসওয়ার্ড (App-Lock Password)
                  </label>
                  <div className="relative">
                    <input
                      type={showAppPass ? 'text' : 'password'}
                      value={appLockPassword}
                      onChange={(e) => setAppLockPassword(e.target.value)}
                      placeholder="ভল্ট পাসওয়ার্ড থেকে আলাদা একটি পাসওয়ার্ড দিন"
                      className="w-full h-12 px-4 pr-12 rounded-xl border border-slate-300 bg-white text-slate-900 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent"
                    />
                    <button
                      type="button"
                      onClick={() => setShowAppPass(!showAppPass)}
                      className="absolute right-1.5 top-1/2 -translate-y-1/2 w-10 h-10 flex items-center justify-center text-slate-500 hover:text-slate-800"
                      aria-label="পাসওয়ার্ড দেখুন"
                    >
                      {showAppPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                </div>
              )}

              {error && (
                <p className="text-sm text-rose-600 font-medium bg-rose-50 border border-rose-200 rounded-xl px-4 py-2.5">
                  {error}
                </p>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full h-12 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm flex items-center justify-center gap-2 transition-colors disabled:opacity-60"
              >
                <span>{loading ? 'সেটআপ হচ্ছে...' : 'পাসওয়ার্ড সেভ করে ড্যাশবোর্ডে প্রবেশ করুন'}</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </form>
          ) : (
            <form onSubmit={handleLogin} className="space-y-5">
              <div>
                <h2 className="font-display text-xl font-bold text-slate-900">
                  ভল্ট আনলক করুন
                </h2>
                <p className="mt-1 text-sm text-slate-500">
                  অ্যাপের ভেতরে প্রবেশ করতে আপনার ভল্ট মাস্টার পাসওয়ার্ড দিন।
                </p>
              </div>

              <div>
                <label className="block text-sm font-semibold text-slate-700 mb-1.5">
                  ভল্ট মাস্টার পাসওয়ার্ড
                </label>
                <div className="relative">
                  <input
                    type={showVaultPass ? 'text' : 'password'}
                    value={vaultPassword}
                    onChange={(e) => setVaultPassword(e.target.value)}
                    placeholder="আপনার ভল্ট পাসওয়ার্ড লিখুন"
                    autoFocus
                    className="w-full h-12 px-4 pr-12 rounded-xl border border-slate-300 bg-white text-slate-900 text-sm focus:outline-none focus:ring-2 focus:ring-blue-600 focus:border-transparent"
                  />
                  <button
                    type="button"
                    onClick={() => setShowVaultPass(!showVaultPass)}
                    className="absolute right-1.5 top-1/2 -translate-y-1/2 w-10 h-10 flex items-center justify-center text-slate-500 hover:text-slate-800"
                    aria-label="পাসওয়ার্ড দেখুন"
                  >
                    {showVaultPass ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                  </button>
                </div>
              </div>

              {error && (
                <p className="text-sm text-rose-600 font-medium bg-rose-50 border border-rose-200 rounded-xl px-4 py-2.5">
                  {error}
                </p>
              )}

              <button
                type="submit"
                disabled={loading}
                className="w-full h-12 rounded-xl bg-blue-600 hover:bg-blue-700 text-white font-semibold text-sm flex items-center justify-center gap-2 transition-colors disabled:opacity-60"
              >
                <Lock className="w-4 h-4" />
                <span>{loading ? 'যাচাই হচ্ছে...' : 'অ্যাপ আনলক করুন'}</span>
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
