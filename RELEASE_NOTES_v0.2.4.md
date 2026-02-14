## v0.2.4

### Fixes
- Fix app update detection edge case where version checks showed stale results across app upgrades.
- Improve update check throttling: update checks now compare against the current installed version and avoid skipping checks right after a version bump.
- Add explicit "no update" callbacks so update banners are hidden cleanly when no new version is available.
- Harden version parsing for tags like `v0.2.4` / pre-release suffixes to keep comparison correct.

### Notifications
- Add background update polling in `GatewayMonitorService` (6-hour interval) to improve visibility when an update is available while app is running in background.
- Add one-time in-app/system notification path for update availability with dismiss/version dedupe handling.

### Infrastructure
- Worker update endpoint now bypasses CDN cache for version queries to reduce stale version results.
