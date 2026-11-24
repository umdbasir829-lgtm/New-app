import express from 'express';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import JSZip from 'jszip';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PORT = Number(process.env.PORT) || 3000;
const DATA_DIR = path.resolve(__dirname, 'data');
const STORE_FILE = path.join(DATA_DIR, 'shieldguard-store.json');
const ANDROID_DIR = path.resolve(__dirname, 'android-project');

interface ManagedApp {
  id: string;
  name: string;
  packageName: string;
  category: string;
  isLocked: boolean;
  dailyLimitMinutes: number;
  usedMinutesToday: number;
}

interface ReleaseManifest {
  versionCode: number;
  versionName: string;
  policyRevision: number;
  publishedAt: string;
  changelog: string;
  apkUrl: string;
}

interface RuntimeConfig {
  configVersion: number;
  accentBlue: string;
  accentGreen: string;
  homeAnnouncement: string;
  lockSubtitle: string;
  heroActiveTitle: string;
}

interface VaultStore {
  vaultPasswordHash: string | null;
  vaultSalt: string | null;
  separateAppLockEnabled: boolean;
  appLockPasswordHash: string | null;
  appLockSalt: string | null;
  apps: ManagedApp[];
  releaseManifest: ReleaseManifest;
  runtimeConfig?: RuntimeConfig;
}

const DEFAULT_RUNTIME_CONFIG: RuntimeConfig = {
  configVersion: 1,
  accentBlue: '#3B82F6',
  accentGreen: '#10B981',
  homeAnnouncement: '',
  lockSubtitle: 'প্রবেশ করতে আপনার লক যাচাই করুন',
  heroActiveTitle: 'সুরক্ষা সম্পূর্ণ সক্রিয়',
};

const DEFAULT_APPS: ManagedApp[] = [
  {
    id: 'whatsapp',
    name: 'WhatsApp',
    packageName: 'com.whatsapp',
    category: 'Social',
    isLocked: true,
    dailyLimitMinutes: 60,
    usedMinutesToday: 38,
  },
  {
    id: 'facebook',
    name: 'Facebook',
    packageName: 'com.facebook.katana',
    category: 'Social',
    isLocked: true,
    dailyLimitMinutes: 45,
    usedMinutesToday: 45,
  },
  {
    id: 'youtube',
    name: 'YouTube',
    packageName: 'com.google.android.youtube',
    category: 'Video',
    isLocked: false,
    dailyLimitMinutes: 90,
    usedMinutesToday: 52,
  },
  {
    id: 'instagram',
    name: 'Instagram',
    packageName: 'com.instagram.android',
    category: 'Social',
    isLocked: true,
    dailyLimitMinutes: 30,
    usedMinutesToday: 19,
  },
  {
    id: 'tiktok',
    name: 'TikTok',
    packageName: 'com.zhiliaoapp.musically',
    category: 'Video',
    isLocked: true,
    dailyLimitMinutes: 25,
    usedMinutesToday: 25,
  },
  {
    id: 'bkash',
    name: 'bKash',
    packageName: 'com.bKash.customerapp',
    category: 'Finance',
    isLocked: true,
    dailyLimitMinutes: 0,
    usedMinutesToday: 4,
  },
  {
    id: 'chrome',
    name: 'Google Chrome',
    packageName: 'com.android.chrome',
    category: 'Browser',
    isLocked: false,
    dailyLimitMinutes: 120,
    usedMinutesToday: 31,
  },
  {
    id: 'gallery',
    name: 'Gallery',
    packageName: 'com.sec.android.gallery3d',
    category: 'Media',
    isLocked: true,
    dailyLimitMinutes: 0,
    usedMinutesToday: 8,
  },
  {
    id: 'pubg',
    name: 'PUBG Mobile',
    packageName: 'com.tencent.ig',
    category: 'Games',
    isLocked: false,
    dailyLimitMinutes: 60,
    usedMinutesToday: 44,
  },
  {
    id: 'gmail',
    name: 'Gmail',
    packageName: 'com.google.android.gm',
    category: 'Work',
    isLocked: true,
    dailyLimitMinutes: 0,
    usedMinutesToday: 11,
  },
];

function hashSecret(secret: string, salt: string): string {
  return crypto.scryptSync(secret, salt, 32).toString('hex');
}

function verifySecret(secret: string, expectedHash: string | null, salt: string | null): boolean {
  if (!expectedHash || !salt) return false;
  const candidate = crypto.scryptSync(secret, salt, 32);
  const expected = Buffer.from(expectedHash, 'hex');
  if (candidate.length !== expected.length) return false;
  return crypto.timingSafeEqual(candidate, expected);
}

function readStore(): VaultStore {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
  if (!fs.existsSync(STORE_FILE)) {
    const initial: VaultStore = {
      vaultPasswordHash: null,
      vaultSalt: null,
      separateAppLockEnabled: true,
      appLockPasswordHash: null,
      appLockSalt: null,
      apps: DEFAULT_APPS,
      releaseManifest: {
        versionCode: 9,
        versionName: '2.5.0',
        policyRevision: 1,
        publishedAt: new Date().toISOString(),
        changelog: 'v2.5.0: ০ms তাৎক্ষণিক লক ওভারলে (আগে অ্যাপ খুলে যাওয়া ও ডাবল পিন সম্পূর্ণ বন্ধ), ৩০০ms ফোরগ্রাউন্ড ওয়াচার (পাসওয়ার্ড ছাড়া ঢোকা বন্ধ), নির্ভুল দৈনিক টাইম লিমিট, শেষ ১ মিনিটের লাইভ কাউন্টডাউন + অটো এক্সিট ও হার্ড ব্লক, এবং শুধু আজকের জন্য এক্সট্রা সময় দেওয়ার সুবিধা!',
        apkUrl: 'https://github.com/umdbasir829-lgtm/New-app/releases/latest/download/ShieldGuard-AppLock.apk',
      },
      runtimeConfig: DEFAULT_RUNTIME_CONFIG,
    };
    fs.writeFileSync(STORE_FILE, JSON.stringify(initial, null, 2), 'utf-8');
    return initial;
  }
  const raw = fs.readFileSync(STORE_FILE, 'utf-8');
  const parsed = JSON.parse(raw) as VaultStore;
  let dirty = false;
  if (!parsed.releaseManifest || parsed.releaseManifest.versionCode < 9) {
    parsed.releaseManifest = {
      versionCode: 9,
      versionName: '2.5.0',
      policyRevision: (parsed.releaseManifest?.policyRevision || 1) + 1,
      publishedAt: new Date().toISOString(),
      changelog: 'v2.5.0: ০ms তাৎক্ষণিক লক ওভারলে (আগে অ্যাপ খুলে যাওয়া ও ডাবল পিন সম্পূর্ণ বন্ধ), ৩০০ms ফোরগ্রাউন্ড ওয়াচার (পাসওয়ার্ড ছাড়া ঢোকা বন্ধ), নির্ভুল দৈনিক টাইম লিমিট, শেষ ১ মিনিটের লাইভ কাউন্টডাউন + অটো এক্সিট ও হার্ড ব্লক, এবং শুধু আজকের জন্য এক্সট্রা সময় দেওয়ার সুবিধা!',
      apkUrl: parsed.releaseManifest?.apkUrl || 'https://github.com/umdbasir829-lgtm/New-app/releases/latest/download/ShieldGuard-AppLock.apk',
    };
    dirty = true;
  }
  if (!parsed.runtimeConfig) {
    parsed.runtimeConfig = DEFAULT_RUNTIME_CONFIG;
    dirty = true;
  }
  if (dirty) {
    writeStore(parsed);
  }
  return parsed;
}

function writeStore(store: VaultStore): void {
  if (!fs.existsSync(DATA_DIR)) {
    fs.mkdirSync(DATA_DIR, { recursive: true });
  }
  fs.writeFileSync(STORE_FILE, JSON.stringify(store, null, 2), 'utf-8');
}

function sanitizeStore(store: VaultStore) {
  return {
    hasVaultPassword: Boolean(store.vaultPasswordHash),
    hasAppLockPassword: Boolean(store.appLockPasswordHash),
    separateAppLockEnabled: store.separateAppLockEnabled,
    apps: store.apps,
    releaseManifest: store.releaseManifest,
    runtimeConfig: store.runtimeConfig || DEFAULT_RUNTIME_CONFIG,
  };
}

function collectAndroidFiles(dirPath: string, baseDir: string, list: Array<{ path: string; content: string }>) {
  if (!fs.existsSync(dirPath)) return;
  const entries = fs.readdirSync(dirPath, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = path.join(dirPath, entry.name);
    if (entry.isDirectory()) {
      collectAndroidFiles(fullPath, baseDir, list);
    } else {
      const relPath = path.relative(baseDir, fullPath).replace(/\\/g, '/');
      list.push({
        path: relPath,
        content: fs.readFileSync(fullPath, 'utf-8'),
      });
    }
  }
}

async function startServer() {
  const app = express();
  app.use(express.json({ limit: '1mb' }));

  app.get('/api/state', (_req, res) => {
    const store = readStore();
    res.json(sanitizeStore(store));
  });

  app.post('/api/auth/setup', (req, res) => {
    const { vaultPassword, appLockPassword, separateAppLockEnabled } = req.body as {
      vaultPassword?: string;
      appLockPassword?: string;
      separateAppLockEnabled?: boolean;
    };

    if (!vaultPassword || vaultPassword.length < 4) {
      res.status(400).json({ error: 'ভল্ট পাসওয়ার্ড কমপক্ষে ৪ অক্ষরের হতে হবে।' });
      return;
    }

    const store = readStore();
    if (store.vaultPasswordHash) {
      res.status(409).json({ error: 'ভল্ট পাসওয়ার্ড ইতিমধ্যে সেট করা আছে।' });
      return;
    }

    const useSeparate = separateAppLockEnabled !== false;
    if (useSeparate && (!appLockPassword || appLockPassword.length < 4)) {
      res.status(400).json({ error: 'আলাদা অ্যাপ-লক পাসওয়ার্ড কমপক্ষে ৪ অক্ষরের হতে হবে।' });
      return;
    }

    const vSalt = crypto.randomBytes(16).toString('hex');
    store.vaultSalt = vSalt;
    store.vaultPasswordHash = hashSecret(vaultPassword, vSalt);
    store.separateAppLockEnabled = useSeparate;

    if (useSeparate && appLockPassword) {
      const aSalt = crypto.randomBytes(16).toString('hex');
      store.appLockSalt = aSalt;
      store.appLockPasswordHash = hashSecret(appLockPassword, aSalt);
    } else {
      store.appLockSalt = null;
      store.appLockPasswordHash = null;
    }

    store.releaseManifest.policyRevision += 1;
    writeStore(store);
    res.json({ ok: true, state: sanitizeStore(store) });
  });

  app.post('/api/auth/verify-vault', (req, res) => {
    const { password } = req.body as { password?: string };
    const store = readStore();
    const ok = verifySecret(password || '', store.vaultPasswordHash, store.vaultSalt);
    res.json({ ok });
  });

  app.post('/api/auth/verify-applock', (req, res) => {
    const { password, isLimitOverride } = req.body as { password?: string; isLimitOverride?: boolean };
    const store = readStore();
    if (isLimitOverride || !store.separateAppLockEnabled || !store.appLockPasswordHash) {
      const ok = verifySecret(password || '', store.vaultPasswordHash, store.vaultSalt);
      res.json({ ok });
      return;
    }
    const ok = verifySecret(password || '', store.appLockPasswordHash, store.appLockSalt);
    res.json({ ok });
  });

  app.post('/api/auth/update-passwords', (req, res) => {
    const { currentVaultPassword, newVaultPassword, newAppLockPassword, separateAppLockEnabled } = req.body as {
      currentVaultPassword?: string;
      newVaultPassword?: string;
      newAppLockPassword?: string;
      separateAppLockEnabled?: boolean;
    };

    const store = readStore();
    if (!verifySecret(currentVaultPassword || '', store.vaultPasswordHash, store.vaultSalt)) {
      res.status(403).json({ error: 'বর্তমান ভল্ট মাস্টার পাসওয়ার্ড সঠিক নয়।' });
      return;
    }

    if (newVaultPassword && newVaultPassword.length > 0) {
      if (newVaultPassword.length < 4) {
        res.status(400).json({ error: 'নতুন ভল্ট পাসওয়ার্ড কমপক্ষে ৪ অক্ষরের হতে হবে।' });
        return;
      }
      const vSalt = crypto.randomBytes(16).toString('hex');
      store.vaultSalt = vSalt;
      store.vaultPasswordHash = hashSecret(newVaultPassword, vSalt);
    }

    if (typeof separateAppLockEnabled === 'boolean') {
      store.separateAppLockEnabled = separateAppLockEnabled;
    }

    if (newAppLockPassword && newAppLockPassword.length > 0) {
      if (newAppLockPassword.length < 4) {
        res.status(400).json({ error: 'নতুন অ্যাপ-লক পাসওয়ার্ড কমপক্ষে ৪ অক্ষরের হতে হবে।' });
        return;
      }
      const aSalt = crypto.randomBytes(16).toString('hex');
      store.appLockSalt = aSalt;
      store.appLockPasswordHash = hashSecret(newAppLockPassword, aSalt);
    }

    store.releaseManifest.policyRevision += 1;
    writeStore(store);
    res.json({ ok: true, state: sanitizeStore(store) });
  });

  app.post('/api/apps/toggle-lock', (req, res) => {
    const { id, isLocked } = req.body as { id?: string; isLocked?: boolean };
    const store = readStore();
    const target = store.apps.find((a) => a.id === id);
    if (!target) {
      res.status(404).json({ error: 'App not found' });
      return;
    }
    target.isLocked = typeof isLocked === 'boolean' ? isLocked : !target.isLocked;
    store.releaseManifest.policyRevision += 1;
    writeStore(store);
    res.json({ ok: true, state: sanitizeStore(store) });
  });

  app.post('/api/apps/set-limit', (req, res) => {
    const { id, dailyLimitMinutes } = req.body as { id?: string; dailyLimitMinutes?: number };
    const store = readStore();
    const target = store.apps.find((a) => a.id === id);
    if (!target) {
      res.status(404).json({ error: 'App not found' });
      return;
    }
    target.dailyLimitMinutes = Math.max(0, Math.round(Number(dailyLimitMinutes) || 0));
    store.releaseManifest.policyRevision += 1;
    writeStore(store);
    res.json({ ok: true, state: sanitizeStore(store) });
  });

  app.post('/api/apps/simulate-usage', (req, res) => {
    const { id, addMinutes } = req.body as { id?: string; addMinutes?: number };
    const store = readStore();
    const target = store.apps.find((a) => a.id === id);
    if (!target) {
      res.status(404).json({ error: 'App not found' });
      return;
    }
    target.usedMinutesToday = Math.max(0, target.usedMinutesToday + (Number(addMinutes) || 10));
    writeStore(store);
    res.json({ ok: true, state: sanitizeStore(store) });
  });

  app.post('/api/apps/reset-usage', (req, res) => {
    const { id } = req.body as { id?: string };
    const store = readStore();
    if (id) {
      const target = store.apps.find((a) => a.id === id);
      if (target) target.usedMinutesToday = 0;
    } else {
      store.apps.forEach((a) => {
        a.usedMinutesToday = 0;
      });
    }
    writeStore(store);
    res.json({ ok: true, state: sanitizeStore(store) });
  });

  app.post('/api/apps/add', (req, res) => {
    const { name, packageName, category } = req.body as {
      name?: string;
      packageName?: string;
      category?: string;
    };
    if (!name || !packageName) {
      res.status(400).json({ error: 'অ্যাপের নাম এবং প্যাকেজ নাম দিন।' });
      return;
    }
    const store = readStore();
    const cleanPkg = packageName.trim();
    if (store.apps.some((a) => a.packageName === cleanPkg)) {
      res.status(409).json({ error: 'এই প্যাকেজ নামের অ্যাপ ইতিমধ্যে তালিকায় আছে।' });
      return;
    }
    store.apps.unshift({
      id: `${cleanPkg.replace(/[^a-zA-Z0-9]/g, '_')}_${Date.now()}`,
      name: name.trim(),
      packageName: cleanPkg,
      category: category?.trim() || 'Installed',
      isLocked: true,
      dailyLimitMinutes: 30,
      usedMinutesToday: 0,
    });
    store.releaseManifest.policyRevision += 1;
    writeStore(store);
    res.json({ ok: true, state: sanitizeStore(store) });
  });

  app.get('/api/updates/latest', (_req, res) => {
    const store = readStore();
    res.json(store.releaseManifest);
  });

  app.get('/api/ota/runtime-config', (_req, res) => {
    const store = readStore();
    res.json(store.runtimeConfig || DEFAULT_RUNTIME_CONFIG);
  });

  app.post('/api/ota/runtime-config', (req, res) => {
    const body = req.body as Partial<RuntimeConfig>;
    const store = readStore();
    const prev = store.runtimeConfig || DEFAULT_RUNTIME_CONFIG;
    store.runtimeConfig = {
      configVersion: prev.configVersion + 1,
      accentBlue: typeof body.accentBlue === 'string' ? body.accentBlue : prev.accentBlue,
      accentGreen: typeof body.accentGreen === 'string' ? body.accentGreen : prev.accentGreen,
      homeAnnouncement: typeof body.homeAnnouncement === 'string' ? body.homeAnnouncement : prev.homeAnnouncement,
      lockSubtitle: typeof body.lockSubtitle === 'string' ? body.lockSubtitle : prev.lockSubtitle,
      heroActiveTitle: typeof body.heroActiveTitle === 'string' ? body.heroActiveTitle : prev.heroActiveTitle,
    };
    writeStore(store);
    res.json({ ok: true, runtimeConfig: store.runtimeConfig });
  });

  app.post('/api/updates/publish', (req, res) => {
    const { versionName, changelog, apkUrl } = req.body as {
      versionName?: string;
      changelog?: string;
      apkUrl?: string;
    };
    const store = readStore();
    store.releaseManifest = {
      versionCode: store.releaseManifest.versionCode + 1,
      versionName: versionName?.trim() || `1.0.${store.releaseManifest.versionCode}`,
      policyRevision: store.releaseManifest.policyRevision + 1,
      publishedAt: new Date().toISOString(),
      changelog: changelog?.trim() || 'নতুন নিরাপত্তা ও ফিচার আপডেট যুক্ত হয়েছে।',
      apkUrl: typeof apkUrl === 'string' ? apkUrl.trim() : store.releaseManifest.apkUrl,
    };
    writeStore(store);
    res.json({ ok: true, releaseManifest: store.releaseManifest });
  });

  app.get('/api/android-project/files', (_req, res) => {
    const files: Array<{ path: string; content: string }> = [];
    collectAndroidFiles(ANDROID_DIR, ANDROID_DIR, files);
    files.sort((a, b) => a.path.localeCompare(b.path));
    res.json({ files });
  });

  app.get('/api/android-project.zip', async (_req, res) => {
    const zip = new JSZip();
    const files: Array<{ path: string; content: string }> = [];
    collectAndroidFiles(ANDROID_DIR, ANDROID_DIR, files);
    for (const f of files) {
      zip.file(f.path, f.content);
    }
    const buffer = await zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
    res.setHeader('Content-Type', 'application/zip');
    res.setHeader('Content-Disposition', 'attachment; filename="ShieldGuard-Android-Source.zip"');
    res.send(buffer);
  });

  if (process.env.NODE_ENV !== 'production') {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distDir = path.resolve(__dirname, 'dist');
    app.use(express.static(distDir));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distDir, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`ShieldGuard server listening on http://localhost:${PORT}`);
  });
}

startServer().catch((err) => {
  console.error('Failed to start server:', err);
  process.exit(1);
});
