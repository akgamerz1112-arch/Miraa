# HANDOVER (keep updated)

## SESSION 3 NOTES (2026-11-04, Android + LiveKit client)
- Done (code only): LiveKit client, config/token API, Settings (URL/device id/key), connection state UI, 9 RPC handlers, "Control do" switch, Gemini code removed (owner said YES), version 0.9.0-livekit / code 8. Details + labels: `FEATURE_STATUS.md`. Next steps: `HANDOVER_SESSION4.md`. Phone test list: `PHONE_TEST_CHECKLIST.md`.
- NOT compiled: no Android SDK / blocked Maven. Only the pure logic was compiled and run (`S3Test`, 12 PASS, with a gson stand-in for org.json). First GitHub Actions run decides; send the `e:` lines if red.
- LiveKit facts checked in the CURRENT docs: artifact `io.livekit:livekit-android`; API reference shows version 2.30.0; `LiveKit.create(context)`, `room.connect(url, token)`, `localParticipant.setMicrophoneEnabled(true)`, `room.registerRpcMethod(method) { data -> ... returns String }` (docs show it on `Room`), `Room.State` = CONNECTING/CONNECTED/DISCONNECTED/RECONNECTING, `RoomEvent.ParticipantConnected/ParticipantDisconnected/Disconnected/FailedToConnect/Reconnecting/Reconnected`, `Participant.isAgent`. Docs also say JitPack should be a repository (added, limited to `com.github.*`). NOT checked (written from SDK knowledge): `room.events.collect`, `room.remoteParticipants`, `room.release()`, `RpcInvocationData.payload` type, exact `RpcHandler` signature. If a compile error mentions these, fix there first.
- Likely build risks: (1) Kotlin 1.9.24 / Compose compiler 1.5.14 may be too old to read LiveKit 2.30 metadata ("binary version of its metadata is 2.x") -> upgrade Kotlin (2.0.x + compose compiler plugin) or pin an older livekit version; (2) LiveKit's AndroidX deps may need compileSdk 35 / newer AGP; (3) JitPack group filter may be wrong. Do not guess: read the CI error.
- API contract detail: `app_version` sent is the integer versionCode (server compares ints with `min_app_version`). Device id is lower-cased and must match `^[a-z0-9][a-z0-9-]{1,31}$`.
- HTTPS: the API must be https (Android blocks http). No cleartext exception was added. Options for the owner: Cloudflare Tunnel / ngrok to the PC's port 8080, or a host with a certificate.
- Rotate Google, LiveKit, Mem0 keys (exposed once in a chat zip). Licence of `HunterisLive-1` still unconfirmed (owner must check).


## SESSION 2 NOTES (2026-11-04, backend session; Android code NOT touched)
- Build truth: no Kotlin/Android SDK/network in the Session 2 environment, so NOTHING was compiled here. Package/folder names were checked (all `package` lines match folders). Evidence from older notes (`HANDOVER_FOR_CHATGPT.md`): CI runs #2-#8 green (v0.2 era); run #9 (v0.6) was RED with one error in `control/PhoneActions.kt` (`Phone` companion), fixed in the code but the fixed build's result was never reported. v0.7/v0.8 code and tests exist but were never compiled either. `versionName` is still `0.6.1-safe-setup`. First real answer = next GitHub Actions run (send the `e:` lines if red).
- Gemini Live close code 1007: STILL OPEN, never reproduced or fixed here. It goes away when the Gemini WebSocket code is removed in Session 3. New fact: current LiveKit docs list `gemini-3.8-live` as an existing model, so the default model name is probably real; the 1007 may come from the setup fields (proactivity / affective dialog are NOT supported on Gemini 3.x per the docs).
- Backend is ready: see `venomai-main/HANDOVER.md` for the RPC contract (`mira.<action>`, phone identity prefix `dev-`) and the Session 3 scope.

## RENAME DONE (ZENRO -> MIRA), NOT COMPILED
- Package `com.zenro.app` -> `com.mira.app` (folders, namespace, applicationId, manifest, imports, tests). Classes: `ZenroApp` -> `MiraApp`, `ZenroAccessibilityService` -> `MiraAccessibilityService`, theme `Theme.Mira`, app label MIRA, `ZENRO_PLAN.md` -> `MIRA_PLAN.md`, APK artifact name `MIRA-debug-apk`.
- Keystore env/secret names `ZENRO_KEYSTORE_*` -> `MIRA_KEYSTORE_*` (GitHub repo secrets must be created again under the new names if a fixed signing key is used).
- New applicationId = Android treats it as a NEW app: uninstall old ZENRO, re-enable Accessibility (App info > 3 dots > Allow restricted settings > Settings > Accessibility > MIRA).
- Identity in `voice/Persona.kt`: creator Ayaan Sir, line "Hamare Ayaan Sir ne hi banaya hai mujhe", user = "Boss", greeting "Assalamu Alaikum Boss!", reply "Wa Alaikum Assalam Boss!", never pretends to be human. Tests: `CreatorTest`, `V08Test` updated.
- Only text/file renames were done with scripts. Nothing was compiled and no unit test was run (no Kotlin compiler in that environment). First GitHub Actions run decides. The old docs below still describe history; the word ZENRO inside them was replaced by MIRA.
- Gemini Live voice code is still in place. It is to be replaced by a LiveKit client in a later session (ask the owner before deleting).


## Where we are (v0.6.1, versionName 0.6.1-safe-setup)
First device report: v0.6 builds and installs; voice failed with close code 1007 (setup rejected). v0.6.1 adds a tiered fallback + shows the server reason. NEXT: ask the user for the new error text / which step worked.
Code exists for: Phase 1 voice, v0.2 (audio routing, volume boost, VAD presets, styles, user gender, key test, Hinglish errors),
v0.3 (mic boost + high start-of-speech sensitivity, natural persona with per-session vibe, Yaadein memory, typed Chat tab,
notification mute/stop buttons, bottom tabs). Details and honest status labels: FEATURE_STATUS.md. Roadmap: MIRA_PLAN.md.

## Verification (honest)
- User reported the v0.1 APK built on GitHub Actions and installed. They also found low volume and Bluetooth sound from the speaker, which v0.2 tries to fix.
- v0.2 and v0.3 Android code has NEVER been compiled, and v0.2/v0.3 unit tests (VoiceV2Test, V03Test) have NEVER been run. No Kotlin compiler was available when they were written.
- Nothing is DEVICE VERIFIED. Gemini model names and the new protocol fields (startOfSpeechSensitivity, tools/toolCall, generateContent model) come from Google docs / memory, not from a live key.

## v0.6 added
Emotion mode: NATIVE_AUDIO_MODEL chip, v1alpha endpoint, enableAffectiveDialog + proactiveAudio, fallback in GeminiLiveProvider.retryWithoutTools, test/V06Test.kt. Test it with the 2.5 native-audio model selected; if voice fails right after enabling, turn it off and send the error.

## v0.5 added
control/PhoneActions.kt (YouTube search, volume, media keys, WhatsApp chat), LinkMath in control/ScreenLogic.kt, 4 new tool declarations,
READ_CONTACTS permission + Settings button, persona lines (emotion, guest greeting), test/V05Test.kt.

## v0.4 (screen live + control) added
control/ScreenLogic.kt, control/MiraAccessibilityService.kt, control/ScreenControl.kt (+ ScreenStreamer), res/xml/accessibility_service_config.xml,
res/values/strings.xml, manifest (service + queries), protocol/provider/persona/VM/service/UI changes, test/V04Test.kt.
Phone setup needed: install APK > App info > 3 dots > Allow restricted settings > Settings > Accessibility > MIRA > ON.

## Files added/changed in v0.3
memory/MemoryLogic.kt (new), chat/TextChat.kt (new), voice/Persona.kt, voice/GeminiLiveProtocol.kt, voice/GeminiLiveProvider.kt,
voice/VoiceProvider.kt, audio/AudioLogic.kt, data/SecureStore.kt, service/VoiceForegroundService.kt (+ NotifActionReceiver, NotifActions),
VoiceViewModel.kt, ui/MiraApp.kt, AndroidManifest.xml (receiver), app/build.gradle.kts (version), test/V03Test.kt (new).

## Next steps
1. User pushes this zip (v0.2 to v0.6 are all uncompiled), runs GitHub Actions, sends the last 40 lines of the log if red (expect small compile errors: first compile of v0.2 + v0.3).
2. Phone test checklist: (a) Bluetooth: sound comes from headphones and is loud enough; (b) whisper/soft speech gets heard (try mic slider 2x/3x); (c) say "meri behen ka naam Riya hai", then open Yaadein tab: is it saved?; (d) Chat tab: type, get a text reply; (e) notification: Mic band karo / Session band work; (f) does talk feel different each session?
3. If voice fails right after enabling memory (error 1007/1008), turn "Khud yaad rakho" off in Yaadein and report it.
4. After that: camera Scan, Diagnostics, audio focus, persisted chat history, memory summaries.

## Rules
No keys in repo, no fake features, mark IMPLEMENTED/TESTED/DEVICE VERIFIED honestly, phone-only (Termux + GitHub Actions). No explicit/18+ mode. Do not copy proprietary Maya code, prompts, voices or art; screenshots of Maya are only a UI/feature reference.
