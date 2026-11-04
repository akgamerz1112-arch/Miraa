# MIRA phone test checklist (v0.9.0-livekit)  -  nothing here has been run yet

Mark each line PASS / FAIL + one-line note. Send FAIL lines (and any red `e:` build lines) to the next session.

## A. Before the phone
1. PC: agent running (`python main.py dev`), API running (`uvicorn api_server:app --host 0.0.0.0 --port 8080`), LiveKit keys set.
2. HTTPS to the API (e.g. Cloudflare Tunnel to port 8080). Open `https://<your-url>/v1/config` in the phone browser: JSON with `api_version` shows = OK.
3. `python device_auth.py add my-phone` -> copy the key (shown once).
4. GitHub Actions build is green; install the APK (uninstall older MIRA/ZENRO first).

## B. App setup
5. Settings: URL `https://...`, device id `my-phone`, key -> Save. Typing `http://...` shows the https warning.
6. Tap the orb, allow Mic (and notifications). Status goes: Server check -> Connect -> "agent ka intezaar" -> "MIRA aa gayi".
7. Notification "MIRA" visible while connected; "Mic band karo" and "Session band" buttons work.
8. Say "Hello" -> MIRA answers by voice. Mute -> MIRA stops hearing you. Unmute -> hears again.

## C. Errors (each should show a plain Hinglish message, no crash)
9. Wrong key -> "Device id ya device key galat (401)".
10. Agent not running -> stays "agent ka intezaar".
11. PC/tunnel off -> "Server tak pahunch nahi paaye".
12. Set `min_app_version` to 99 in config.json -> "App purani hai ... update app" (UPDATE state, red).
13. Airplane mode mid-call -> "Dobara connect..." then error or recover.

## D. Phone control (Accessibility ON first: App info > 3 dots > Allow restricted settings > Accessibility > MIRA)
14. "Control do" OFF: say "YouTube kholo" -> MIRA says she cannot (ok=false "Control band hai").
15. "Control do" ON: "YouTube khol do" -> opens. "Volume badhao" -> volume changes. "Music pause karo" -> media key.
16. "WhatsApp pe <contact> ko hi likho" -> chat opens with text typed, NOT sent.
17. Focus a password box, ask her to type -> refuses.
18. "Scroll down" / "back dabao" -> swipe / back works. The cyan "MIRA ... kar rahi hai" line appears for each action.
19. Notification "Control band" turns Control OFF.

## E. Stability
20. Lock the screen 2 min mid-call, unlock: still connected or clean error.
21. Close the app from recents: mic notification disappears (no secret mic use).
