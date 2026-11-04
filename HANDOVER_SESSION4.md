# HANDOVER for Session 4 of 4 (written at the end of Session 3)

Do ONLY Session 4 scope. Owner: hurry, dislikes many questions, writes Hinglish. Decide small things, state the assumption in one line, ASK FIRST only before heavy/destructive tasks. Replies short, Hinglish, mobile-friendly. Keep all existing work. Never open/print `.env*`. Remind: rotate Google, LiveKit, Mem0 keys.

## State (honest)
- Android v0.9.0-livekit (code 8): LiveKit client, `/v1/config` + `/v1/token`, Settings, connection UI, 9 RPC handlers, owner switch "Control do" (default OFF). NOT compiled, NOT device verified. Only pure logic compiled/run: `S3Test` 12 PASS (gson stand-in for org.json).
- Backend (Session 2) unchanged and still not run for real (agent, LiveKit, Gemini, FastAPI, token signing, ChromaDB/FastEmbed).
- Read first: `HANDOVER.md` (Session 3 notes: verified vs unverified LiveKit API, build risks), `FEATURE_STATUS.md`, `PHONE_TEST_CHECKLIST.md`, `venomai-main/HANDOVER.md`.

## SESSION 4 SCOPE (from the roadmap)
0. FIRST: try to build. If red, get the `e:` lines from the owner (likely suspects in HANDOVER.md: Kotlin 1.9.24 vs LiveKit metadata, compileSdk, `room.events.collect`/`remoteParticipants`/`release`, RpcHandler signature). Minimal fixes only.
1. Phone versions of useful PC tools (WhatsApp, YouTube, open app via Accessibility) as server-side tool declarations that call the EXISTING nine phone actions (backend deploy only, no new APK). Anything needing a new permission/executor needs a new APK: say so.
2. Phone skills written from scratch (no copying of `mobile-maya-skills`, which has no licence).
3. First real run of RAG (chromadb/fastembed, verify the embedding model name) + OCR / upload idea.
4. Diagnostics (small screen: server reachable, config versions, token OK, agent joined, Accessibility on) and final docs.

## Known leftovers / ideas
- Unused now: `audio/AudioRouter.kt`, `audio/Pcm.kt`, `ScreenStreamer`/`FrameGate` (screen frames no longer go anywhere). Delete only after ASK, or reuse for diagnostics.
- Phone tool descriptions on the server mention "look at the next screen frame" but the app does not publish the screen. Either reword the tool text (backend only) or add screen publishing (needs a new APK + privacy care).
- No transcript in the UI. LiveKit transcription text streams are an option (verify in current docs first).
- Yaadein on the phone is separate from the server's SQLite memory; decide whether to keep or point it at the server.
- Persona/identity lines (MIRA, Ayaan Sir, "Boss", never human, no 18+) now live only in `venomai-main/prompts.py` (the Android `Persona.kt` is gone). Check they are still all there and add a test if missing.
- Hosting: PC + LiveKit Cloud by default; anything else = ASK. Licence of `HunterisLive-1` still unconfirmed.
