# HANDOVER (keep updated)

Done: Phase 1 voice + Voice v0.2 code (audio routing, volume boost, VAD presets, styles, user gender, key test, Hinglish errors). See FEATURE_STATUS.md.
Verification: v0.1 pure-Kotlin tests passed on JVM earlier. v0.2 tests are written but NOT run; v0.2 Android code is NOT compiled. The user reported v0.1 built on GitHub Actions and installed.
Next: user pushes this zip, runs Actions, sends the last 40 lines of the log if red. Then phone test (ZENRO_PLAN.md section 7: Bluetooth sound, volume, speaker echo, Hindi feel vs Maya).
After that: camera Scan (Phase 3), Diagnostics screen, audio focus, text chat, then memory.
Rules: no keys in repo, no fake features, mark IMPLEMENTED/TESTED/DEVICE VERIFIED honestly, phone-only (Termux + GitHub Actions). No explicit/18+ mode.
