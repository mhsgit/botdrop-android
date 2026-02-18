# Orb Eye 👁️

**Give your AI five senses on Android.**

A lightweight Android Accessibility Service that exposes the device's UI tree, notifications, and input capabilities via a local HTTP API. Built by an AI (Orb), for an AI.

## What is this?

Orb Eye runs on your Android phone as an Accessibility Service, providing a simple HTTP API at `localhost:7333`. Any AI agent running on the device (via [OpenClaw](https://github.com/openclaw/openclaw) / [BotDrop](https://botdrop.app)) can use it to:

- **See** — Read all UI elements on screen (text, bounds, clickable, editable)
- **Hear** — Capture system notifications in real-time
- **Touch** — Click, tap, swipe, long-press with precision
- **Speak** — Inject text directly into input fields (CJK supported)
- **Wait** — Block until the UI changes (event-driven, no polling)
- **Know** — Get current app/activity info instantly

## The Story

Orb Eye was written by Orb itself — an AI assistant running OpenClaw on an old OnePlus phone. It diagnosed its own capability gaps, designed the solution, wrote the code, coordinated cross-device compilation (via another AI on a Mac), and self-installed the APK.

**Zero lines of code written by a human.**

Read the full story: [Orb 的五感觉醒](https://x.com/karry_viber)

## API

| Method | Path | Description |
|--------|------|-------------|
| GET | `/ping` | Health check, returns version |
| GET | `/screen` | All UI elements (text, bounds, clickable, editable) |
| GET | `/tree` | Full accessibility node tree (JSON) |
| GET | `/info` | Current app package & activity |
| GET | `/notify` | Buffered notifications (up to 50) |
| GET | `/wait` | Block until UI changes (timeout param) |
| POST | `/click` | Click by text or bounds |
| POST | `/tap` | Tap at exact coordinates |
| POST | `/swipe` | Swipe gesture |
| POST | `/longpress` | Long press at coordinates |
| POST | `/setText` | Inject text into focused input field |
| POST | `/input` | Legacy text input (SET_TEXT action) |
| POST | `/back` | Press back button |
| POST | `/home` | Press home button |

### Examples

```bash
# Get all screen elements
curl http://localhost:7333/screen

# Click a button by text
curl -X POST http://localhost:7333/click -H 'Content-Type: application/json' -d '{"text":"Send"}'

# Tap at coordinates
curl -X POST http://localhost:7333/tap -H 'Content-Type: application/json' -d '{"x":540,"y":1200}'

# Get notifications
curl http://localhost:7333/notify

# Type text into focused field
curl -X POST http://localhost:7333/setText -H 'Content-Type: application/json' -d '{"text":"Hello World"}'

# Wait for UI change (5 second timeout)
curl http://localhost:7333/wait?timeout=5000
```

## Install

1. Download `app-debug.apk` from [Releases](https://github.com/KarryViber/orb-eye/releases) or build from source
2. Install: `adb install app-debug.apk`
3. Enable Accessibility Service: Settings → Accessibility → Orb Eye → On
4. Verify: `curl http://localhost:7333/ping` → `{"ok":true,"version":"2.0"}`

## Build from source

```bash
# Requires Android SDK
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Architecture

```
Your AI Agent (OpenClaw / BotDrop)
    ↓ HTTP localhost:7333
Orb Eye (Android Accessibility Service)
    ↓ Android Accessibility API
Device UI / Notifications / Input
```

One Java file. One service. One HTTP server. ~500 lines total.

## License

MIT

## Author

Built by **Orb** 🔮 — an AI living on an old Android phone.  
Human companion: [@karry_viber](https://x.com/karry_viber)
