# TwinsApp — Product Requirements Document

**Version:** 0.1 (MVP)
**Author:** Frank Lee
**Last updated:** 2026-04-04
**Status:** Draft

---

## 1. Problem Statement

WhatsApp restricts each account to a single active phone session at a time. Users who carry two phones — or want to use WhatsApp on a secondary device — have no official way to do so without logging out of their primary phone.

WhatsApp Web (`web.whatsapp.com`) is the official workaround, but it is designed for desktop browsers and is difficult to use on a mobile screen.

---

## 2. Solution

**TwinsApp** is an Android application that loads WhatsApp Web inside a native mobile browser shell (WebView), making it fully usable on a phone. It adds a mobile-friendly single-column layout so the interface feels natural on a small screen.

Think of it like this: WhatsApp Web already does the hard work (authentication, messaging, media). TwinsApp is just a better mobile window into it.

---

## 3. Goals

| Goal | Metric |
|---|---|
| Users can log in via QR code and use WhatsApp on a second phone | QR scan works end-to-end |
| Interface is comfortable to use on a 6" Android screen | No horizontal scrolling; chat list and chat view both usable |
| App stays connected in the background | Session survives app minimize for ≥ 30 min |
| MVP can be installed as an APK (sideload) | Installable on Android 10+ without Play Store |

**North Star:** Paid app distributed on Google Play Store.

---

## 4. Non-Goals (MVP)

- iOS support (future phase)
- Multiple WhatsApp accounts simultaneously
- Custom notification system (rely on WhatsApp Web's browser notifications)
- End-to-end encryption at the app layer (WhatsApp handles this)
- Offline mode
- Any feature NOT already in WhatsApp Web

---

## 5. Target Users

**Primary:** Android users who own or carry two phones and want WhatsApp active on both simultaneously.

**Secondary:** Users who want a dedicated WhatsApp app on a tablet or secondary Android device.

---

## 6. Feature Scope (MVP)

All features mirror what is available in WhatsApp Web. TwinsApp does not add or remove WhatsApp functionality — it is a presentation layer.

### 6.1 Included (via WhatsApp Web)
- QR code login / session pairing
- Text messaging (send & receive)
- Media: images, video, documents, voice notes
- Emoji, stickers, GIF support
- Group chats
- Voice and video calls (WebRTC, browser-based)
- Read receipts, typing indicators
- Search
- Archived chats
- Status updates (view & post)
- Star/delete/forward messages
- Profile settings

### 6.2 TwinsApp-specific (app layer)
- Single-column mobile layout (chat list → tap → open chat, back button returns to list)
- Full-screen immersive mode (hide browser chrome)
- Back-button navigation (Android hardware/gesture back)
- Persistent session (WebView retains cookies/session across app restarts)
- Splash screen / loading state while WhatsApp Web initializes
- Camera & microphone permissions handled natively

---

## 7. Technical Approach

### Why Native Android WebView (not React Native / Flutter)
For a first-time mobile developer, native Android with a WebView is the simplest path:
- Very little code required (~100–200 lines of Kotlin)
- No extra frameworks to install or learn
- Direct access to Android's WebView APIs (camera, mic, file picker)
- Produces a standard APK ready for sideload or Play Store

**Analogy for a Python developer:** Think of it like writing a very thin Python script that just calls a library — the library (WhatsApp Web) does all the real work.

### Tech Stack
| Layer | Technology |
|---|---|
| Language | Kotlin |
| IDE | Android Studio (free) |
| UI | Single `Activity` + `WebView` |
| Target API | Android 10 (API 29) minimum, Android 15 (API 35) target |
| Build system | Gradle (Android Studio manages this automatically) |

### How it works (simplified)
```
User opens TwinsApp
    → App launches a fullscreen WebView
    → WebView loads web.whatsapp.com
    → User scans QR code with their primary phone
    → WhatsApp Web session starts (all logic handled by WhatsApp's servers)
    → TwinsApp injects CSS to reflow the two-column layout into one column
    → User interacts normally
```

### Layout Reflow Strategy
WhatsApp Web uses a two-panel desktop layout (chat list on left, active chat on right). On mobile:
- Inject a small CSS snippet via `WebView.evaluateJavascript()` to hide the left panel when a chat is open
- Show the left panel (chat list) by default; when user taps a chat, right panel goes fullscreen
- Android back button restores the chat list view

---

## 8. Screens & User Flow

```
[Splash Screen]
    ↓ (WebView loads)
[QR Code Screen]  ← WhatsApp Web's own QR screen, fullscreen
    ↓ (scan with primary phone)
[Chat List]       ← single column, full screen
    ↓ (tap a chat)
[Chat View]       ← single column, full screen
    ↓ (press back)
[Chat List]
```

No custom screens need to be designed for MVP — WhatsApp Web renders everything. TwinsApp only needs:
1. A splash/loading screen (simple logo + spinner)
2. The WebView (takes up 100% of the screen)

---

## 9. Permissions Required

| Permission | Reason |
|---|---|
| `INTERNET` | Load WhatsApp Web |
| `CAMERA` | QR scan + video calls |
| `RECORD_AUDIO` | Voice messages + voice/video calls |
| `READ_EXTERNAL_STORAGE` / `READ_MEDIA_*` | Send media files |
| `WRITE_EXTERNAL_STORAGE` | Download received media |

---

## 10. MVP Milestones

| # | Milestone | Description |
|---|---|---|
| 1 | **Hello World APK** | Android Studio project created, installable on a real phone |
| 2 | **Basic WebView** | App loads `web.whatsapp.com`, QR code scannable |
| 3 | **Permissions** | Camera, mic, file picker all work |
| 4 | **Session persistence** | Closing and reopening the app keeps you logged in |
| 5 | **Layout reflow** | Single-column navigation with back button |
| 6 | **Polish** | Splash screen, app icon, app name |
| 7 | **Sideload release** | Signed APK ready to install and share |

---

## 11. Risks & Mitigations

| Risk | Likelihood | Mitigation |
|---|---|---|
| WhatsApp detects WebView user-agent and blocks access | Medium | Spoof the user-agent string to match Chrome on Android |
| WhatsApp ToS violation leads to account bans | Low–Medium | Users accept risk; app is a thin wrapper, not modifying the protocol |
| Google Play Store rejects the app | Medium | Start with sideload; review Play Store policies before submission; may need to brand carefully |
| WhatsApp Web changes its layout, breaking CSS reflow | Medium | CSS injection is minimal; easy to update |
| Voice/video calls don't work in WebView | Low–Medium | Android WebView supports WebRTC since Android 5; test early |

---

## 12. Out of Scope for v1.0 (Future Phases)

- iOS version (Swift / React Native)
- Push notifications via native Android (requires WhatsApp Web API access)
- Multiple account support
- Custom themes / dark mode toggle
- Tablet-optimized two-column layout
- In-app subscription / payment (for Play Store paid app)

---

## 13. Open Questions

1. **App name & branding:** "TwinsApp" — confirm this is final before Play Store submission.
2. **Pricing:** What price point for the paid Play Store version?
3. **User-agent spoofing:** Test early to confirm WhatsApp Web loads properly in WebView vs. showing a "browser not supported" warning.
4. **Notification delivery:** WhatsApp Web relies on the tab being open for notifications. Behavior when app is minimized needs testing.
