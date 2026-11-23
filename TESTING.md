# ReelGuard — Comprehensive Test Plan & Verification Report

This document outlines the testing strategy, executed test suites, automated unit tests, and the manual physical-device testing protocol for **ReelGuard**.

---

## 1. Automated Test Suites Summary

ReelGuard is built with pure decoupled domain and engine logic, allowing 100% of business logic, state machines, detectors, and aggregation logic to run on the JVM without mocking frameworks or Android emulators.

| Suite | Component | Scope / Invariants Verified | Tests | Status |
|---|---|---|:---:|:---:|
| `AppConfigTest` | Global Configuration | Tracked package IDs (Instagram, YouTube, Facebook), shared preference key names, default limits (30m / 40 reels). | 3 | **PASS** |
| `SupportedAppTest` | Domain Models | Package lookup by ID, null-safety for untracked apps, correct terminology mapping ("Reels" vs "Shorts"). | 5 | **PASS** |
| `AppUsageDataTest` | Domain Calculations | Remaining time & reels math, warning state triggers, limit reached states, clean `HH:mm` / `mm:ss` duration formatting. | 7 | **PASS** |
| `DashboardUiStateTest` | UI State Management | Empty state handling, total screen time and reels aggregation, highest consumption identification, zero-division safety. | 4 | **PASS** |
| `PermissionTypeTest` | Permission Onboarding | Required intent action targets, transparent rationale strings, mandatory permission categorization. | 5 | **PASS** |
| `UsageTrackerEngineTest` | Tracking Engine | Event processing, app-switching attribution, screen lock (SCREEN_OFF / KEYGUARD) pausing, duplicate timestamp filtering, elapsed tick clamping, midnight rollover reset. | 12 | **PASS** |
| `PlatformReelDetectorTest` | Detection Engine | 12 scenarios: genuine transitions, repeated/redraw event suppression, scroll transitions, comments/profile exclusions, backgrounding reset, safe fallback on unknown/null nodes, separate platform counters, persistence, daily reset, and rapid event debouncing. | 12 | **PASS** |
| `OverlayStateControllerTest` | Floating Overlay | State styling transitions (NORMAL -> WARNING -> LIMIT_REACHED), label formatting, dismiss callback handling, initial window parameters. | 5 | **PASS** |
| `LimitStateMachineTest` | Limits & Alerts | Single-fire alert semantics per threshold, duplicate notification suppression, custom `TriggerPolicy` enforcement (`TIME_ONLY`, `REELS_ONLY`, `WHICHEVER_FIRST`, `BOTH_REQUIRED`). | 8 | **PASS** |
| `BreakExperienceManagerTest` | Break Experience | Countdown progression (5s -> 0s), dismissal unlock, snooze extension timer, navigation intent target verification. | 3 | **PASS** |
| `WeeklySummaryTest` | History & Trends | 7-day rolling window aggregation, daily peak computation, zero-usage handling, per-app breakdown totals. | 3 | **PASS** |
| `ActiveAppTrackerTest` | Active Tracking | Real-time interaction tracking, accessibility window correlation, timeout window validation, state resets. | 5 | **PASS** |
| `PrivacyAndDataDeletionTest` | Privacy & Security | Zero `INTERNET` permission in manifest, `android:allowBackup="false"`, non-exported internal services, password node exclusion, 100% local data purge. | 8 | **PASS** |
| **Total** | | **All Automated Test Suites** | **80** | **PASS (100%)** |

---

## 2. Test Execution Commands

### Command Line (Windows PowerShell)
```powershell
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest assembleDebug
```

### Android Studio
- **Unit Tests**: Right-click `app/src/test/java` -> Select **Run 'Tests in 'com.example.reelguard''**.
- **Build APK**: Build -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**.

---

## 3. Edge Cases & Resiliency Matrix

| Edge Case Scenario | Handled Mechanism | Test Verification |
|---|---|---|
| **App Switching** | `UsageTrackerEngine` receives `MOVE_TO_FOREGROUND` from an untracked app or home screen and immediately finalizes and flushes active sessions. | `UsageTrackerEngineTest#engine_switchesForegroundApp_closesPreviousInterval` |
| **Screen Lock** | Android sends `SCREEN_OFF` or `KEYGUARD_SHOWN`. The engine pauses active accumulation without double counting. | `UsageTrackerEngineTest#engine_screenOffPausesTracking` |
| **Midnight Rollover** | Usage events crossing 00:00 UTC/local date partition usage into the prior day up to midnight, and resets current day metrics to zero. | `UsageTrackerEngineTest#engine_midnightRollover_resetsDailyUsage` |
| **Rapid Swiping** | Swiping 10 reels in 3 seconds is debounced using minimum display duration (500ms) and unique layout content signatures. | `PlatformReelDetectorTest#instagram_rapidSwipe_debouncesDuplicateEvents` |
| **Sub-screen Navigation** | Opening comments, audio pages, direct messages, or profiles suppresses count increments. | `PlatformReelDetectorTest#instagram_commentsScreen_doesNotTriggerTransition` |
| **Password & Auth Inputs** | Accessibility node snapshots detect `isPassword == true` and immediately mask the node without reading content. | `PrivacyAndDataDeletionTest#nodeSnapshot_flagsPasswordFieldsAsSensitive` |
| **Permission Revocation** | When overlay or usage permissions are revoked while running, the service safely deactivates UI overlays without crashing. | `PermissionTypeTest#allPermissions_haveSettingsIntentActions` |
| **Service Restart** | When the background service is restarted by the OS, it restores baseline usage from `UsageStore` SharedPreferences. | `DailyUsageRecord` & `UsageStore` integration |

---

## 4. Manual Physical-Device Testing Checklist

Because Accessibility services, overlays, and system window managers exhibit hardware and OEM-dependent behaviors, execute this manual checklist on a physical device (Android 8.0 through Android 15):

### Step 1: Permission Onboarding
- [ ] Install `app-debug.apk` onto a test device.
- [ ] Launch ReelGuard. Verify the Dashboard shows the permission status banner.
- [ ] Tap **Permissions** tab. Confirm all 4 permissions (Usage Access, Display Over Other Apps, Accessibility Service, Notifications) display informative rationale cards.
- [ ] Tap **Grant** for Usage Access -> redirects to system "Usage access" screen -> grant -> return to app -> indicator changes to green checkmark.
- [ ] Tap **Grant** for Overlay -> redirects to "Appear on top" -> grant -> return -> green checkmark.
- [ ] Tap **Grant** for Accessibility -> redirects to "Accessibility" -> tap ReelGuard -> enable -> return -> green checkmark.
- [ ] Tap **Grant** for Notifications -> system dialog displays -> Allow -> green checkmark.
- [ ] Background/Resume app -> Verify permission state refreshes automatically in `onResume()`.

### Step 2: Foreground Time Tracking
- [ ] Open Instagram. Spend exactly 2 minutes scrolling feeds.
- [ ] Open ReelGuard. Verify Instagram time shows `0h 02m` (or 120s) with progress bar updated.
- [ ] Switch to a non-tracked app (e.g., Settings or Calculator) for 2 minutes.
- [ ] Reopen ReelGuard. Confirm Instagram usage remained at 2 minutes and did not increment while in background.
- [ ] Turn screen off with power button for 1 minute. Turn back on. Verify screen-off time was not counted.

### Step 3: Best-Effort Reel & Short Counting
- [ ] Open Instagram -> Navigate to the Reels tab.
- [ ] Verify the floating overlay pill appears with app icon and initial count (e.g., `0 Reels`).
- [ ] Swipe up to the next Reel. Verify counter increments to `1 Reel`.
- [ ] Tap the Comments button. Read comments for 10 seconds. Verify counter does NOT increment.
- [ ] Tap user profile. Return to feed. Verify counter does NOT increment.
- [ ] Open YouTube -> Navigate to Shorts. Verify pill switches to Shorts count.
- [ ] Swipe through 3 Shorts. Verify count increments to 3.
- [ ] Open Facebook -> Navigate to Reels. Verify Reel transitions increment counter.

### Step 4: Overlay Interaction & Dragging
- [ ] Touch and hold the floating overlay pill.
- [ ] Drag across the screen. Verify smooth dragging without jumping or stutter.
- [ ] Tap the small `✕` close button on the overlay. Verify overlay disappears for the session.

### Step 5: Warning & Limit Enforcement
- [ ] In ReelGuard **Limits** tab, set Instagram limit to **1 Minute** and **2 Reels**, with **Warning at 1 Reel**.
- [ ] Return to Instagram Reels.
- [ ] Swipe to Reel #1: Floating overlay turns **Yellow (Warning)** and a reminder notification is posted.
- [ ] Swipe to Reel #2 (or wait for 1 minute):
  - Floating overlay turns **Red (Limit Reached)**.
  - Full-screen `BreakReminderActivity` launches immediately.
  - A 5-second mindful countdown prevents immediate bypassing.
  - Tapping **Take a Break** returns user safely to ReelGuard Dashboard.
  - Tapping **Snooze (+5 min)** dismisses the break screen and grants 5-minute extension.

### Step 6: 7-Day History & Weekly Statistics
- [ ] Open ReelGuard **Statistics** tab.
- [ ] Verify 7-day summary displays: Total Week Time, Total Week Reels, Daily Average, and Peak Day.
- [ ] Verify horizontal 7-day bar chart renders with proportional bar heights.
- [ ] Verify per-app breakdowns show separate rows for Instagram, YouTube, and Facebook.

### Step 7: Privacy & Reset
- [ ] In **Settings** tab, tap **Reset Today's Counters**.
- [ ] Return to Dashboard. Verify today's seconds and reels reset to 0.
- [ ] In **Settings**, tap **Clear All Data & History**.
- [ ] Confirm alert dialog. Verify all history, limits, and records are completely deleted.
- [ ] Check device Settings -> App permissions -> Confirm ReelGuard requests zero network permissions.
