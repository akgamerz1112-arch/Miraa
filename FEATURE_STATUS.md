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

## Not started (Phases 2-9)

Sarvam voices, camera/vision, persistent memory, phone control, communication, wake word, background reliability,
reminders, provider router, task agent, web research, PC/TV/smart-home. None of these exist yet; no placeholder buttons are shown.
ZENRO therefore CANNOT open apps, call, message or browse yet. It only talks.

## Platform limits to remember

- Free Gemini quota has limits; it is not unlimited.
- Android may stop background mic use on some phones; Samsung battery optimization can kill the service.
- Gemini Live model names change often; the model is editable in Settings.
- Gemini voices are fixed prebuilt voices; there is no custom voice cloning.
