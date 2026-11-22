import React, { useEffect, useState } from 'react';
import {
  Download,
  RefreshCw,
  CheckCircle2,
  Copy,
  Check,
  FileCode2,
  UploadCloud,
  Smartphone,
  AlertCircle,
} from 'lucide-react';
import type { AndroidSourceFile, VaultState } from '../types';

interface ApkAndOtaTabProps {
  state: VaultState;
  onStateChange: (next: VaultState) => void;
}

export function ApkAndOtaTab({ state, onStateChange }: ApkAndOtaTabProps) {
  const [files, setFiles] = useState<AndroidSourceFile[]>([]);
  const [selectedPath, setSelectedPath] = useState<string>(
    'app/src/main/java/com/shieldguard/applock/MainActivity.kt'
  );
  const [copied, setCopied] = useState(false);

  const [checkingUpdate, setCheckingUpdate] = useState(false);
  const [updateStatus, setUpdateStatus] = useState<string | null>(null);

  const [newVersionName, setNewVersionName] = useState('');
  const [newChangelog, setNewChangelog] = useState('');
  const [newApkUrl, setNewApkUrl] = useState(state.releaseManifest.apkUrl || '');
  const [publishing, setPublishing] = useState(false);
  const [publishOk, setPublishOk] = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/android-project/files')
      .then((r) => r.json())
      .then((data) => {
        if (Array.isArray(data.files)) {
          setFiles(data.files);
          if (data.files.length > 0 && !data.files.some((f: AndroidSourceFile) => f.path === selectedPath)) {
            setSelectedPath(data.files[0].path);
          }
        }
      })
      .catch(() => {});
  }, [selectedPath]);

  const activeFile = files.find((f) => f.path === selectedPath);

  const handleCopyCode = async () => {
    if (!activeFile) return;
    await navigator.clipboard.writeText(activeFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  const handleCheckAutoUpdate = async () => {
    setCheckingUpdate(true);
    setUpdateStatus(null);
    try {
      const [latestRes, stateRes] = await Promise.all([
        fetch('/api/updates/latest'),
        fetch('/api/state'),
      ]);
      const latest = await latestRes.json();
      const freshState = await stateRes.json();
      onStateChange(freshState);

      if (latest.apkUrl) {
        setUpdateStatus(
          `নতুন ভার্সন v${latest.versionName} (Build ${latest.versionCode}) পাওয়া গেছে! APK ডাউনলোড লিংক প্রস্তুত: ${latest.apkUrl}`
        );
      } else {
        setUpdateStatus(
          `সর্বশেষ পলিসি ও সিস্টেম সিঙ্ক সম্পন্ন (v${latest.versionName} · Policy #${latest.policyRevision})। নতুন APK রিলিজ URL যুক্ত করলে ফোনের অ্যাপ অটো ডাউনলোড ও ইনস্টল করতে পারবে।`
        );
      }
    } catch {
      setUpdateStatus('আপডেট সার্ভারের সাথে সংযোগ করা যায়নি।');
    } finally {
      setCheckingUpdate(false);
    }
  };

  const handlePublishRelease = async (e: React.FormEvent) => {
    e.preventDefault();
    setPublishing(true);
    setPublishOk(null);
    try {
      const res = await fetch('/api/updates/publish', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          versionName: newVersionName || undefined,
          changelog: newChangelog || undefined,
          apkUrl: newApkUrl,
        }),
      });
      const data = await res.json();
      if (data.releaseManifest) {
        onStateChange({
          ...state,
          releaseManifest: data.releaseManifest,
        });
        setNewVersionName('');
        setNewChangelog('');
        setPublishOk(
          `নতুন রিলিজ v${data.releaseManifest.versionName} (Build ${data.releaseManifest.versionCode}) পাবলিশ হয়েছে! ফোনের ইনস্টল করা অ্যাপে "অটো আপডেট" চাপলেই এটি আপডেট হয়ে যাবে।`
        );
      }
    } finally {
      setPublishing(false);
    }
  };

  return (
    <div className="space-y-6">
      {/* Top 2-column split: APK Download & OTA Auto-Update */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* Native Android APK Builder & Source Download */}
        <div className="lg:col-span-7 bg-white border border-slate-200 rounded-2xl p-6 sm:p-8 flex flex-col justify-between gap-6">
          <div className="space-y-4">
            <div className="flex items-start gap-3.5">
              <div className="w-11 h-11 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center shrink-0">
                <Smartphone className="w-5 h-5" />
              </div>
              <div>
                <h2 className="font-display text-xl font-bold text-slate-900">
                  Android APK প্রজেক্ট ও GitHub অটো-বিল্ডার
                </h2>
                <p className="mt-1 text-sm text-slate-600 leading-relaxed">
                  মোবাইলের সকল অ্যাপ ডিটেক্ট (<span className="font-mono-tabular">PackageManager</span>), ব্যবহারের সময় মাপা (<span className="font-mono-tabular">UsageStatsManager</span>), লক স্ক্রিন (<span className="font-mono-tabular">AccessibilityService</span>) এবং ইন-অ্যাপ অটো আপডেট (<span className="font-mono-tabular">UpdateManager.kt</span>) সহ সম্পূর্ণ নেটিভ Android Kotlin প্রজেক্ট তৈরি করা আছে।
                </p>
              </div>
            </div>

            <div className="p-4 rounded-xl bg-amber-50 border border-amber-200 flex items-start gap-3 text-xs text-amber-900 leading-relaxed">
              <AlertCircle className="w-4 h-4 text-amber-600 shrink-0 mt-0.5" />
              <div>
                <strong>সততা ও কারিগরি তথ্য:</strong> এই ক্লাউড ওয়েব সার্ভারে Android SDK / Gradle ইনস্টল না থাকায় এখান থেকে সরাসরি নকল <span className="font-mono-tabular">.apk</span> ফাইল দেওয়া হয়নি (নকল ফাইল মোবাইলে ইনস্টল হয় না)। নিচের বাটন থেকে সম্পূর্ণ Android সোর্স <span className="font-mono-tabular">.zip</span> ডাউনলোড করুন — এর ভেতরেই <span className="font-mono-tabular">.github/workflows/build-apk.yml</span> যুক্ত করা আছে, যা GitHub-এ আপলোড করলেই পিসি ছাড়াই ৩ মিনিটে আসল <span className="font-mono-tabular">ShieldGuard-AppLock.apk</span> তৈরি করে দেবে।
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs text-slate-700">
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                <p className="font-bold text-slate-900 mb-1">ধাপ ১: ডাউনলোড</p>
                <p>নিচের বাটনে ক্লিক করে <span className="font-mono-tabular">ShieldGuard-Android-Source.zip</span> নামান।</p>
              </div>
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                <p className="font-bold text-slate-900 mb-1">ধাপ ২: APK তৈরি</p>
                <p>Android Studio-তে বিল্ড করুন অথবা GitHub-এ পুশ করে Actions থেকে APK ডাউনলোড করুন।</p>
              </div>
              <div className="p-3.5 rounded-xl bg-slate-50 border border-slate-200">
                <p className="font-bold text-slate-900 mb-1">ধাপ ৩: অটো আপডেট</p>
                <p>মোবাইলে ইনস্টল করার পর অ্যাপের ভেতরের আপডেট বাটনে চাপলেই এই সার্ভার থেকে অটো আপডেট হবে।</p>
              </div>
            </div>
          </div>

          <div className="flex flex-wrap items-center gap-3 pt-2">
            <a
              href="/api/android-project.zip"
              download="ShieldGuard-Android-Source.zip"
              className="h-12 px-6 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-semibold flex items-center gap-2 transition-colors whitespace-nowrap"
            >
              <Download className="w-4 h-4" />
              <span>Android প্রজেক্ট + APK বিল্ডার ডাউনলোড (.zip)</span>
            </a>
          </div>
        </div>

        {/* In-App OTA Auto-Update Panel */}
        <div className="lg:col-span-5 bg-white border border-slate-200 rounded-2xl p-6 sm:p-8 flex flex-col justify-between gap-5">
          <div className="space-y-4">
            <div className="flex items-center justify-between gap-2">
              <h2 className="font-display text-lg font-bold text-slate-900">
                OTA অটো-আপডেট সিস্টেম
              </h2>
              <span className="text-xs font-mono-tabular text-slate-500">
                v{state.releaseManifest.versionName} · Build {state.releaseManifest.versionCode}
              </span>
            </div>

            <div className="p-4 rounded-xl bg-slate-50 border border-slate-200 text-xs space-y-1.5 text-slate-600">
              <div className="flex justify-between">
                <span>পলিসি রিভিশন</span>
                <span className="font-mono-tabular font-semibold text-slate-900">
                  #{state.releaseManifest.policyRevision}
                </span>
              </div>
              <div className="flex justify-between">
                <span>সর্বশেষ প্রকাশ</span>
                <span className="font-mono-tabular text-slate-900">
                  {new Date(state.releaseManifest.publishedAt).toLocaleDateString('bn-BD')}
                </span>
              </div>
              <p className="pt-1 text-slate-700">{state.releaseManifest.changelog}</p>
            </div>

            <button
              type="button"
              onClick={handleCheckAutoUpdate}
              disabled={checkingUpdate}
              className="w-full h-11 rounded-xl bg-slate-900 hover:bg-slate-800 text-white text-sm font-semibold flex items-center justify-center gap-2 transition-colors disabled:opacity-60"
            >
              <RefreshCw className={`w-4 h-4 ${checkingUpdate ? 'animate-spin' : ''}`} />
              <span>{checkingUpdate ? 'আপডেট চেক হচ্ছে...' : 'অটো আপডেট চেক ও সিঙ্ক করুন'}</span>
            </button>

            {updateStatus && (
              <div className="p-3 rounded-xl bg-blue-50 border border-blue-200 text-xs text-blue-900 leading-relaxed">
                {updateStatus}
              </div>
            )}
          </div>

          {/* Publish New OTA APK Update */}
          <form onSubmit={handlePublishRelease} className="pt-4 border-t border-slate-200 space-y-3">
            <p className="text-xs font-bold text-slate-800 flex items-center gap-1.5">
              <UploadCloud className="w-4 h-4 text-blue-600" />
              <span>নতুন APK ভার্সন পুশ করুন (ফোনের অটো-আপডেটের জন্য)</span>
            </p>
            <div className="grid grid-cols-2 gap-2.5">
              <input
                type="text"
                value={newVersionName}
                onChange={(e) => setNewVersionName(e.target.value)}
                placeholder={`ভার্সন (যেমন 1.0.${state.releaseManifest.versionCode})`}
                className="h-10 px-3 rounded-lg border border-slate-300 text-xs"
              />
              <input
                type="url"
                value={newApkUrl}
                onChange={(e) => setNewApkUrl(e.target.value)}
                placeholder="GitHub/Direct .apk লিংক"
                className="h-10 px-3 rounded-lg border border-slate-300 text-xs font-mono-tabular"
              />
            </div>
            <input
              type="text"
              value={newChangelog}
              onChange={(e) => setNewChangelog(e.target.value)}
              placeholder="আপডেটে কী নতুন যোগ হয়েছে (Changelog)..."
              className="w-full h-10 px-3 rounded-lg border border-slate-300 text-xs"
            />
            <button
              type="submit"
              disabled={publishing}
              className="w-full h-10 rounded-lg border border-blue-600 text-blue-600 hover:bg-blue-50 text-xs font-semibold transition-colors"
            >
              {publishing ? 'পাবলিশ হচ্ছে...' : 'নতুন আপডেট রিলিজ পাবলিশ করুন'}
            </button>
            {publishOk && (
              <p className="text-xs text-emerald-700 font-medium flex items-start gap-1.5">
                <CheckCircle2 className="w-4 h-4 shrink-0 mt-0.5" />
                <span>{publishOk}</span>
              </p>
            )}
          </form>
        </div>
      </div>

      {/* Android Kotlin Source Code Explorer */}
      <div className="bg-white border border-slate-200 rounded-2xl overflow-hidden">
        <div className="px-5 py-4 border-b border-slate-200 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <FileCode2 className="w-4 h-4 text-slate-600" />
            <h3 className="font-display text-sm font-bold text-slate-900">
              Android Kotlin সোর্স কোড প্রিভিউ ({files.length} ফাইল)
            </h3>
          </div>
          <button
            type="button"
            onClick={handleCopyCode}
            disabled={!activeFile}
            className="h-9 px-3.5 rounded-lg border border-slate-200 hover:bg-slate-50 text-xs font-semibold text-slate-700 flex items-center gap-1.5"
          >
            {copied ? <Check className="w-3.5 h-3.5 text-emerald-600" /> : <Copy className="w-3.5 h-3.5" />}
            <span>{copied ? 'কপি হয়েছে' : 'কোড কপি করুন'}</span>
          </button>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-12">
          <div className="lg:col-span-4 border-b lg:border-b-0 lg:border-r border-slate-200 max-h-[380px] overflow-y-auto divide-y divide-slate-100">
            {files.map((f) => (
              <button
                key={f.path}
                type="button"
                onClick={() => setSelectedPath(f.path)}
                className={`w-full text-left px-4 py-3 text-xs font-mono-tabular truncate transition-colors ${
                  selectedPath === f.path
                    ? 'bg-blue-50 text-blue-700 font-semibold'
                    : 'text-slate-600 hover:bg-slate-50'
                }`}
              >
                {f.path}
              </button>
            ))}
          </div>

          <div className="lg:col-span-8 bg-slate-950 text-slate-100 p-4 max-h-[380px] overflow-auto">
            <pre className="text-xs font-mono-tabular leading-relaxed whitespace-pre">
              {activeFile ? activeFile.content : 'লোড হচ্ছে...'}
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
}
