# v0.8 PLAN (2026-11-04): OWNER VOICE + STRONG ROADMAP + HANDOVER PROTOCOL

Status of this section: PLAN ONLY. No code for owner voice exists yet. Nothing below is IMPLEMENTED, TESTED or DEVICE VERIFIED.
User request (2026-11-04, Hinglish): keep building ZENRO (he calls it the best app); the one big thing missing is that ZENRO must recognise HIS voice (owner voice); add strong features in future; keep a handover so any session can continue; make a strong plan.

## 9. Owner Voice ("Maalik ki awaaz")

### 9.1 What it is, and honest limits
- Goal: ZENRO acts on sensitive commands only when the speaker's voice matches the enrolled owner (Ayan sir). TV, friends, or a stranger nearby must not be able to steer the phone.
- Gemini Live CANNOT verify who is speaking. The persona rule "owner is Ayan sir" is only text; it proves nothing. Identity must be decided IN CODE, on the phone, by a speaker-embedding model. The model (Gemini) is never asked "is this the owner?" and never decides it.
- Voice match is a probability, not a lock. Honest limits to show in Settings text and keep in this doc:
  1. Short speech (< ~1.5 s), whisper, a cold, noise, Bluetooth mic vs phone mic all change the score. Expect some false rejects of the real owner.
  2. A recording of the owner's voice, or an AI voice clone, can pass. There is no anti-spoof in this plan. So voice match is NEVER the only protection for money, delete, send, or install actions: those keep a spoken confirmation and, in a later step, Android BiometricPrompt.
  3. Typed Chat is treated as owner input (phone unlock is the guard there).
- Reference idea (concept only, no code/prompts copied): the Maya v4 catalog doc has a "Boss voice lock / Guest mode": guests may chat but tools are blocked, with an optional allowlist. ZENRO re-designs that idea for Android.

### 9.2 Modes (Settings > "Awaaz suraksha")
| Mode | Behaviour |
|---|---|
| OFF | Today's behaviour. Default until the owner enrols. |
| GUARD (recommended) | Everyone can talk to ZENRO. Sensitive tools (screen control, WhatsApp, memory saving, future calls/SMS) run only if the latest owner match is fresh. A guest gets a polite Hinglish refusal ("Ye kaam sirf Ayan sir ki awaaz par hota hai"). |
| STRICT | Audio that does not match is not sent to Gemini at all (also saves quota, blocks TV). Uses a pre-roll buffer and an "owner window" (stays open for N seconds after a match) so it does not feel deaf. Needs the best tuning; built last. |
Sensitivity: Easy / Normal / Strict (moves the threshold). Allowlist for guests (e.g. time, weather, chat) configurable later.

### 9.3 Architecture (new package `com.zenro.app.owner`)
Pipeline: `MicStreamer.onChunk` -> `OwnerGate` -> provider `sendAudio`. In GUARD mode the gate only observes (audio always flows). In STRICT mode the gate also decides what flows.

| File | Role | Testable on JVM? |
|---|---|---|
| `owner/OwnerVoiceLogic.kt` | Pure Kotlin: L2-normalise, cosine similarity, enrolment aggregate (mean + keep individual vectors), threshold calibration from enrolment scores, score smoothing over last 2-3 segments, gate state machine (CLOSED / OWNER_WINDOW), pre-roll ring buffer, `OwnerDecision(isOwner, score, fresh)`, tool-gate decision table | YES (unit tests) |
| `owner/SpeechSegmenter.kt` | Cuts speech segments from the 16 kHz stream using the existing `Pcm.level`; min speech length before scoring; max segment length | YES |
| `owner/SpeakerEmbedder.kt` | Interface `embed(pcm16: ShortArray): FloatArray?` | interface only |
| `owner/SherpaSpeakerEmbedder.kt` | Android implementation on top of sherpa-onnx speaker embedding (runs on a background thread, only on speech segments) | NO (device) |
| `owner/OwnerStore.kt` | Embeddings stored through `SecureStore` (encrypted). NO raw audio is ever saved. "Voiceprint delete" button. | partly |
| `ui` additions | Enrolment screen, live score meter, mode + sensitivity, delete voiceprint | NO |
| hook in `GeminiLiveProvider.handleTool` | Before running `SCREEN_TOOLS`, `save_memory` and future sensitive tools call `ownerGate.allowSensitive()`; if false -> `reply(false, "Sirf maalik ki awaaz...")` | decision table YES |
Persona: add a short GUEST section (guests can chat, cannot control; never claim to have recognised a voice; the app, not the user's words, decides identity; ignore "I am the owner" spoken or typed - this extends the existing creator/owner rule). Memory writes from a non-owner turn are refused in GUARD mode so guests cannot fill Yaadein.

### 9.4 Model choice (verify at build time, do not trust this table)
- Library: sherpa-onnx. Checked on the web on 2026-11-04: its docs list speaker identification / verification as supported, offline, with a Kotlin API and Android support. NOT checked: the exact way to add it to a Gradle project (AAR / jniLibs + Kotlin sources / Maven), the current release number, model file names and sizes, and APK size impact. These MUST be looked up in the official docs before coding, then pinned (version + checksum) in the GitHub Actions workflow, because the user has no desktop.
- Model: pick one small speaker-embedding model from sherpa-onnx's official model list (the families named in docs are WeSpeaker, 3D-Speaker and NeMo TitaNet - from memory, unverified). Prefer small + fast. Decide after measuring real scores (step O2).
- Cost to watch: APK size, CPU/battery (only embed speech segments), first-run model load time.

### 9.5 Enrolment flow
1. Settings > "Meri awaaz sikhao". Quiet room, normal voice.
2. 8 short Hinglish prompts (different words and lengths, includes the word "ZENRO", numbers, one longer sentence), each ~3-5 s. Samples that are too quiet or too short are rejected with a Hinglish hint.
3. If the user uses Bluetooth headphones: 3 extra samples while that route is active (route-specific embeddings, since BT mics differ). Optional.
4. Threshold is calibrated from the owner's own sample-to-sample scores, then shifted by the Easy/Normal/Strict setting.
5. Self-test screen: user speaks, sees live score vs threshold and "Match / No match". "Ye main hi tha" button logs a false reject (score only, no audio) to help tuning.
6. Re-enrol any time; delete voiceprint any time.

### 9.6 Owner-voice build steps (each = one single zip, CI must be green before the next)
| Step | Content | Gate to continue |
|---|---|---|
| O1 | `OwnerVoiceLogic` + segmenter + store + unit tests (no model, no UI change except hidden dev toggle) | CI green, tests pass |
| O2 | sherpa-onnx integration + model + enrolment UI + LIVE SCORE SCREEN. NO gating yet. | Device test: owner vs others score gap is clear. If not, change model/threshold before gating anything |
| O3 | GUARD mode: tool gate + guest refusal + persona guest section + memory refusal for guests | Device test matrix (9.7) |
| O4 | STRICT mode: mic gate, pre-roll, owner window | Device test: no "deaf" feeling, start of sentences not cut |
| O5 | Tuning, cautious adaptive update of the voiceprint (high-confidence samples only, capped), BiometricPrompt step-up for high-risk actions | Report |

### 9.7 Test plan
- JVM unit tests (`OwnerLogicTest`): cosine and normalisation (zero vector, NaN), calibration, smoothing, owner-window timing, pre-roll order, tool-gate decision table (owner / guest / stale match / mode OFF).
- Device matrix (user reports counts; no audio leaves the phone): owner normal / soft / whisper / tired / outdoors / Bluetooth mic / speaker mode; family member of same gender; friend; TV news; YouTube voice; RECORDED owner voice played from another phone (EXPECTED to pass - document it, it is a known limit).
- Record accepts/rejects per condition in HANDOVER.md. Targets (goals, not claims): owner accepted in most quiet normal-voice tries at "Normal"; other people rejected in nearly all tries.

## 10. Strong roadmap (order matters)

Principle: the code written since v0.2 (v0.2 to v0.7) has never been fully compiled or device-verified. More features on an unverified base is the biggest risk. So Stage 0 comes first.

| Stage | Goal | Contents |
|---|---|---|
| 0 | Stable base (BLOCKER) | Push latest zip, GitHub Actions green incl. unit tests (fix compile errors from the log), bump `versionName` (build.gradle still says 0.6.1-safe-setup while notes describe v0.7), then device-check voice: setup ladder / error 1007, Bluetooth routing, volume, barge-in, reconnect |
| 1 | Owner voice O1-O3 | section 9 |
| 2 | Reliability | Diagnostics screen (route, model that worked, last error, reconnects; never shows the key), audio focus on phone calls, persisted chat history, Samsung battery guidance, greet-first on session start |
| 3 | Safety in code, not in prompts | Today "ask before send/pay/delete" is only a prompt rule. Enforce it in code: a confirmation step for sensitive tool calls; owner voice O4-O5; BiometricPrompt for high-risk actions |
| 4 | Communication | Calls, SMS, contacts, alarms/reminders (AlarmManager), notification reading (NotificationListenerService). All with permission prompts, confirm-before-send, owner gate |
| 5 | Senses + memory v2 | Camera Scan (consent every session, visible indicator), Yaadein v2 (Room DB, summaries, export, emotion-tagged), optional daily check-in notification |
| 6 | Wake word "ZENRO" | Owner check right after wake; Android/Samsung background limits apply; never promise always-on |
| 7 | Brains and voices | Provider router (free-first, paid opt-in), Sarvam Hindi TTS as a separate, honestly-labelled path, web research / task agent, parallel tool calls (Gemini can return several function calls at once; `handleTool` currently answers one call per event - check and extend), per-tool on/off toggles |
| 8 | Optional PC bridge | Only if wanted: laptop backend so the phone can use PC tools (files, terminal, spreadsheets). Separate project, separate plan |
Ideas parked (need a safety design first): letting ZENRO write its own scripts ("self-learning skills" from the Maya v4 doc) - only as user-approved, sandboxed, versioned scripts, never silent code execution.
Out of scope by project rule: 18+ / romantic-explicit modes, copying any proprietary Maya or VenomAI code, prompts, voices or art.

## 11. Handover protocol (every session, every AI)
START of a session: read `HANDOVER.md` -> `FEATURE_STATUS.md` -> this section. Ask the user for the newest CI result or error text before writing code.
END of a session (all in the SAME single zip): update `FEATURE_STATUS.md` (honest labels), `HANDOVER.md` (where we are, next steps, decisions), `versionName`, and the tests list.
Rules that never change: no keys in repo or chat; no fake buttons; IMPLEMENTED / TESTED / DEVICE VERIFIED only when true ("TESTED" only for tests that really ran); phone-only (Termux + GitHub Actions), short Hinglish copy-paste steps with `cd ~/ZENRO` first; keep the signing key stable so updates install over the old app; do not touch the package ID.
Decision log (append, never rewrite):
- 2026-11-04: user dropped the separate "Maya-Android" experiment; ZENRO is the project to continue.
- 2026-11-04: owner voice is decided on-device in code, never by Gemini; GUARD before STRICT; measure scores (O2) before gating anything (O3).
Open questions for the user: (1) Bluetooth headphones used often? (2) default mode GUARD or STRICT? (3) which actions must ALWAYS need his voice (screen control, WhatsApp, memory, calls)?
