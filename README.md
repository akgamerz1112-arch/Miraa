# MIRA (v0.3: voice + chat + memory)

Native Android (Kotlin + Jetpack Compose). Talk to MIRA in real time (LiveKit room + MIRA agent running on your PC), in Hindi, Hinglish or English.
New independent project. Right now it TALKS only; phone control, camera, memory etc. are later phases (see FEATURE_STATUS.md).

## Phone-only workflow (Termux, no laptop)

### One-time setup
```
pkg update -y && pkg install -y git unzip
termux-setup-storage          # allow storage when asked
git config --global user.name "YOUR NAME"
git config --global user.email "you@example.com"
```

### Upload to your new GitHub repo `MIRA`
1. Put MIRA.zip in your phone Downloads, then:
   ```
   cd ~ && rm -rf MIRA && unzip ~/storage/downloads/MIRA.zip && cd MIRA
   ```
2. Create a GitHub Personal Access Token: github.com > Settings > Developer settings > Personal access tokens >
   Fine-grained > only repo MIRA > Repository permissions: Contents = Read and write, Workflows = Read and write.
   Copy it. NEVER paste this token into any chat or into the code.
3. Push:
   ```
   git init -b main
   git add .
   git commit -m "MIRA phase 1 voice"
   git remote add origin https://github.com/<your-username>/MIRA.git
   git push -u origin main
   ```
   When Git asks for Username type your GitHub username; for Password paste the token.
4. GitHub repo > **Actions** > **Android Debug APK** > wait 5-10 min (first run is slower).
5. Open the finished run > **Artifacts** > download **MIRA-debug-apk** > unzip > install the `.apk`
   (allow "install unknown apps" for your browser/file manager).
6. Open MIRA > **Settings** > paste Gemini API key (+ your name) > Save > tap the orb and talk.

### Updating later
```
cd ~/MIRA && git add . && git commit -m "update" && git push
```

## Gemini API key

Free key from Google AI Studio (aistudio.google.com). Paste it ONLY inside the app Settings.
Never put it in the code or on GitHub. Free quota is limited, not unlimited.

## Keep the same signing key (so updates install over the old app)

Without this, every build uses a new debug key and you must uninstall before installing a new build.
Optional: add GitHub repo secrets `MIRA_KEYSTORE_BASE64`, `MIRA_KEYSTORE_PASSWORD`, `MIRA_KEY_ALIAS`, `MIRA_KEY_PASSWORD`.

## Troubleshooting

- Build red in Actions: open the failed step, copy the last 40 lines of the log and send them.
- Push rejected / auth failed: token needs Contents + Workflows read/write for this repo.
- "Connection closed (1007/1008)": API key wrong, or model name not available. In Settings tap another model chip.
- No sound: raise call volume; check that mic permission is allowed.
- Stops in background: Settings > Battery > MIRA > Unrestricted.

See FEATURE_STATUS.md for what is real and what is not built yet.


## v0.9.0 (Session 3): LiveKit client
The Gemini key/model settings are gone. MIRA now needs your PC server (`venomai-main`: agent + API, see its README) and these in Settings:
server URL (**https**), device id and device key (`python device_auth.py add my-phone`). The older "Gemini API key" steps above are obsolete.
Status labels: `FEATURE_STATUS.md`. Phone tests: `PHONE_TEST_CHECKLIST.md`.
