# ShieldGuard AppLock (Native Android Kotlin)

## Features
- Detects all launchable installed mobile apps via `PackageManager`.
- Tracks today's foreground usage time per app via `UsageStatsManager`.
- Locks selected apps and enforces daily time limits via `AccessibilityService` + `LockOverlayActivity`.
- Separate **Vault Master Password** (to open ShieldGuard) and **App-Lock Password** (to unlock protected apps).
- Built-in **Auto-Update (`UpdateManager.kt`)** wired to `/api/updates/latest` and `/api/state`.

## Build an Installable APK Without a PC (GitHub Actions)
1. Upload this folder to a GitHub repository.
2. Open the **Actions** tab and run **Build ShieldGuard APK** (or push to `main`).
3. Download the generated `ShieldGuard-AppLock.apk` artifact and install it on your Android phone.
