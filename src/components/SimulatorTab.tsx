import React from 'react';
import { Lock, Clock, Smartphone } from 'lucide-react';
import type { ManagedApp, VaultState } from '../types';

interface SimulatorTabProps {
  state: VaultState;
  onTestAppLaunch: (app: ManagedApp) => void;
}

export function SimulatorTab({ state, onTestAppLaunch }: SimulatorTabProps) {
  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-start">
      <div className="lg:col-span-5 bg-white border border-slate-200 rounded-2xl p-6 space-y-4">
        <div className="w-10 h-10 rounded-xl bg-slate-100 text-slate-800 flex items-center justify-center">
          <Smartphone className="w-5 h-5" />
        </div>
        <h2 className="font-display text-lg font-bold text-slate-900">
          মোবাইল লক ও টাইম-লিমিট টেস্ট সিমুলেটর
        </h2>
        <p className="text-sm text-slate-600 leading-relaxed">
          ডানপাশের মোবাইল হোম স্ক্রিনের যেকোনো অ্যাপে ট্যাপ করে দেখুন অ্যান্ড্রয়েড ফোনে <span className="font-mono-tabular">LockOverlayActivity</span> কীভাবে কাজ করে:
        </p>
        <ul className="space-y-2.5 text-sm text-slate-600">
          <li>· লক করা অ্যাপে ট্যাপ করলে আলাদা <strong>অ্যাপ-লক পাসওয়ার্ড</strong> চাইবে।</li>
          <li>· যে অ্যাপের দৈনিক সময়সীমা শেষ হয়ে গেছে, তাতে ট্যাপ করলে <strong>ভল্ট মাস্টার পাসওয়ার্ড</strong> ছাড়া খুলবে না।</li>
          <li>· লক ও লিমিট মুক্ত অ্যাপ সরাসরি ওপেন হবে।</li>
        </ul>
      </div>

      <div className="lg:col-span-7 flex justify-center">
        <div className="w-full max-w-sm bg-slate-950 rounded-[2.5rem] p-4 border-[6px] border-slate-800 shadow-xl">
          <div className="w-24 h-5 bg-slate-800 rounded-full mx-auto mb-5" />

          <div className="bg-slate-900 rounded-3xl p-5 min-h-[460px] flex flex-col justify-between">
            <div>
              <div className="flex items-center justify-between text-xs text-slate-400 mb-5">
                <span className="font-mono-tabular">ShieldGuard Active</span>
                <span className="font-mono-tabular">
                  {state.apps.filter((a) => a.isLocked).length} Locked
                </span>
              </div>

              <div className="grid grid-cols-3 gap-4">
                {state.apps.slice(0, 9).map((app) => {
                  const limitHit =
                    app.dailyLimitMinutes > 0 && app.usedMinutesToday >= app.dailyLimitMinutes;
                  return (
                    <button
                      key={app.id}
                      type="button"
                      onClick={() => onTestAppLaunch(app)}
                      className="flex flex-col items-center gap-2 p-2 rounded-2xl hover:bg-slate-800/70 transition-colors"
                    >
                      <div className="relative w-14 h-14 rounded-2xl bg-slate-800 border border-slate-700 flex items-center justify-center text-white font-display font-bold text-lg">
                        {app.name.slice(0, 1).toUpperCase()}
                        {limitHit ? (
                          <span className="absolute -top-1.5 -right-1.5 w-6 h-6 rounded-full bg-rose-600 text-white flex items-center justify-center shadow">
                            <Clock className="w-3.5 h-3.5" />
                          </span>
                        ) : app.isLocked ? (
                          <span className="absolute -top-1.5 -right-1.5 w-6 h-6 rounded-full bg-blue-600 text-white flex items-center justify-center shadow">
                            <Lock className="w-3.5 h-3.5" />
                          </span>
                        ) : null}
                      </div>
                      <span className="text-xs text-slate-200 font-medium truncate w-full text-center">
                        {app.name}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="pt-4 mt-4 border-t border-slate-800 text-center text-[11px] text-slate-400">
              যেকোনো অ্যাপ আইকনে ট্যাপ করে পাসওয়ার্ড টেস্ট করুন
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
