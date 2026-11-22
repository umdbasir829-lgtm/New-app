import React, { useState } from 'react';
import { Lock, Clock, CheckCircle2, X, ShieldAlert } from 'lucide-react';
import type { ManagedApp } from '../types';

interface AppLaunchOverlayModalProps {
  app: ManagedApp;
  separateAppLockEnabled: boolean;
  onClose: () => void;
}

export function AppLaunchOverlayModal({
  app,
  separateAppLockEnabled,
  onClose,
}: AppLaunchOverlayModalProps) {
  const [password, setPassword] = useState('');
  const [unlocked, setUnlocked] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const isLimitReached = app.dailyLimitMinutes > 0 && app.usedMinutesToday >= app.dailyLimitMinutes;
  const isBlocked = isLimitReached || app.isLocked;

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await fetch('/api/auth/verify-applock', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          password,
          isLimitOverride: isLimitReached,
        }),
      });
      const data = await res.json();
      if (!data.ok) {
        setError(
          isLimitReached
            ? 'ভুল ভল্ট মাস্টার পাসওয়ার্ড! সময়সীমা অতিক্রম করলে শুধু ভল্ট পাসওয়ার্ড দিয়েই খোলা যাবে।'
            : separateAppLockEnabled
              ? 'ভুল অ্যাপ-লক পাসওয়ার্ড! (ভল্ট পাসওয়ার্ড নয়, আলাদা অ্যাপ-লক পাসওয়ার্ড দিন)'
              : 'ভুল পাসওয়ার্ড!'
        );
        return;
      }
      setUnlocked(true);
    } catch {
      setError('যাচাই করা যায়নি।');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-950/60 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="w-full max-w-sm bg-slate-900 text-white rounded-3xl border border-slate-800 shadow-2xl overflow-hidden">
        <div className="flex items-center justify-between px-5 py-4 border-b border-slate-800">
          <span className="text-xs font-mono-tabular text-slate-400 truncate">
            {app.packageName}
          </span>
          <button
            onClick={onClose}
            className="min-w-[44px] min-h-[44px] -mr-2 flex items-center justify-center text-slate-400 hover:text-white rounded-full"
            aria-label="বন্ধ করুন"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <div className="p-6 text-center">
          {unlocked || !isBlocked ? (
            <div className="space-y-4 py-4">
              <div className="w-16 h-16 rounded-2xl bg-emerald-500/15 text-emerald-400 flex items-center justify-center mx-auto">
                <CheckCircle2 className="w-9 h-9" />
              </div>
              <h3 className="font-display text-xl font-bold">{app.name} চালু হয়েছে</h3>
              <p className="text-sm text-slate-300">
                {!isBlocked
                  ? 'এই অ্যাপে বর্তমানে কোনো লক নেই এবং দৈনিক সময়সীমাও বাকি আছে।'
                  : isLimitReached
                    ? 'ভল্ট মাস্টার পাসওয়ার্ড দিয়ে অতিরিক্ত ১০ মিনিট ব্যবহারের অনুমতি দেওয়া হয়েছে।'
                    : 'অ্যাপ-লক পাসওয়ার্ড সঠিক হয়েছে — অ্যাপটি আনলক হয়েছে।'}
              </p>
              <button
                onClick={onClose}
                className="w-full h-11 rounded-xl bg-white text-slate-900 font-semibold text-sm hover:bg-slate-100 transition-colors"
              >
                ড্যাশবোর্ডে ফিরুন
              </button>
            </div>
          ) : (
            <form onSubmit={handleVerify} className="space-y-4">
              <div
                className={`w-16 h-16 rounded-2xl flex items-center justify-center mx-auto ${
                  isLimitReached ? 'bg-rose-500/15 text-rose-400' : 'bg-blue-500/15 text-blue-400'
                }`}
              >
                {isLimitReached ? <Clock className="w-8 h-8" /> : <Lock className="w-8 h-8" />}
              </div>

              <div>
                <h3 className="font-display text-xl font-bold">{app.name}</h3>
                <p className="mt-1.5 text-sm text-slate-300">
                  {isLimitReached
                    ? `আজকের দৈনিক সময়সীমা শেষ (${app.usedMinutesToday}/${app.dailyLimitMinutes} মিনিট)`
                    : 'এই মোবাইল অ্যাপটি লক করা আছে'}
                </p>
              </div>

              <div className="p-3 rounded-xl bg-slate-800/80 border border-slate-700 text-xs text-slate-300 flex items-start gap-2 text-left">
                <ShieldAlert className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
                <span>
                  {isLimitReached
                    ? 'দৈনিক লিমিট শেষ হওয়ায় আনলক করতে ভল্ট মাস্টার পাসওয়ার্ড প্রয়োজন।'
                    : separateAppLockEnabled
                      ? 'এটি খুলতে আপনার আলাদা অ্যাপ-লক পাসওয়ার্ড দিন (ভল্ট পাসওয়ার্ড নয়)।'
                      : 'খুলতে আপনার পাসওয়ার্ড দিন।'}
                </span>
              </div>

              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={isLimitReached ? 'ভল্ট মাস্টার পাসওয়ার্ড' : 'অ্যাপ-লক পাসওয়ার্ড'}
                autoFocus
                className="w-full h-12 px-4 rounded-xl bg-slate-800 border border-slate-700 text-white text-sm text-center focus:outline-none focus:ring-2 focus:ring-blue-500"
              />

              {error && <p className="text-xs text-rose-400 font-medium">{error}</p>}

              <div className="grid grid-cols-2 gap-2.5 pt-1">
                <button
                  type="button"
                  onClick={onClose}
                  className="h-11 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-medium transition-colors"
                >
                  হোমে যান
                </button>
                <button
                  type="submit"
                  disabled={loading}
                  className="h-11 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-sm font-semibold transition-colors disabled:opacity-60"
                >
                  {loading ? 'যাচাই...' : 'আনলক করুন'}
                </button>
              </div>
            </form>
          )}
        </div>
      </div>
    </div>
  );
}
