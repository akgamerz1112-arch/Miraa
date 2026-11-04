# HANDOVER (keep updated)

## Where we are (v0.6.1, versionName 0.6.1-safe-setup)
First device report: v0.6 builds and installs; voice failed with close code 1007 (setup rejected). v0.6.1 adds a tiered fallback + shows the server reason. NEXT: ask the user for the new error text / which step worked.
Code exists for: Phase 1 voice, v0.2 (audio routing, volume boost, VAD presets, styles, user gender, key test, Hinglish errors),
v0.3 (mic boost + high start-of-speech sensitivity, natural persona with per-session vibe, Yaadein memory, typed Chat tab,
notification mute/stop buttons, bottom tabs). Details and honest status labels: FEATURE_STATUS.md. Roadmap: ZENRO_PLAN.md.

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
control/ScreenLogic.kt, control/ZenroAccessibilityService.kt, control/ScreenControl.kt (+ ScreenStreamer), res/xml/accessibility_service_config.xml,
res/values/strings.xml, manifest (service + queries), protocol/provider/persona/VM/service/UI changes, test/V04Test.kt.
Phone setup needed: install APK > App info > 3 dots > Allow restricted settings > Settings > Accessibility > ZENRO > ON.

## Files added/changed in v0.3
memory/MemoryLogic.kt (new), chat/TextChat.kt (new), voice/Persona.kt, voice/GeminiLiveProtocol.kt, voice/GeminiLiveProvider.kt,
voice/VoiceProvider.kt, audio/AudioLogic.kt, data/SecureStore.kt, service/VoiceForegroundService.kt (+ NotifActionReceiver, NotifActions),
VoiceViewModel.kt, ui/ZenroApp.kt, AndroidManifest.xml (receiver), app/build.gradle.kts (version), test/V03Test.kt (new).

## Next steps
1. User pushes this zip (v0.2 to v0.6 are all uncompiled), runs GitHub Actions, sends the last 40 lines of the log if red (expect small compile errors: first compile of v0.2 + v0.3).
2. Phone test checklist: (a) Bluetooth: sound comes from headphones and is loud enough; (b) whisper/soft speech gets heard (try mic slider 2x/3x); (c) say "meri behen ka naam Riya hai", then open Yaadein tab: is it saved?; (d) Chat tab: type, get a text reply; (e) notification: Mic band karo / Session band work; (f) does talk feel different each session?
3. If voice fails right after enabling memory (error 1007/1008), turn "Khud yaad rakho" off in Yaadein and report it.
4. After that: camera Scan, Diagnostics, audio focus, persisted chat history, memory summaries.

## Rules
No keys in repo, no fake features, mark IMPLEMENTED/TESTED/DEVICE VERIFIED honestly, phone-only (Termux + GitHub Actions). No explicit/18+ mode. Do not copy proprietary Maya code, prompts, voices or art; screenshots of Maya are only a UI/feature reference.
