import React, { useMemo, useState } from 'react';
import { Search, Lock, Unlock, Plus, Clock, RotateCcw, Play } from 'lucide-react';
import type { ManagedApp, VaultState } from '../types';

interface AppsManagerTabProps {
  state: VaultState;
  onStateChange: (next: VaultState) => void;
  onTestAppLaunch: (app: ManagedApp) => void;
}

type FilterMode = 'all' | 'locked' | 'limited' | 'exceeded';

const PRESET_LIMITS = [
  { label: 'লিমিট বন্ধ', value: 0 },
  { label: '১৫ মি.', value: 15 },
  { label: '৩০ মি.', value: 30 },
  { label: '৪৫ মি.', value: 45 },
  { label: '১ ঘণ্টা', value: 60 },
  { label: '৯০ মি.', value: 90 },
  { label: '২ ঘণ্টা', value: 120 },
];

export function AppsManagerTab({ state, onStateChange, onTestAppLaunch }: AppsManagerTabProps) {
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState<FilterMode>('all');
  const [showAddForm, setShowAddForm] = useState(false);
  const [newName, setNewName] = useState('');
  const [newPkg, setNewPkg] = useState('');
  const [newCat, setNewCat] = useState('Social');
  const [addError, setAddError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const stats = useMemo(() => {
    const total = state.apps.length;
    const locked = state.apps.filter((a) => a.isLocked).length;
    const limited = state.apps.filter((a) => a.dailyLimitMinutes > 0).length;
    const exceeded = state.apps.filter(
      (a) => a.dailyLimitMinutes > 0 && a.usedMinutesToday >= a.dailyLimitMinutes
    ).length;
    return { total, locked, limited, exceeded };
  }, [state.apps]);

  const filteredApps = useMemo(() => {
    const q = search.trim().toLowerCase();
    return state.apps.filter((app) => {
      if (
        q &&
        !app.name.toLowerCase().includes(q) &&
        !app.packageName.toLowerCase().includes(q) &&
        !app.category.toLowerCase().includes(q)
      ) {
        return false;
      }
      if (filter === 'locked') return app.isLocked;
      if (filter === 'limited') return app.dailyLimitMinutes > 0;
      if (filter === 'exceeded') {
        return app.dailyLimitMinutes > 0 && app.usedMinutesToday >= app.dailyLimitMinutes;
      }
      return true;
    });
  }, [state.apps, search, filter]);

  const handleToggleLock = async (app: ManagedApp) => {
    setBusyId(app.id);
    try {
      const res = await fetch('/api/apps/toggle-lock', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: app.id, isLocked: !app.isLocked }),
      });
      const data = await res.json();
      if (data.state) onStateChange(data.state);
    } finally {
      setBusyId(null);
    }
  };

  const handleLimitChange = async (app: ManagedApp, minutes: number) => {
    setBusyId(app.id);
    try {
      const res = await fetch('/api/apps/set-limit', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: app.id, dailyLimitMinutes: minutes }),
      });
      const data = await res.json();
      if (data.state) onStateChange(data.state);
    } finally {
      setBusyId(null);
    }
  };

  const handleSimulateUsage = async (app: ManagedApp, addMinutes: number) => {
    setBusyId(app.id);
    try {
      const res = await fetch('/api/apps/simulate-usage', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id: app.id, addMinutes }),
      });
      const data = await res.json();
      if (data.state) onStateChange(data.state);
    } finally {
      setBusyId(null);
    }
  };

  const handleResetUsage = async (id?: string) => {
    const res = await fetch('/api/apps/reset-usage', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id }),
    });
    const data = await res.json();
    if (data.state) onStateChange(data.state);
  };

  const handleAddCustomApp = async (e: React.FormEvent) => {
    e.preventDefault();
    setAddError(null);
    const res = await fetch('/api/apps/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: newName, packageName: newPkg, category: newCat }),
    });
    const data = await res.json();
    if (!res.ok) {
      setAddError(data.error || 'যোগ করা যায়নি।');
      return;
    }
    onStateChange(data.state);
    setNewName('');
    setNewPkg('');
    setShowAddForm(false);
  };

  return (
    <div className="space-y-6">
      {/* 4-column summary strip */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-white border border-slate-200 rounded-2xl p-5">
          <p className="text-xs font-medium text-slate-500">তালিকাভুক্ত মোবাইল অ্যাপ</p>
          <p className="mt-1.5 text-2xl font-bold text-slate-900 font-mono-tabular">
            {stats.total}টি
          </p>
        </div>
        <div className="bg-white border border-slate-200 rounded-2xl p-5">
          <p className="text-xs font-medium text-slate-500">লক করা আছে</p>
          <p className="mt-1.5 text-2xl font-bold text-blue-600 font-mono-tabular">
            {stats.locked}টি
          </p>
        </div>
        <div className="bg-white border border-slate-200 rounded-2xl p-5">
          <p className="text-xs font-medium text-slate-500">টাইম লিমিট সেট</p>
          <p className="mt-1.5 text-2xl font-bold text-slate-900 font-mono-tabular">
            {stats.limited}টি
          </p>
        </div>
        <div className="bg-white border border-slate-200 rounded-2xl p-5">
          <p className="text-xs font-medium text-slate-500">দৈনিক লিমিট শেষ</p>
          <p className="mt-1.5 text-2xl font-bold text-rose-600 font-mono-tabular">
            {stats.exceeded}টি
          </p>
        </div>
      </div>

      {/* Controls Toolbar */}
      <div className="bg-white border border-slate-200 rounded-2xl p-4 sm:p-5 space-y-4">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div className="relative flex-1 max-w-md">
            <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="অ্যাপ বা প্যাকেজ নাম দিয়ে খুঁজুন (যেমন com.whatsapp)..."
              className="w-full h-11 pl-10 pr-4 rounded-xl border border-slate-200 bg-slate-50 text-sm text-slate-900 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-600"
            />
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="flex items-center gap-1 p-1 bg-slate-100 rounded-xl overflow-x-auto">
              {(
                [
                  { id: 'all', label: 'সব অ্যাপ' },
                  { id: 'locked', label: 'লক করা' },
                  { id: 'limited', label: 'টাইম লিমিট' },
                  { id: 'exceeded', label: 'লিমিট শেষ' },
                ] as const
              ).map((tab) => (
                <button
                  key={tab.id}
                  onClick={() => setFilter(tab.id)}
                  className={`px-3 py-2 text-xs font-semibold rounded-lg whitespace-nowrap shrink-0 transition-colors ${
                    filter === tab.id
                      ? 'bg-white text-slate-900 shadow-sm'
                      : 'text-slate-600 hover:text-slate-900'
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>

            <button
              onClick={() => setShowAddForm(!showAddForm)}
              className="h-11 px-4 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0 transition-colors"
            >
              <Plus className="w-4 h-4" />
              <span>অ্যাপ যোগ করুন</span>
            </button>

            <button
              onClick={() => handleResetUsage()}
              className="h-11 px-3.5 rounded-xl border border-slate-200 hover:bg-slate-50 text-slate-700 text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0 transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>ব্যবহার রিসেট</span>
            </button>
          </div>
        </div>

        {showAddForm && (
          <form
            onSubmit={handleAddCustomApp}
            className="pt-4 border-t border-slate-200 grid grid-cols-1 sm:grid-cols-4 gap-3"
          >
            <input
              type="text"
              value={newName}
              onChange={(e) => setNewName(e.target.value)}
              placeholder="অ্যাপের নাম (যেমন Telegram)"
              className="h-11 px-3.5 rounded-xl border border-slate-300 text-sm"
              required
            />
            <input
              type="text"
              value={newPkg}
              onChange={(e) => setNewPkg(e.target.value)}
              placeholder="Package (যেমন org.telegram.messenger)"
              className="h-11 px-3.5 rounded-xl border border-slate-300 text-sm font-mono-tabular"
              required
            />
            <select
              value={newCat}
              onChange={(e) => setNewCat(e.target.value)}
              className="h-11 px-3.5 rounded-xl border border-slate-300 text-sm bg-white"
            >
              <option value="Social">Social</option>
              <option value="Video">Video</option>
              <option value="Finance">Finance</option>
              <option value="Games">Games</option>
              <option value="Work">Work</option>
              <option value="Installed">Installed</option>
            </select>
            <button
              type="submit"
              className="h-11 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold"
            >
              তালিকায় সেভ করুন
            </button>
            {addError && <p className="sm:col-span-4 text-xs text-rose-600">{addError}</p>}
          </form>
        )}

        <p className="text-xs text-slate-500">
          নোট: Android APK মোবাইলে ইনস্টল করলে <span className="font-mono-tabular">AppDetector.kt</span> ফোনের সকল অ্যাপ স্বয়ংক্রিয়ভাবে ডিটেক্ট করে, এবং এখানকার সেট করা লক ও টাইম লিমিট নিয়ম সরাসরি ফোনে সিঙ্ক হয়।
        </p>
      </div>

      {/* App List Grid */}
      {filteredApps.length === 0 ? (
        <div className="bg-white border border-slate-200 rounded-2xl p-10 text-center text-sm text-slate-500">
          এই ফিল্টারে কোনো অ্যাপ পাওয়া যায়নি।
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {filteredApps.map((app) => {
            const hasLimit = app.dailyLimitMinutes > 0;
            const limitReached = hasLimit && app.usedMinutesToday >= app.dailyLimitMinutes;
            const pct = hasLimit
              ? Math.min(100, Math.round((app.usedMinutesToday / app.dailyLimitMinutes) * 100))
              : 0;

            return (
              <div
                key={app.id}
                className="bg-white border border-slate-200 rounded-2xl p-5 flex flex-col justify-between gap-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex items-center gap-3.5 min-w-0">
                    <div
                      className={`w-12 h-12 rounded-xl flex items-center justify-center font-display font-bold text-lg shrink-0 ${
                        limitReached
                          ? 'bg-rose-50 text-rose-700 border border-rose-200'
                          : app.isLocked
                            ? 'bg-blue-50 text-blue-700 border border-blue-200'
                            : 'bg-slate-100 text-slate-700'
                      }`}
                    >
                      {app.name.slice(0, 1).toUpperCase()}
                    </div>

                    <div className="min-w-0">
                      <h3 className="text-base font-bold text-slate-900 truncate">{app.name}</h3>
                      <div className="mt-0.5 flex flex-wrap items-center gap-1.5 text-xs text-slate-500">
                        <span>{app.category}</span>
                        <span aria-hidden="true">·</span>
                        <span className="font-mono-tabular truncate max-w-[180px]">
                          {app.packageName}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Lock Toggle */}
                  <button
                    type="button"
                    disabled={busyId === app.id}
                    onClick={() => handleToggleLock(app)}
                    className={`min-h-[44px] px-3.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 shrink-0 transition-colors ${
                      app.isLocked
                        ? 'bg-blue-600 text-white hover:bg-blue-700'
                        : 'bg-slate-100 text-slate-700 hover:bg-slate-200'
                    }`}
                  >
                    {app.isLocked ? <Lock className="w-3.5 h-3.5" /> : <Unlock className="w-3.5 h-3.5" />}
                    <span>{app.isLocked ? 'লক চালু' : 'লক বন্ধ'}</span>
                  </button>
                </div>

                {/* Usage Progress & Time Limit */}
                <div className="space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-slate-600 font-medium flex items-center gap-1.5">
                      <Clock className="w-3.5 h-3.5 text-slate-400" />
                      <span>
                        আজ ব্যবহার:{' '}
                        <strong className="font-mono-tabular text-slate-900">
                          {app.usedMinutesToday} মি.
                        </strong>
                        {hasLimit && (
                          <>
                            {' / '}
                            <span className="font-mono-tabular">{app.dailyLimitMinutes} মি.</span>
                          </>
                        )}
                      </span>
                    </span>
                    <span
                      className={`font-semibold ${
                        limitReached ? 'text-rose-600' : hasLimit ? 'text-slate-600' : 'text-slate-400'
                      }`}
                    >
                      {limitReached ? 'সময়সীমা শেষ' : hasLimit ? `${pct}% ব্যবহৃত` : 'কোনো লিমিট নেই'}
                    </span>
                  </div>

                  {hasLimit && (
                    <div className="h-2 w-full bg-slate-100 rounded-full overflow-hidden">
                      <div
                        className={`h-full transition-all ${
                          limitReached ? 'bg-rose-600' : pct > 80 ? 'bg-amber-500' : 'bg-blue-600'
                        }`}
                        style={{ width: `${pct}%` }}
                      />
                    </div>
                  )}
                </div>

                {/* Bottom Action Row */}
                <div className="pt-2 border-t border-slate-100 flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <label className="text-xs text-slate-500 whitespace-nowrap" htmlFor={`limit-${app.id}`}>
                      দৈনিক লিমিট:
                    </label>
                    <select
                      id={`limit-${app.id}`}
                      value={
                        PRESET_LIMITS.some((p) => p.value === app.dailyLimitMinutes)
                          ? app.dailyLimitMinutes
                          : 'custom'
                      }
                      onChange={(e) => {
                        const val = Number(e.target.value);
                        if (!Number.isNaN(val)) handleLimitChange(app, val);
                      }}
                      className="h-10 px-2.5 rounded-lg border border-slate-200 bg-slate-50 text-xs font-semibold text-slate-800"
                    >
                      {PRESET_LIMITS.map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                      {!PRESET_LIMITS.some((p) => p.value === app.dailyLimitMinutes) && (
                        <option value="custom">{app.dailyLimitMinutes} মি.</option>
                      )}
                    </select>
                  </div>

                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => handleSimulateUsage(app, 15)}
                      className="h-10 px-3 rounded-lg border border-slate-200 hover:bg-slate-50 text-xs font-medium text-slate-700 whitespace-nowrap"
                      title="টেস্ট করার জন্য ১৫ মিনিট ব্যবহার যোগ করুন"
                    >
                      +১৫ মি. টেস্ট
                    </button>
                    <button
                      type="button"
                      onClick={() => onTestAppLaunch(app)}
                      className="h-10 px-3.5 rounded-lg bg-slate-900 hover:bg-slate-800 text-white text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap"
                    >
                      <Play className="w-3.5 h-3.5" />
                      <span>লঞ্চ টেস্ট</span>
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
