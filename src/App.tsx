import React, { useCallback, useEffect, useState } from 'react';
import { Shield, KeyRound, Smartphone, DownloadCloud, RefreshCw, CheckCircle2 } from 'lucide-react';
import type { ActiveTab, ManagedApp, VaultState } from './types';
import { VaultAuthScreen } from './components/VaultAuthScreen';
import { AppsManagerTab } from './components/AppsManagerTab';
import { PasswordSettingsTab } from './components/PasswordSettingsTab';
import { SimulatorTab } from './components/SimulatorTab';
import { ApkAndOtaTab } from './components/ApkAndOtaTab';
import { AppLaunchOverlayModal } from './components/AppLaunchOverlayModal';

export default function App() {
  const [state, setState] = useState<VaultState | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [isUnlocked, setIsUnlocked] = useState(false);
  const [activeTab, setActiveTab] = useState<ActiveTab>('apps');
  const [testingApp, setTestingApp] = useState<ManagedApp | null>(null);
  const [updatingQuick, setUpdatingQuick] = useState(false);
  const [toastMsg, setToastMsg] = useState<string | null>(null);

  const fetchState = useCallback(async () => {
    try {
      const res = await fetch('/api/state');
      if (!res.ok) throw new Error('Failed to load state');
      const data: VaultState = await res.json();
      setState(data);
      setLoadError(null);
    } catch {
      setLoadError('সার্ভার থেকে তথ্য লোড করা যায়নি।');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchState();
  }, [fetchState]);

  const handleQuickAutoUpdate = async () => {
    setUpdatingQuick(true);
    try {
      const [latestRes, stateRes] = await Promise.all([
        fetch('/api/updates/latest'),
        fetch('/api/state'),
      ]);
      const latest = await latestRes.json();
      const fresh: VaultState = await stateRes.json();
      setState(fresh);
      setToastMsg(
        latest.apkUrl
          ? `আপডেট পাওয়া গেছে (v${latest.versionName}) — APK ও অটো-আপডেট ট্যাবে ডাউনলোড লিংক প্রস্তুত।`
          : `সিস্টেম সর্বশেষ সংস্করণে আছে (v${latest.versionName} · Policy #${latest.policyRevision})`
      );
      setTimeout(() => setToastMsg(null), 5000);
    } catch {
      setToastMsg('আপডেট চেক ব্যর্থ হয়েছে।');
      setTimeout(() => setToastMsg(null), 4000);
    } finally {
      setUpdatingQuick(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-[#f6f8fb] flex items-center justify-center p-6">
        <div className="text-sm font-medium text-slate-600">ShieldGuard লোড হচ্ছে...</div>
      </div>
    );
  }

  if (loadError || !state) {
    return (
      <div className="min-h-screen bg-[#f6f8fb] flex flex-col items-center justify-center p-6 gap-4">
        <p className="text-sm text-rose-600 font-medium">{loadError || 'তথ্য পাওয়া যায়নি'}</p>
        <button
          onClick={fetchState}
          className="h-11 px-5 rounded-xl bg-blue-600 text-white text-sm font-semibold"
        >
          আবার চেষ্টা করুন
        </button>
      </div>
    );
  }

  if (!state.hasVaultPassword || !isUnlocked) {
    return (
      <VaultAuthScreen
        hasVaultPassword={state.hasVaultPassword}
        onAuthenticated={(updatedState) => {
          if (updatedState) setState(updatedState);
          setIsUnlocked(true);
        }}
      />
    );
  }

  const navItems: Array<{ id: ActiveTab; label: string; mobileLabel: string; icon: React.ReactNode }> = [
    {
      id: 'apps',
      label: 'অ্যাপ লক ও লিমিট',
      mobileLabel: 'অ্যাপসমূহ',
      icon: <Shield className="w-5 h-5" />,
    },
    {
      id: 'passwords',
      label: 'পাসওয়ার্ড ভল্ট',
      mobileLabel: 'পাসওয়ার্ড',
      icon: <KeyRound className="w-5 h-5" />,
    },
    {
      id: 'simulator',
      label: 'টেস্ট সিমুলেটর',
      mobileLabel: 'সিমুলেটর',
      icon: <Smartphone className="w-5 h-5" />,
    },
    {
      id: 'apk',
      label: 'APK ও অটো-আপডেট',
      mobileLabel: 'APK/আপডেট',
      icon: <DownloadCloud className="w-5 h-5" />,
    },
  ];

  return (
    <div className="min-h-screen bg-[#f6f8fb] text-slate-900 pb-24 md:pb-12">
      {/* Top Bar Contract: 3 zones separated by gap-8 */}
      <header className="sticky top-0 z-30 bg-white/95 backdrop-blur-md border-b border-slate-200">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-8">
          <button
            type="button"
            onClick={() => setActiveTab('apps')}
            className="font-display text-lg font-bold tracking-tight text-slate-900 whitespace-nowrap shrink-0"
          >
            ShieldGuard
          </button>

          <nav className="hidden md:flex items-center gap-6 text-sm font-medium text-slate-600">
            {navItems.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => setActiveTab(item.id)}
                className={`py-1 whitespace-nowrap shrink-0 transition-colors border-b-2 ${
                  activeTab === item.id
                    ? 'text-blue-600 border-blue-600 font-semibold'
                    : 'border-transparent hover:text-slate-900'
                }`}
              >
                {item.label}
              </button>
            ))}
          </nav>

          <div className="flex items-center shrink-0">
            <button
              type="button"
              onClick={handleQuickAutoUpdate}
              disabled={updatingQuick}
              className="h-10 px-4 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0 transition-colors disabled:opacity-60"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${updatingQuick ? 'animate-spin' : ''}`} />
              <span>{updatingQuick ? 'চেক হচ্ছে...' : 'অটো আপডেট'}</span>
            </button>
          </div>
        </div>
      </header>

      {toastMsg && (
        <div className="max-w-7xl mx-auto px-4 sm:px-6 pt-4">
          <div className="bg-slate-900 text-white text-xs sm:text-sm px-4 py-3 rounded-xl flex items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
              <span>{toastMsg}</span>
            </div>
            <button
              type="button"
              onClick={() => setActiveTab('apk')}
              className="text-blue-300 hover:text-blue-200 font-semibold whitespace-nowrap shrink-0"
            >
              APK ও আপডেট দেখুন
            </button>
          </div>
        </div>
      )}

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-4 sm:px-6 pt-6 sm:pt-8 space-y-6">
        <div className="flex flex-col sm:flex-row sm:items-end justify-between gap-3">
          <div>
            <h1 className="font-display text-2xl sm:text-3xl font-bold text-slate-900 text-balance">
              {activeTab === 'apps' && 'মোবাইল অ্যাপ লক ও ব্যবহার সময়সীমা'}
              {activeTab === 'passwords' && 'ভল্ট ও অ্যাপ-লক ডুয়াল পাসওয়ার্ড'}
              {activeTab === 'simulator' && 'অ্যান্ড্রয়েড লক ও টাইম-লিমিট টেস্ট'}
              {activeTab === 'apk' && 'Android APK ডাউনলোড ও অটো-আপডেট'}
            </h1>
            <p className="mt-1 text-sm text-slate-500">
              সার্ভার ভার্সন <span className="font-mono-tabular">v{state.releaseManifest.versionName}</span> · পলিসি{' '}
              <span className="font-mono-tabular">#{state.releaseManifest.policyRevision}</span> ·{' '}
              {state.separateAppLockEnabled
                ? 'আলাদা অ্যাপ-লক পাসওয়ার্ড সক্রিয়'
                : 'অভিন্ন ভল্ট পাসওয়ার্ড সক্রিয়'}
            </p>
          </div>
        </div>

        {activeTab === 'apps' && (
          <AppsManagerTab
            state={state}
            onStateChange={setState}
            onTestAppLaunch={(app) => setTestingApp(app)}
          />
        )}

        {activeTab === 'passwords' && (
          <PasswordSettingsTab
            state={state}
            onStateChange={setState}
            onLockVaultNow={() => setIsUnlocked(false)}
          />
        )}

        {activeTab === 'simulator' && (
          <SimulatorTab state={state} onTestAppLaunch={(app) => setTestingApp(app)} />
        )}

        {activeTab === 'apk' && <ApkAndOtaTab state={state} onStateChange={setState} />}
      </main>

      {/* Mobile Bottom Tab Bar */}
      <nav className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-white/95 backdrop-blur-md border-t border-slate-200 grid grid-cols-4 h-16">
        {navItems.map((item) => {
          const active = activeTab === item.id;
          return (
            <button
              key={item.id}
              type="button"
              onClick={() => setActiveTab(item.id)}
              className={`flex flex-col items-center justify-center min-h-[44px] text-[11px] font-medium transition-colors ${
                active ? 'text-blue-600 font-semibold' : 'text-slate-500'
              }`}
            >
              {item.icon}
              <span className="mt-1 truncate max-w-[76px]">{item.mobileLabel}</span>
            </button>
          );
        })}
      </nav>

      {/* Simulated Mobile Lock Overlay Modal */}
      {testingApp && (
        <AppLaunchOverlayModal
          app={testingApp}
          separateAppLockEnabled={state.separateAppLockEnabled}
          onClose={() => setTestingApp(null)}
        />
      )}
    </div>
  );
}
