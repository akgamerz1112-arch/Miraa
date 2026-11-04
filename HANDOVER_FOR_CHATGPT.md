# MIRA - HANDOVER FOR CHATGPT / ANY OTHER AI (read fully before doing anything)

Written by Claude (Anthropic) for the user. The user works ONLY from an Android phone (Galaxy S20 FE, Android 13) with Termux and GitHub Actions. No laptop. Reply to the user in simple Hinglish, short mobile-friendly steps.

## 0. The most important thing: there are TWO different projects. Do not mix them.

| | MIRA (the user's project) | VenomAI / "Friday" (someone else's) |
|---|---|---|
| What | Native **Android** app, Kotlin + Jetpack Compose | **Windows PC** Python agent (LiveKit Agents + Gemini realtime) |
| Owner | the user (GitHub repo `akgamerz1112-arch/Mira`) | HunterisLive-1 (github.com/HunterisLive-1/venomai, YouTube channel HunterIsLive) |
| Where | `MIRA.zip` in this kit | public repo, not part of this kit |

Your prompt asks to "inspect the entire existing VenomAI project". The user's actual project is **MIRA (Android)**. VenomAI was only used as an IDEA reference (its README feature list and some screenshots of its `main.py`). If you inspect VenomAI you will be answering about the wrong codebase. Unzip `MIRA.zip` and inspect THAT.
Do not copy VenomAI code/prompts, and do not copy the commercial "Maya" app's code, prompts, voices or art (the user's screenshots of the Maya app are only a UI/feature reference). The Maya app's Romantic/Intense (18+) modes are NOT to be built.

## 1. Answers to your 7 questions, for MIRA (v0.6, versionName 0.6.0-emotion)
Evidence labels: SOURCE = I read/wrote the code; CI = GitHub Actions result the user showed me; NOT RUN = no test/device run. I (Claude) had NO Kotlin compiler and NO Android device, so I never ran any test. Your own test run is welcome, and be honest about what it shows.

**Size:** 3860 lines Kotlin in 27 files; 51 unit tests written (GeminiLiveProtocolTest 15, VoiceV2Test 10, V03Test 13, V04Test 7, V05Test 4, V06Test 2). All NOT RUN by me.

**Q1. Features implemented (in code):**
- Gemini Live voice over WebSocket: `voice/GeminiLiveProtocol.kt`, `voice/GeminiLiveProvider.kt`, `audio/MicStreamer.kt`, `audio/PcmPlayer.kt`. 16 kHz mic up, 24 kHz down, barge-in, session resumption, context compression, 8 voices, mute/stop, reconnect.
- Audio: `audio/AudioRouter.kt` (Auto / Headphone HQ / Headset / Speaker / Ear), `audio/AudioLogic.kt` (volume gain + soft limiter, mic gain 1x-4x).
- Persona: `voice/Persona.kt` (Hinglish, varied talk, per-session vibe, time of day, honesty rules, screen/control rules).
- Memory "Yaadein": `memory/MemoryLogic.kt` (add/dedupe/cap 60/secret filter/JSON), stored in `data/SecureStore.kt` (EncryptedSharedPreferences). Voice saves via function call `save_memory`; typed chat via hidden `[[YAAD: ...]]` tag.
- Typed Chat tab: `chat/TextChat.kt` (Gemini generateContent, default model `gemini-flash-latest`, editable).
- Notification buttons (mute, stop, screen off): `service/VoiceForegroundService.kt`.
- Screen live + control: `control/MiraAccessibilityService.kt` (takeScreenshot ~1/sec as live video input, tap, swipe, type, back/home/recents, open app), `control/ScreenControl.kt` (tool runner + `ScreenStreamer`), `control/ScreenLogic.kt`.
- Phone tools: `control/PhoneActions.kt` (YouTube search, volume, media keys, WhatsApp chat prefill, contact lookup).
- Emotion mode (affective dialog + proactive audio, 2.5 native-audio model, v1alpha): `voice/GeminiLiveProtocol.kt`, switch in `ui/MiraApp.kt`.
- UI: `ui/MiraApp.kt` (tabs Voice/Chat/Yaadein, Settings dialog), `VoiceViewModel.kt` (458 lines, holds almost all state).

**Q2. What actually works vs not verified:**
- CI (user's screenshots of the Actions list, repo Mira): runs #2-#8 green, among them "voice v0.2" (#3). Run #9 "MIRA v0.6" RED: one compile error `control/PhoneActions.kt:76:54 Classifier 'Phone' does not have a companion object`. That was my bug; fixed in the zip in this kit (imports `ContactsContract.CommonDataKinds.Phone` and uses it directly). The log the user pasted showed only that one `e:` line. I do NOT know yet whether the fixed build is green: ask the user.
- So: compiles = CI-evidenced up to the v0.2 era; v0.3-v0.6 code compiled except the one error above (inferred from one `e:` line, not from a green run). Nothing is DEVICE VERIFIED. No placeholder buttons were intentionally added, but "not run" is not "works".
- Unit tests: also run by CI (`Unit tests` step after the APK upload). The user has not shown me their result.

**Q3. Is Gemini Realtime voice working?** v0.1 voice: the user reported the APK installed; they said volume was low and Bluetooth audio came from the phone speaker (v0.1 bug, fixed in code in v0.2 AudioRouter, NOT device verified). Nobody has verified v0.2+ voice on the phone. Default model `gemini-3.8-live` and fields like `startOfSpeechSensitivity`, tools, video input came from Google docs / memory, never tested with a live key. Emotion mode must use `gemini-2.5-flash-native-audio-preview-12-2025` (Google's Live guide: affective dialog and proactive audio are not supported on 3.1 Flash Live; both need v1alpha). Where `enableAffectiveDialog` goes in the JSON (I put it in `setup.generationConfig`) is unverified; the provider falls back (retries once without tools/emotion) if the server rejects setup.

**Q4. Is memory persistent and user-specific?** Persistent: yes in code (encrypted prefs, survives app restarts). User-specific: it is per app install on one phone, no accounts. It is a plain list of up to 60 short facts (200 chars each), NOT semantic search, NOT summarised, no export. Facts are sent to Google in the prompt each session/chat message (the Yaadein screen says so). Typed chat history itself is NOT persisted. NOT RUN.

**Q5. Actions (the Android equivalents of PC/WhatsApp/YouTube):** tools declared to the model and implemented, all UNTESTED on a device: `screen_tap`, `screen_swipe`, `type_text` (refuses password fields), `press_button`, `open_app`, `youtube_search` (opens results only; video is picked via screen tools), `set_volume`, `media_control`, `whatsapp_message` (opens chat with text typed, does NOT press Send; user must say yes first, then the model taps Send). Not built: calls, SMS, file attach, screenshot sharing, camera vision. Android may block opening apps from the background; unverified.

**Q6. Missing for a complete Maya-style assistant:** camera "Scan" (live camera vision), Diagnostics screen, audio focus on phone calls, wake word, persisted chat, semantic/long-term memory with summaries, call/SMS, task agent/automation, proper onboarding (permissions, accessibility, "allow restricted settings" on Android 13), multiple voices preview, background reliability on Samsung, instrumented tests, signing key for stable updates. Maya's commercial "license/free-mode minutes" system is out of scope.

**Q7. Biggest architectural weaknesses (honest):**
1. `VoiceViewModel.kt` is a 458-line god-object (voice, chat, memory, screen, settings, notification). Split into use-cases.
2. API key is put in the WebSocket URL query (`?key=`); can leak into logs/proxies.
3. "Ask before sensitive actions" is only a PROMPT rule; code does not enforce a confirmation step. Screen text can try prompt injection (prompt says ignore it; not enforced).
4. Tools and emotion share one fallback switch; one rejection turns all extras off silently (it does tell the user on screen).
5. Screen "live" is a ~1/sec screenshot stream (takeScreenshot, Android 11+), not video; fragile on FLAG_SECURE/banking apps.
6. Control relies on AccessibilityService gestures with "OK = Android accepted the gesture", not "UI really changed"; verification depends on the model looking at the next frame.
7. No audio focus handling, no instrumented/UI tests, no CI lint step; chat not persisted; memory is a flat list.
8. Provider/model names change often (Google); several are hard-coded defaults.

## 2. Rules the user set (non-negotiable)
- Inspect the uploaded project first, keep all existing work, do not restart or reorganise without being asked.
- Short mobile-friendly steps in Hinglish.
- No API keys/tokens in the repo or in chat. Keys only in the app Settings.
- Never say "done/works" without evidence. Label: IMPLEMENTED / TESTED (CI or JVM) / DEVICE VERIFIED.
- No explicit/18+ mode. No copying of proprietary code, prompts, voices, assets.
- If the build fails, ask the user for the `e:` lines (GitHub Actions run > Summary > Annotations; the workflow now prints only errors there) and fix ONLY those errors before adding anything new.

## 3. Upload / build flow the user uses
Termux: `unzip -o ~/storage/downloads/MIRA.zip` in `~`, `cd ~/MIRA`, `git add -A`, `git commit`, `git push` (GitHub token as password). Then GitHub > Actions > "Android Debug APK" > Artifacts > MIRA-debug-apk. Full steps: `TERMUX_GIT_GUIDE.md`. Phone setup for control: install APK > App info > 3 dots > Allow restricted settings > Settings > Accessibility > MIRA > ON. Test list: `PHONE_TEST_CHECKLIST.md`.

## 4. What I (Claude) want you to do and know
1. First answer the user: "Which codebase do you want inspected: MIRA Android (this zip) or VenomAI PC?" Default to MIRA.
2. Do the read-only inspection you proposed, but on `MIRA.zip`. Run the unit tests if you have a JDK/Gradle (`./gradlew :app:testDebugUnitTest`); say clearly if you cannot.
3. Then ask the user for the result of the latest Actions run (green/red + `e:` lines). Do not guess errors.
4. Next work order after a green build: (a) phone test checklist results, (b) fix what fails, (c) Diagnostics screen, (d) audio focus, (e) persisted chat, (f) split `VoiceViewModel`, (g) enforce a code-level confirmation for send/pay/delete tool calls, (h) camera Scan.
5. Be honest with the user about limits: Maya's voice quality probably comes from Gemini native-audio + a good persona (emotion/proactive flags), not a hidden trick; MIRA cannot be "unlimited free" (Gemini free quota is limited and screen sharing uses it faster).
6. When you change anything, deliver a full new `MIRA.zip` plus update `HANDOVER.md`, `FEATURE_STATUS.md` and `MIRA_PLAN.md` inside it.
