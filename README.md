# ReelGuard — Mindful Short-Video Control for Android

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026--35)-brightgreen.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Language-Kotlin%202.0.21-blue.svg)](https://kotlinlang.org)
[![Build](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](#building--running)
[![Tests](https://img.shields.io/badge/Tests-80%2F80%20Passing-success.svg)](#testing)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline-orange.svg)](#privacy--security-guarantees)

**ReelGuard** is a native, privacy-first Android application engineered to help users regain control over short-form video consumption across **Instagram Reels**, **YouTube Shorts**, and **Facebook Reels**.

ReelGuard combines foreground time tracking (`UsageStatsManager`) with user-consented, localized accessibility heuristics (`AccessibilityService`) to detect feed transitions, present a non-intrusive floating HUD counter (`WindowManager`), deliver early threshold warnings, and trigger mindful break reminders when daily limits are reached.

---

## Architecture Overview

ReelGuard follows a clean, decoupled, layered Android architecture that strictly isolates platform frameworks from testable business logic:

```
┌────────────────────────────────────────────────────────────────────────┐
│                              UI LAYER                                  │
│   MainActivity (Chip Navigation)   │   BreakReminderActivity (Mindful) │
│   • Dashboard                      │   • 5s Breathing Countdown        │
│   • Supported Apps & Limits        │   • Snooze & Home Navigation      │
│   • 7-Day History & Statistics     │                                   │
│   • Permission Onboarding & Settings                                   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                           SERVICES & HUD                               │
│   UsageMonitorService (Foreground Service - specialUse type)           │
│   OverlayController (Window Overlay HUD: Normal, Warning, Limit)      │
│   ReelAccessibilityService (Locally scoped node event ingestion)       │
└─────────────────┬─────────────────────────────────┬────────────────────┘
                  │                                 │
┌─────────────────▼──────────────────┐   ┌──────────▼────────────────────┐
│      DETECTION & HEURISTICS        │   │       LIMITS & ENGINE         │
│   PlatformDetectorRegistry         │   │   UsageTrackerEngine          │
│   • InstagramReelDetector          │   │   • Screen off/on filtering   │
│   • YouTubeShortsDetector          │   │   • App switching interval    │
│   • FacebookReelDetector           │   │   • Midnight rollover         │
│   NodeSnapshot (Pure immutables)   │   │   LimitStateMachine           │
└─────────────────┬──────────────────┘   └──────────┬────────────────────┘
                  │                                 │
┌─────────────────▼─────────────────────────────────▼────────────────────┐
│                           DATA & REPOSITORY                            │
│   UsageStore (Fast SharedPreferences cache: today's seconds & reels)   │
│   PreferencesUsageRepository (7-Day Rolling History & Weekly Summary)  │
│   DailyUsageRecord & WeeklySummary (Pure domain aggregations)          │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Key Features & How It Works

### 1. Foreground Screen Time Tracking
- **API**: Uses Android's `UsageStatsManager` query APIs.
- **Engine**: The background `UsageMonitorService` runs on a dedicated background worker thread with periodic polling.
- **Resiliency**:
  - Handles screen-off (`Intent.ACTION_SCREEN_OFF`) and device sleep without double-counting intervals.
  - Detects app switching immediately when user exits to the home launcher or another application.
  - Automatically resets daily metrics at midnight (`00:00:00`) without requiring an app relaunch.

### 2. Short-Form Video Feed Detection
- **API**: User-enabled `AccessibilityService` configured exclusively for target package IDs:
  - `com.instagram.android`
  - `com.google.android.youtube`
  - `com.facebook.katana`
- **Detection Heuristics**:
  - Converts active accessibility node hierarchies into lightweight, immutable `NodeSnapshot` trees.
  - Platform-specific detectors (`InstagramReelDetector`, `YouTubeShortsDetector`, `FacebookReelDetector`) inspect view IDs, content descriptions, and layout containers.
  - Computes transition signatures to differentiate scrolling from screen redraws.
  - **False Positive Rejection**: Automatically detects and ignores non-feed screens:
    - Comment drawers and reply sheets
    - User profile pages
    - Audio and sound picker pages
    - Direct messages / chat
    - Search results and explore feeds
  - **Debouncing**: Enforces minimum view durations (500ms) to ignore high-speed flick gestures.

### 3. Floating HUD Counter
- **API**: `WindowManager` with `TYPE_APPLICATION_OVERLAY`.
- **Interactivity**:
  - Draggable: Can be freely dragged and positioned anywhere on screen.
  - Dismissible: Tap the small `✕` to hide the overlay for the current session.
  - Dynamic Color Coding:
    - 🟢 **Normal**: Displays current reel/short count and app icon.
    - 🟡 **Warning**: Activates when approaching threshold (e.g., 80% of limit or 3 reels remaining).
    - 🔴 **Limit Reached**: Changes to high-contrast alert styling when quota is exhausted.

### 4. Mindful Break Screen
- When a configured limit is exceeded, `BreakReminderActivity` presents a mindful intervention:
  - Full-screen distraction barrier.
  - 5-second breathing countdown to disrupt reflexive consumption loops.
  - One-tap navigation back to the safe ReelGuard Dashboard.
  - Optional 5-minute Snooze for critical tasks.

### 5. 7-Day History & Weekly Trends
- Daily records are persisted locally across a 7-day rolling window.
- Weekly summary aggregates:
  - Total weekly screen time
  - Total reels/shorts consumed
  - Daily average
  - Peak consumption day
  - Visual 7-day horizontal trend bar chart.

---

## Required Permissions & Rationales

| Permission | Android ID | Why ReelGuard Needs It |
|---|---|---|
| **Usage Access** | `android.permission.PACKAGE_USAGE_STATS` | Measures time spent inside Instagram, YouTube, and Facebook to enforce daily time limits. |
| **Display Over Other Apps** | `android.permission.SYSTEM_ALERT_WINDOW` | Displays the non-intrusive floating reel counter HUD while browsing supported apps. |
| **Accessibility Service** | `android.permission.BIND_ACCESSIBILITY_SERVICE` | Detects short-video feed transitions locally on-device. Never reads personal chats, passwords, or search queries. |
| **Notifications** | `android.permission.POST_NOTIFICATIONS` | Delivers early warning notifications before limits are reached. |
| **Foreground Service** | `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | Keeps time-tracking active in the background for digital wellbeing monitoring (Android 14+ compliant). |

---

## Privacy & Security Guarantees

ReelGuard is built with an absolute **zero-data-exfiltration** guarantee:

1. **100% Offline**: The application does **NOT** declare `android.permission.INTERNET`. It is physically impossible for ReelGuard to send any usage data, analytics, or screen content over the network.
2. **Local-Only Storage**: All data is stored in device-private `SharedPreferences` (`MODE_PRIVATE`).
3. **No Cloud Backup**: Declares `android:allowBackup="false"` in `AndroidManifest.xml` to prevent viewing habits from leaking into third-party cloud backups.
4. **Password & Sensitive Input Masking**: `ReelAccessibilityService` inspects `node.isPassword`; any password field is instantly masked and skipped.
5. **Scoped Accessibility**: `reel_accessibility_config.xml` strictly limits package listening to the 3 target apps. ReelGuard never receives accessibility events from banking, messaging, or browser applications.
6. **Complete Data Purge**: Users can permanently erase all historical data and reset counters at any time with a single tap in **Settings > Clear All Data & History**.

---

## Building & Running

### Prerequisites
- Android Studio Ladybug (2024.2.1+) or newer
- JDK 17, 21, or 25
- Android SDK Platform 35
- Physical device running Android 8.0 (API 26) through Android 15 (API 35)

### Build Commands (CLI)
```powershell
# Set Java 21+ Home if necessary
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Run all 70 unit tests
.\gradlew.bat testDebugUnitTest

# Assemble Debug APK
.\gradlew.bat assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

### Installation
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## Testing

ReelGuard includes a comprehensive automated test suite with **70 tests across 12 suites**, with 100% pass rate:

- `AppConfigTest` — Configuration constants & package mapping
- `SupportedAppTest` — Target app metadata & terminology
- `AppUsageDataTest` — Duration formatting, warning math & limit status
- `DashboardUiStateTest` — Aggregate metrics & empty state resiliency
- `PermissionTypeTest` — Settings intent mapping & disclosures
- `UsageTrackerEngineTest` — Session tracking, screen-off, app-switching & midnight resets
- `PlatformReelDetectorTest` — Feed detection, debouncing & screen filtering
- `OverlayStateControllerTest` — HUD states, styling & dismissal
- `LimitStateMachineTest` — Once-per-threshold alert firing & trigger policies
- `BreakExperienceManagerTest` — Countdown progression & snooze logic
- `WeeklySummaryTest` — 7-day rolling window analytics & peak day computation
- `PrivacyAndDataDeletionTest` — Manifest security, no-internet validation & data purge

See [TESTING.md](file:///c:/Users/suman/Downloads/ReelGuardStarter/TESTING.md) for the complete manual physical-device testing protocol.

---

## Known Limitations & Device Quirks

- **Detection is Heuristic, Not an Official API**: Android does not provide an official API for counting videos inside 3rd-party apps. Reel detection relies on observable accessibility node structures and can require adjustments when social apps roll out significant UI redesigns.
- **OEM Battery Optimizations**: Certain aggressive OEM skins (e.g., Xiaomi MIUI/HyperOS, Samsung OneUI, Huawei EMUI) may terminate background foreground services. Users may need to set ReelGuard battery usage to "Unrestricted".
- **Overlay Window Permissions**: Android restricts floating overlays over sensitive system dialogs (e.g., permission prompts, package installer). The overlay will automatically hide during those system interactions.
- **Mindful Intervention vs OS-Enforced Lockout**: ReelGuard is a mindful wellbeing companion, not a root-level parental lock. Users retain ultimate device ownership and can bypass the break screen via Snooze or system navigation.

---

## Release Checklist

- [x] All 70 unit tests passing cleanly (`testDebugUnitTest`).
- [x] Debug APK builds with 0 errors (`assembleDebug`).
- [x] `AndroidManifest.xml` verified: zero `INTERNET` permission, `allowBackup="false"`, non-exported components.
- [x] Target SDK 35 compliance: `specialUse` foreground service type and subtype documentation.
- [x] Google Play compliant privacy disclosures in `strings.xml` and in-app Onboarding UI.
- [x] 100% local-only storage verified; full data purge tested.
- [x] Comprehensive test plan and physical device test checklist in `TESTING.md`.
