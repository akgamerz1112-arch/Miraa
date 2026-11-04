# MIRA — VOICE-FIRST MASTER PLAN (v0.2 "one big update")

Priority set by the user after the first real install: **VOICE quality first**. Features come after the voice feels right.
All changes in a phase are delivered together in ONE zip (all files updated at once), then built by GitHub Actions.

## 0. What the user found on a real phone (user-reported, Galaxy S20 FE, Android 13)

| # | Finding | Status |
|---|---|---|
| 1 | APK built and installed from GitHub Actions | USER-REPORTED OK (so the Android code compiled) |
| 2 | Voice volume is very low | OPEN |
| 3 | Bluetooth headphones connected, but sound still comes from the phone speaker | OPEN |
| 4 | Too few features | KNOWN (Phase 1 only), see section 5 |
| 5 | Wants focus on voice | PLAN BELOW |

Not yet reported (ask the user to test and report): Hindi/Hinglish quality, echo/self-interruption on speaker, barge-in, reconnect, battery.

## 1. Root causes found in the code (read from source, not yet fixed)

`GeminiLiveProvider.routeAudio()` sets `AudioManager.MODE_IN_COMMUNICATION` and `isSpeakerphoneOn = true`.
`PcmPlayer` uses `USAGE_VOICE_COMMUNICATION`.

- **Speaker instead of headphones:** `isSpeakerphoneOn = true` forces the loudspeaker even when Bluetooth is connected. No Bluetooth routing exists at all.
- **Low volume:** with voice-communication usage the sound plays on the *voice-call volume stream*, which is usually set low and is not what the volume buttons change when the user is not in a call. Output samples are also played at 1.0x gain.
- **Android 12+ (API 31+):** reading Bluetooth devices needs the runtime permission `BLUETOOTH_CONNECT`; the manifest and UI do not request it yet.

## 2. PHASE V1 — Audio routing + volume (do first, highest value)

### 2.1 New class `audio/AudioRouter.kt`
Detect outputs with `AudioManager.getDevices(GET_DEVICES_OUTPUTS)`; types: `TYPE_BLUETOOTH_A2DP`, `TYPE_BLE_HEADSET`, `TYPE_BLUETOOTH_SCO`, `TYPE_WIRED_HEADSET`, `TYPE_WIRED_HEADPHONES`, `TYPE_USB_HEADSET`, `TYPE_BUILTIN_SPEAKER`, `TYPE_BUILTIN_EARPIECE`. Listen for changes with `AudioDeviceCallback` (headphones plugged/unplugged mid-session: re-route without dropping the session).

Route modes (Settings > "Awaaz kahan se aaye"):
1. **Auto (default):** headphones present -> Headphone HQ mode; else Speaker mode.
2. **Headphone HQ mode (recommended for Bluetooth):** `MODE_NORMAL`, output `USAGE_MEDIA` to the A2DP/wired device (stereo-quality, uses the normal MEDIA volume the volume buttons control), microphone = the phone's **built-in mic** (`AudioRecord.setPreferredDevice(TYPE_BUILTIN_MIC)`, source `VOICE_RECOGNITION` or `MIC`) so Bluetooth does NOT switch to the low-quality call profile. No echo problem because headphones isolate the sound.
3. **Headset call mode (fallback):** `MODE_IN_COMMUNICATION`; on API 31+ `setCommunicationDevice(<BT/BLE/wired device from availableCommunicationDevices>)`, on API 26-30 `startBluetoothSco()` + `isBluetoothScoOn`. Mic comes from the headset (narrowband, call quality: worse for speech recognition and the voice sounds thin). Use only if mode 2 fails on a headset.
4. **Speaker mode:** loudspeaker. Two sub-options to A/B test on device: (a) `MODE_IN_COMMUNICATION` + `USAGE_VOICE_COMMUNICATION` + hardware AEC (less echo, quieter) or (b) `USAGE_MEDIA` + loud (louder, may echo and self-interrupt; add mic-gate threshold). Default to (a) with extra gain; expose "Loud speaker" switch for (b).
5. **Phone ear mode:** earpiece (private call style).

Always call `clearCommunicationDevice()` / restore mode / stop SCO on stop. Request and handle `AudioFocus` (`AUDIOFOCUS_GAIN`); on loss (e.g. phone call) pause the session politely; resume or stop cleanly.

### 2.2 Volume
- Software gain in `PcmPlayer.enqueue`: multiply PCM16 samples by a user gain 1.0x-3.0x (default 1.6x) with a soft limiter (clamp to +-32767, no wrap-around) to avoid distortion. Unit test: gain, clipping, silence.
- In-app volume slider (the gain) and show current route.
- In speaker/call modes, raise `STREAM_VOICE_CALL` volume toward max on start and restore the previous value on stop (tell the user in Settings text; this changes a system volume while the session is active).
- Headphone HQ mode uses `STREAM_MUSIC`; hint the user to use the phone volume buttons.

### 2.3 Permissions
Manifest: `BLUETOOTH_CONNECT` (API 31+; `maxSdkVersion` handling for old `BLUETOOTH` not needed with minSdk 26 only if we guard calls). Request at runtime together with RECORD_AUDIO and POST_NOTIFICATIONS. If denied: still work through the speaker/wired path and say so in plain Hinglish.

### 2.4 UI
Status line under the orb: "Output: Bluetooth headphones (HQ)" / "Phone speaker". Route chips: Auto / Headphones / Speaker / Ear. Volume slider. Never claim a route is active unless `AudioManager` confirms the device (verify after set; show the real active device).

### 2.5 Tests
Unit tests (pure Kotlin): gain/limiter, route selection logic (a pure function `chooseRoute(devices, preference)` that takes a list of device types). Android calls stay thin. Device test checklist in section 7.

## 3. PHASE V2 — Voice naturalness and conversation feel

1. **Hindi pronunciation (important):** Roman-script Hindi can be read with an English accent. Add to the persona: speak Hindi words with natural Indian Hindi pronunciation and Indian-English accent for English words; do not spell out or read symbols. Test with a fixed phrase list.
2. **VAD tuning:** add `realtimeInputConfig.automaticActivityDetection` with sensible `silenceDurationMs` (Google recommends 500-800 ms; 100-200 ms splits sentences and hurts quality), `prefixPaddingMs`, and start/end-of-speech sensitivity. Expose a Settings choice: "Jaldi jawab" (shorter silence) / "Normal" / "Aaram se" (longer silence so slow speakers are not cut off). Unit-test the JSON; device-test the feel.
3. **Barge-in quality:** after `interrupted`, flush playback instantly (already done); also drop any audio chunks still arriving for the cancelled turn; measure latency from user speech to silence on device.
4. **Speed/emotion:** Gemini has no speed knob; steer by prompt ("slightly slower, warm, expressive"). Research `enableAffectiveDialog` / `proactivity` for the current model (docs say they need `v1beta`, and are not supported on the legacy 3.1 model; their exact placement for `gemini-3.8-live` is unconfirmed). Add as an **Experimental toggle** with automatic fallback if the server rejects it (close code 1007) — never crash.
5. **Voice choice:** show all 8 current voices with male/female tags and a "Sunao" (preview) button that speaks one fixed line using the real session (no fake previews). Check the official voice list for newer voices before adding.
6. **Style presets (honest, no 18+):** "Dost" (friendly), "Pyaari dost" (warm, playful, caring), "Focus" (short and direct), "Padhai" (study helper). Presets only change the system prompt. No sexual content mode will be built.
7. **User profile:** user gender (Ladka/Ladki) so Hindi grammar toward the user is right, plus name; both in Persona; unit tests.
8. **Compare with Maya/Myra by listening:** user plays 20 fixed prompts (section 7) on both apps and reports differences in his own words; the persona is tuned from that. No copying of any proprietary prompt, code, voice or art.

## 4. PHASE V3 — Reliability + diagnostics (so device bugs can be fixed fast)

1. **Key test button** (a tiny real Live handshake or a cheap authenticated call; clear Hinglish result: key ok / key wrong / quota over / no internet).
2. **Diagnostics screen:** active route and device name, sample rates, session state, reconnect count, last error code, resumption handle present yes/no, last round-trip latency estimate. "Copy report" button; the report must never include the API key.
3. Hinglish error messages for each failure (1007/1008 bad key or model, quota 429, no internet, mic busy, Bluetooth permission denied).
4. Notification with a Stop action; optional keep-screen-on; handle phone calls (audio focus loss) and headset unplug.
5. Keep reconnect + resumption; add network-change listener to reconnect faster; cap total retries.
6. Text chat tab (type + voice in the same conversation; uses `realtimeInput.text`). Chat history stays on-device only after the Memory phase adds consent.
7. Battery guidance screen (Samsung: Unrestricted battery for MIRA).

## 5. Later feature phases (after voice feels good; each in its own single update)

| Phase | What | Notes |
|---|---|---|
| 2 | Sarvam streaming TTS (Hindi male/female) via the existing `VoiceProvider` interface | Read Sarvam docs/terms/pricing first; free tier is not unlimited; needs a separate STT+LLM path so label it honestly as "not Gemini Live" |
| 3 | Camera "Scan": live camera frames to Gemini (`realtimeInput.video`), read text/objects | Explicit consent each session, visible indicator, no silent upload |
| 4 | Memory ("Yaadein") | Room DB, user can view/edit/delete, on/off switch, export; semantic retrieval later |
| 5 | Phone control + communication | Accessibility Service, contacts/calls/SMS with permissions, confirm before sending, verify results |
| 6 | Wake word "MIRA", background service, reminders | Android/Samsung restrictions apply; never promise always-on |
| 7 | Task agent + web research + provider router (Groq/OpenRouter/etc.) | Free-first, paid opt-in, no auto paid fallback |
| 8 | PC / TV / smart-home integrations | Only when truly implemented and tested |
| 9 | Security + performance + full device test | |

## 6. Rules for every update (unchanged)

No API keys or tokens in code or chat. No fake buttons. Mark IMPLEMENTED / TESTED / DEVICE VERIFIED honestly; "TESTED" only for tests that really ran. Update `FEATURE_STATUS.md`, `HANDOVER.md` and the kit zip at the end of every update. Deliver ONE zip with all files updated together. Keep the signing key stable (optional secrets) so updates install over the old app. User is phone-only (Termux + GitHub Actions); give copy-paste Termux steps, one command per block, with real values not placeholders, and always `cd ~/MIRA` first.

## 7. Device test checklist for the user (after each update)

1. Bluetooth headphones on: tap the orb; does sound come from the headphones? Is it loud enough at 60% media volume?
2. Unplug/disconnect headphones mid-conversation; does it move to the speaker without crashing?
3. Speaker only: is the volume enough? Does MIRA hear herself and interrupt herself?
4. Interrupt her mid-sentence; how quickly does she stop?
5. Say 20 fixed lines (greeting, bad day, joke, tongue-twister in Hindi, English phrase, name, number, long story request, "tum kya kar sakti ho", "kal ki baat yaad hai?"); note pronunciation and tone vs Maya.
6. Turn mobile data off for 10 seconds and on again; does she reconnect and continue the same conversation?
7. Receive a phone call during a session; behaviour after the call?
8. Leave the app for 5 minutes; does the session survive?
Report results as short notes; screenshots of the Diagnostics screen help.

## 8. Definition of done for the "Voice v0.2" update

- Bluetooth headphones play the voice at a comfortable level with the media volume buttons; phone speaker still works.
- Volume slider and real active-route display.
- Hindi pronunciation prompt + VAD presets + user gender in Persona.
- Key test + diagnostics screen + Hinglish errors.
- Unit tests pass in CI; user device checklist done and reported.


---

## UPDATE after v0.3 (what changed in the plan)

| Item | Status now |
|---|---|
| V1 audio routing + volume | CODE WRITTEN in v0.2, not compiled, not device verified |
| V2 naturalness (VAD presets, styles, user gender, Hindi pronunciation) | CODE WRITTEN in v0.2; v0.3 adds a varied-talk persona + per-session vibe + temperature 1.0 |
| Soft speech | v0.3: mic boost slider + server start sensitivity HIGH (device test needed) |
| V3 text chat tab | v0.3: DONE as code (typed chat, text replies) |
| V3 notification Stop action | v0.3: DONE as code (mute + stop buttons) |
| Memory | v0.3: first version (facts saved by the model + manual add/delete). Not yet: summaries of old chats, per-topic memory, export |
| V3 Diagnostics screen, audio focus, battery guidance | NOT DONE |
| Camera Scan, phone control, etc. (Phases 3-9) | NOT STARTED |

Reference app: the user's screenshots show the commercial "Maya" app (Home / Scan / Voice / Yaadein / Chat tabs, Friendly/Romantic/Intense modes).
MIRA copies only the general idea of the tab layout. The Romantic/Intense (18+) modes are out of scope by project rule.

Next update (v0.4) proposal, voice quality first: Diagnostics screen, audio focus (phone call), voice preview "Sunao", persisted chat history,
then Scan (camera). Order can change after the phone test of v0.3.


## UPDATE after v0.4
Screen live + phone control: CODE WRITTEN in v0.4 (accessibility service + screenshot stream + 5 control functions), not compiled, not device verified.
Still not built: calls/SMS, camera Scan, Diagnostics, audio focus, persisted chat. If v0.2-v0.4 fail to compile together, fix compile errors first, then test in this order: voice, chat/memory, screen share, control.


## UPDATE after v0.5
Venom/"Friday" PC-agent feature ideas rebuilt for Android (YouTube search, volume, media keys, WhatsApp chat prefill): CODE WRITTEN, untested.
Not built: camera Scan (live camera vision), WhatsApp attachments/screenshot sharing, Diagnostics, audio focus, persisted chat, calls/SMS, wake word.


## UPDATE after v0.6
Emotion mode (affective dialog + proactive audio, 2.5 native-audio model, v1alpha) CODE WRITTEN, unverified. This is the main thing the "Friday" video agent relies on for its emotional feel.
