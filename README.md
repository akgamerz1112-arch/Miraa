# ZENRO (v0.3: voice + chat + memory)

Native Android (Kotlin + Jetpack Compose). Talk to ZENRO in real time using Gemini Live, in Hindi, Hinglish or English.
New independent project. Right now it TALKS only; phone control, camera, memory etc. are later phases (see FEATURE_STATUS.md).

## Phone-only workflow (Termux, no laptop)

### One-time setup
```
pkg update -y && pkg install -y git unzip
termux-setup-storage          # allow storage when asked
git config --global user.name "YOUR NAME"
git config --global user.email "you@example.com"
```

### Upload to your new GitHub repo `ZENRO`
1. Put ZENRO.zip in your phone Downloads, then:
   ```
   cd ~ && rm -rf ZENRO && unzip ~/storage/downloads/ZENRO.zip && cd ZENRO
   ```
2. Create a GitHub Personal Access Token: github.com > Settings > Developer settings > Personal access tokens >
   Fine-grained > only repo ZENRO > Repository permissions: Contents = Read and write, Workflows = Read and write.
   Copy it. NEVER paste this token into any chat or into the code.
3. Push:
   ```
   git init -b main
   git add .
   git commit -m "ZENRO phase 1 voice"
   git remote add origin https://github.com/<your-username>/ZENRO.git
   git push -u origin main
   ```
   When Git asks for Username type your GitHub username; for Password paste the token.
4. GitHub repo > **Actions** > **Android Debug APK** > wait 5-10 min (first run is slower).
5. Open the finished run > **Artifacts** > download **ZENRO-debug-apk** > unzip > install the `.apk`
   (allow "install unknown apps" for your browser/file manager).
6. Open ZENRO > **Settings** > paste Gemini API key (+ your name) > Save > tap the orb and talk.

### Updating later
```
cd ~/ZENRO && git add . && git commit -m "update" && git push
```

## Gemini API key

Free key from Google AI Studio (aistudio.google.com). Paste it ONLY inside the app Settings.
Never put it in the code or on GitHub. Free quota is limited, not unlimited.

## Keep the same signing key (so updates install over the old app)

Without this, every build uses a new debug key and you must uninstall before installing a new build.
Optional: add GitHub repo secrets `ZENRO_KEYSTORE_BASE64`, `ZENRO_KEYSTORE_PASSWORD`, `ZENRO_KEY_ALIAS`, `ZENRO_KEY_PASSWORD`.

## Troubleshooting

- Build red in Actions: open the failed step, copy the last 40 lines of the log and send them.
- Push rejected / auth failed: token needs Contents + Workflows read/write for this repo.
- "Connection closed (1007/1008)": API key wrong, or model name not available. In Settings tap another model chip.
- No sound: raise call volume; check that mic permission is allowed.
- Stops in background: Settings > Battery > ZENRO > Unrestricted.

See FEATURE_STATUS.md for what is real and what is not built yet.
