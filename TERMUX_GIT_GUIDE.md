# Termux se GitHub pe upload (ZENRO) - chhote steps

Pehli baar sirf Step 1-4 ek baar. Har update pe sirf "Har update" wale commands.

## Ek baar ka setup
1. Termux (F-Droid wala) kholo:
   pkg update -y && pkg install -y git unzip
   termux-setup-storage        # permission "Allow" do
2. Git ka naam (apna do):
   git config --global user.name "TumharaNaam"
   git config --global user.email "tumhara@email.com"
   git config --global credential.helper store
3. GitHub token banao (browser mein): GitHub > Settings > Developer settings > Personal access tokens > Tokens (classic) > Generate new token > tick "repo" AUR "workflow" > Generate > copy karo.
   Token kisi ko chat mein mat bhejo. Password ki jagah ye token use hota hai.
4. GitHub pe naya khaali repo banao (jaise ZENRO). Uska link: https://github.com/USERNAME/REPO.git

## Pehli baar upload
cd ~
unzip -o ~/storage/downloads/ZENRO.zip      # ~/ZENRO folder banega
cd ~/ZENRO
git init
git branch -M main
git remote add origin https://github.com/USERNAME/REPO.git
git add -A
git commit -m "ZENRO v0.6"
git push -u origin main
# Username poochhe to GitHub username, Password poochhe to TOKEN paste karo.

## Har update (naya ZENRO.zip aane par)
cd ~
unzip -o ~/storage/downloads/ZENRO.zip      # purani files ke upar, baaki kuch delete nahi hota
cd ~/ZENRO
git add -A
git commit -m "ZENRO v0.6 update"
git push

## Agar push mein "rejected / fetch first" aaye
git pull --rebase origin main
git push
(--force tabhi jab pakka pata ho ki repo mein kuch zaroori nahi.)

## APK banana
GitHub repo > Actions > "Android Debug APK" > (push par khud chalta hai, nahi to Run workflow) > run poora hone do (5-10 min)
> Artifacts > ZENRO-debug-apk download > unzip > .apk install (purane app ke upar ho jata hai).
Build red ho to: us run pe click > laal step kholo > last 40 lines copy karke Claude ko bhejo.

## Phone par pehli baar
APK install > Settings > Apps > ZENRO > 3 dots > "Allow restricted settings" > Settings > Accessibility > ZENRO > ON.

## Safety
API key repo mein mat daalo (sirf app ke Settings mein). Token kabhi commit ya chat mein nahi.
