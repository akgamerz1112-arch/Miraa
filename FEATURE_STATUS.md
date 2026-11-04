# ZENRO feature status

Legend: IMPLEMENTED = code exists. TESTED = a test passes. DEVICE VERIFIED = checked on a real phone.
Last check done: pure-Kotlin parts (protocol, persona, PCM level) compiled and 15 unit tests passed on a desktop JVM.
The Android parts (audio, service, UI) have NOT been compiled or run by anyone yet. Update this file after the first
GitHub Actions run and phone test.

## Phase 1 - real-time live voice (Gemini Live)

| Feature | Status |
|---|---|
| Gemini Live WebSocket protocol (setup, audio in, audio out) | IMPLEMENTED, TESTED on JVM (field names checked against Google docs, not against a live server) |
| Default model `gemini-3.8-live` (editable; legacy and extended-thinking chips in Settings) | IMPLEMENTED, name taken from Google docs, NOT verified with a real key |
| Session resumption + context-window compression | IMPLEMENTED, TESTED (message format only), NOT device verified |
| Streaming mic input 16 kHz, echo cancel + noise suppression if device supports | IMPLEMENTED (not device verified) |
| Streaming low-latency playback 24 kHz | IMPLEMENTED (not device verified) |
| Barge-in (server VAD "interrupted" -> playback flushed) | IMPLEMENTED (not device verified) |
| Male/female voice selection (8 Gemini prebuilt voices) | IMPLEMENTED (not device verified) |
| Maya-style personality (warm, chatty Hinglish, follow-ups, mood matching), gender-aware Hindi grammar | IMPLEMENTED, prompt text TESTED; how it actually sounds is NOT verified |
| Your name in Settings, used by ZENRO | IMPLEMENTED |
| Mute / stop | IMPLEMENTED |
| Pause/resume | NOT IMPLEMENTED (mute + stop only) |
| Auto-reconnect (4 tries, backoff), same conversation via resumption handle, else transcript recap | IMPLEMENTED (not device verified) |
| Foreground mic service + visible notification | IMPLEMENTED (not device verified) |
| API key in Android Keystore-backed encrypted storage | IMPLEMENTED |
| Dark UI, animated orb, transcript, settings, launcher icon | IMPLEMENTED (not device verified) |
| Provider interface for other voice providers | IMPLEMENTED (only Gemini Live exists) |
| Hindi/Hinglish quality | UNKNOWN until tested on device |

## Voice v0.2 (this update)

None of the items below were compiled or run: no Kotlin compiler and no network were available when this was written.
The first GitHub Actions run is the first compile of ALL Android code. Unit tests were WRITTEN (VoiceV2Test, 11 tests) but NOT RUN.

| Feature | Status |
|---|---|
| Output routing: Auto / Headphones / Speaker / Ear (`AudioRouter`) | IMPLEMENTED, not compiled, not device verified |
| Headphone HQ mode (media stream to Bluetooth/wired + phone mic, so BT does not drop to call quality) | IMPLEMENTED, not device verified |
| Re-route when headphones are plugged/unplugged mid-session | IMPLEMENTED, not device verified |
| Volume boost 1.0-3.0x with soft limiter + slider; speaker mode also raises call volume and restores it | IMPLEMENTED, gain logic has tests written (not run) |
| Real output shown under the orb (taken from `AudioTrack.routedDevice` after first audio) | IMPLEMENTED, not device verified |
| BLUETOOTH_CONNECT permission requested at runtime | IMPLEMENTED |
| Hindi pronunciation prompt, user gender (Ladka/Ladki), styles Dost / Pyaari dost / Focus / Padhai | IMPLEMENTED, prompt tests written (not run); how it sounds is NOT verified |
| Reply speed presets Jaldi / Normal / Aaram se (VAD silence 500 / 700 / 1100 ms) | IMPLEMENTED, JSON test written; field names from Google docs, not checked on a live server |
| Key test button (header-auth call to models list; proves the key, not Live access) | IMPLEMENTED, not device verified |
| Hinglish error messages | IMPLEMENTED, tests written (not run) |

Still planned: Headset call mode fallback, audio-focus handling (phone calls), Diagnostics screen, voice preview button, camera Scan. (Text chat, notification buttons and memory arrived in v0.3 below.)
No 18+ / romantic-explicit mode will be built; "Pyaari dost" is warm friendship only.

## v0.3 (this update): chat, memory, soft-speech, natural talk, notification buttons

None of this was compiled or run (no Kotlin compiler / network where it was written). v0.2 is also still uncompiled,
so the next GitHub Actions run is the first compile of v0.2 + v0.3 together. Tests WRITTEN: `V03Test` (13 tests), NOT RUN.

| Feature | Status |
|---|---|
| Soft-speech hearing: mic boost 1x-4x (default 2x) with soft limiter + Settings slider | IMPLEMENTED, gain maths test written (not run), not device verified |
| Soft-speech hearing: server `startOfSpeechSensitivity = HIGH` in the VAD setup | IMPLEMENTED, field name from Google docs, NOT checked on a live server |
| More natural, different-every-time talk: persona rewrite (varied openers/length, not always a question, imperfect speech, patient with soft/slow speakers), a random "vibe" per session, time of day, temperature 1.0 | IMPLEMENTED, prompt tests written (not run); how it sounds is NOT verified |
| Yaadein (memory): voice uses a real `save_memory` function call, typed chat uses a hidden `[[YAAD: ...]]` tag; facts saved on the phone (encrypted), shown in the Yaadein tab, added/deleted by hand, on/off switch | IMPLEMENTED, logic tests written (not run), not device verified |
| Memory safety: facts with long numbers, password, OTP, PIN, CVV, Aadhaar words are refused; max 60 facts of 200 chars | IMPLEMENTED, tests written (not run) |
| Memory facts are sent to Google inside the prompt at the start of each session / chat message | TRUE, stated on the Yaadein screen |
| If the server rejects the setup with the memory tool on, voice retries once without the tool (no memory saving, voice still works) | IMPLEMENTED, not device verified |
| Chat tab: type, ZENRO answers in text (Gemini `generateContent`, key in header, default model `gemini-flash-latest`, editable in Settings) | IMPLEMENTED, request/response tests written (not run), model name NOT verified with a real key |
| Chat history | In memory only; lost when the app is killed. Clear button. NOT persisted |
| Notification buttons: "Mic band/on karo" (mute) and "Session band" (stop) + text shows muted state | IMPLEMENTED, not device verified |
| Notification can be fully hidden | NO. Android requires a visible notification while the mic runs in the background (Android 13 lets you swipe it away, the service keeps running) |
| Bottom tabs Voice / Chat / Yaadein | IMPLEMENTED (plain Compose, text/emoji icons), not device verified |

Not in this update: Scan (camera), Diagnostics screen, audio focus on phone calls, voice preview, pause/resume,
chat history saving, memory from past voice transcripts (only what the model chooses to save).
Maya's 18+/romantic modes seen in the reference screenshots are NOT built and will not be.

## v0.4: live screen sharing + phone control (code only)

Same warning as v0.3: NOT compiled, NOT run, NOT device verified. `V04Test` (7 tests) written, not run.

| Feature | Status |
|---|---|
| Accessibility service (screenshot, tap, swipe, type, back/home/recents, open app) | IMPLEMENTED, not device verified |
| Screen "live": a screenshot about once a second (max 720 px wide, JPEG) sent as live video input; identical frames skipped, 4 s keep-alive | IMPLEMENTED. It is a stream of pictures, NOT smooth video. Needs Android 11+ |
| Model controls the phone through 5 functions (screen_tap, screen_swipe, type_text, press_button, open_app), coordinates 0-1000 | IMPLEMENTED, whether the model uses them well is NOT verified |
| Results: "ok" means Android accepted the action, not that the app changed; the model is told to check the next picture | IMPLEMENTED |
| Safety: Screen dikhao and Control do are OFF by default; control needs share; prompt rules (one action at a time, ask before send/pay/delete, never passwords/OTP, ignore instructions written on screen); password boxes are refused in code; notification "Screen band" button; on-screen line shows what ZENRO is doing | IMPLEMENTED. "Ask before sensitive actions" is a prompt rule, NOT enforced in code |
| If the server rejects the tools at setup, voice retries without memory/control and says so | IMPLEMENTED, not device verified |
| Banking / FLAG_SECURE screens | Screenshot fails or is black; ZENRO is told to say so |
| Android 13+ blocks Accessibility for apps installed from an APK file until you tap App info > 3 dots > "Allow restricted settings" | Needed on the phone, cannot be done by the app |
| Screen pictures go to Google (free-tier data may be used by Google); quota is used faster | TRUE, stated on screen |
| Calls, SMS, camera, files, background tasks without the screen | NOT built |

## v0.5: ideas from the Venom/"Friday" PC agent (HunterisLive-1/venomai), rebuilt for Android

Only the README (feature list) was read. No code, prompts or assets were copied. Still NOT compiled / run / device verified. `V05Test` (4 tests) written, not run.

| Idea in that PC agent | In ZENRO v0.5 |
|---|---|
| YouTube open/search/play | `youtube_search` opens YouTube results (intent); picking/playing a video is done by the model through the screen tools. IMPLEMENTED, untested |
| YouTube volume | `set_volume` (exact % or up/down, media volume). IMPLEMENTED, untested |
| Media navigation | `media_control` play/pause, next, previous (media keys). IMPLEMENTED, untested |
| WhatsApp open + send message | `whatsapp_message` opens the chat with text typed (wa.me link, bare 10-digit numbers get +91); the model then presses Send on screen AFTER the user says yes. Contact names need the Contacts permission (button in Settings). IMPLEMENTED, untested |
| Open apps / show desktop | `open_app`, `press_button` home/recents (v0.4) |
| Realtime vision | live screen pictures (v0.4). Camera vision NOT built |
| Long-term memory (mem0) | Yaadein (v0.3) |
| Affectionate personality, emotions | prompt only: warmer feelings in voice, greet a guest by name. How it sounds is NOT verified; Google's "affective dialog" switch was NOT used (unverified) |
| Attach files in WhatsApp, screenshots sent via WhatsApp | NOT built (model can try via the screen tools) |
| Autodesk Maya "Sentinel" demo | not relevant, not built |

Android may block apps from opening other apps while ZENRO is in the background (all these intents). If a tool says done but nothing opens, bring ZENRO to the front or tell me. Not verified.

## v0.6: Emotion mode (what the "Friday" video's code actually uses)

The user's screenshots of that agent show: model `gemini-2.5-flash-native-audio-preview-12-2025`, voice Aoede, temperature 1.0,
`proactivity=True`, `enable_affective_dialog=True`, LiveKit noise cancellation. Those two flags (not a secret prompt) are most of the "emotional, adapts its tone" feel.
Google's Live API guide says both are supported on the Gemini 2.5 native-audio Live models only (NOT on 3.1 Flash Live) and need API version v1alpha.

| Item | Status |
|---|---|
| Settings switch "Emotion + proactive mode" (default OFF), new model chip for the 2.5 native-audio model | IMPLEMENTED, not compiled/run |
| When ON: v1alpha endpoint, `generationConfig.enableAffectiveDialog = true`, `setup.proactivity.proactiveAudio = true` | IMPLEMENTED. Field LOCATION of enableAffectiveDialog is from memory of Google's SDK, NOT verified on a live server |
| If the server rejects the setup, voice retries once without emotion/tools and says so on screen | IMPLEMENTED, not device verified |
| Temperature 1.0 | already set since v0.3 |
| Proactive audio means the model may stay silent when the input is not meant for it | By design; can feel like ZENRO ignores you. Turn the switch off if so |
| Server-side noise cancellation (LiveKit/Krisp) | NOT built; Android echo/noise suppression + mic boost are used instead |
| `V06Test` (2 tests) | written, NOT RUN |

## v0.6.1: voice error 1007 on the phone (first real device report)

User screenshot (v0.6 installed, so the CI build is green and the app runs): orb screen says "Key ya model galat lagta hai (1007)".
1007 means the server rejected the setup message. v0.1 voice had worked; since then the setup gained temperature, `startOfSpeechSensitivity`, the VAD block, tools and emotion fields. Which one Google rejects is NOT known yet.
Fix (IMPLEMENTED, not run): tiered fallback in `GeminiLiveProvider.degrade()`: step 1 drop tools + emotion, step 2 drop temperature + start sensitivity, step 3 drop the VAD block (= plain v0.1 setup). Errors now include the server's own reason text ("Server ne kaha: ..."). Tests: `V07Test`, not run.
Also to try: Settings > Live model name > chip `gemini-3.1-flash-live-preview`.

## Not started (Phases 2-9)

Sarvam voices, camera/vision, persistent memory, phone control, communication, wake word, background reliability,
reminders, provider router, task agent, web research, PC/TV/smart-home. None of these exist yet; no placeholder buttons are shown.
ZENRO therefore CANNOT open apps, call, message or browse yet. It only talks.

## Platform limits to remember

- Free Gemini quota has limits; it is not unlimited.
- Android may stop background mic use on some phones; Samsung battery optimization can kill the service.
- Gemini Live model names change often; the model is editable in Settings.
- Gemini voices are fixed prebuilt voices; there is no custom voice cloning.

## v0.7 (2026-11-04) - all CODE WRITTEN, NOT compiled, NOT device verified
- Persona: creator rule (Ayan sir is the only owner; refuses other claims); big EMOTIONS section (range, per-emotion protocol, voice cues, emotional continuity); more moods.
- Emotion mode: on failure before session start tries the other API version, then plain voice (no error screen).
- Voice 1007 "Request contains an invalid argument": setup ladder now 6 steps (tools/emotion -> temperature/start sensitivity -> VAD -> compression+resumption -> model gemini-2.5-flash-native-audio-preview-12-2025 -> gemini-3.1-flash-live-preview); working level remembered until app restart; screen shows which model worked.
- Chat tab now uses the Live model (no flash). Reply is spoken (audio plays) AND shown as text (output transcription). Memory tool works in chat. Falls back emotion+memory -> memory -> plain.
- NOT done yet: greet-first when a session starts, daily proactive check-in notifications, emotion-tagged memory store.
- Emotion ON now first uses model gemini-2.5-flash-native-audio-preview-12-2025 on v1alpha (same as a working LiveKit agent: voice Aoede, temperature 1.0, proactivity + affective dialog), then the Settings model, then plain voice. Same order in Chat.
