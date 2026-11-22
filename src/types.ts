export interface ManagedApp {
  id: string;
  name: string;
  packageName: string;
  category: string;
  isLocked: boolean;
  dailyLimitMinutes: number;
  usedMinutesToday: number;
}

export interface ReleaseManifest {
  versionCode: number;
  versionName: string;
  policyRevision: number;
  publishedAt: string;
  changelog: string;
  apkUrl: string;
}

export interface VaultState {
  hasVaultPassword: boolean;
  hasAppLockPassword: boolean;
  separateAppLockEnabled: boolean;
  apps: ManagedApp[];
  releaseManifest: ReleaseManifest;
}

export interface AndroidSourceFile {
  path: string;
  content: string;
}

export type ActiveTab = 'apps' | 'passwords' | 'simulator' | 'apk';
