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

Not in this update (planned): Headset call mode fallback, audio-focus handling (phone calls), Diagnostics screen,
text chat tab, notification Stop action, voice preview button, camera Scan, memory.
No 18+ / romantic-explicit mode will be built; "Pyaari dost" is warm friendship only.

## Not started (Phases 2-9)

Sarvam voices, camera/vision, persistent memory, phone control, communication, wake word, background reliability,
reminders, provider router, task agent, web research, PC/TV/smart-home. None of these exist yet; no placeholder buttons are shown.
ZENRO therefore CANNOT open apps, call, message or browse yet. It only talks.

## Platform limits to remember

- Free Gemini quota has limits; it is not unlimited.
- Android may stop background mic use on some phones; Samsung battery optimization can kill the service.
- Gemini Live model names change often; the model is editable in Settings.
- Gemini voices are fixed prebuilt voices; there is no custom voice cloning.
