<div align="center">

<img src="https://img.shields.io/badge/Kaam%20Chor-v2.0-0084FF?style=for-the-badge&labelColor=001B3D" alt="Kaam Chor" height="40" />

# 🎬 Kaam Chor

**A self-hosted social + chat + calling app.**
Feed · 1-on-1 & group chat · voice notes · audio/video calls — all on your own server.

<p>
  <img src="https://img.shields.io/badge/Android-29%2B-3DDC84?logo=android&logoColor=white&style=flat-square" />
  <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white&style=flat-square" />
  <img src="https://img.shields.io/badge/Jetpack-Compose-4285F4?logo=jetpackcompose&logoColor=white&style=flat-square" />
  <img src="https://img.shields.io/badge/Python-3.10%2B-3776AB?logo=python&logoColor=white&style=flat-square" />
  <img src="https://img.shields.io/badge/Flask-3.0-000000?logo=flask&logoColor=white&style=flat-square" />
  <img src="https://img.shields.io/badge/MariaDB-11-003545?logo=mariadb&logoColor=white&style=flat-square" />
  <img src="https://img.shields.io/badge/WebRTC-native-333333?logo=webrtc&logoColor=white&style=flat-square" />
</p>

<p>
  <a href="../../actions/workflows/android-build.yml"><img src="https://img.shields.io/badge/build%20(Android)-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white&style=flat-square" /></a>
  <a href="../../actions/workflows/backend-ci.yml"><img src="https://img.shields.io/badge/CI%20(Backend)-MariaDB%20tested-2088FF?logo=githubactions&logoColor=white&style=flat-square" /></a>
  <img src="https://img.shields.io/badge/tests-26%2F26%20%2B%207%2F7-success?style=flat-square" />
  <img src="https://img.shields.io/badge/license-MIT-yellow?style=flat-square" />
</p>

</div>

---

<div align="center">

> **A complete rewrite.** Cleaner architecture, fixed the bugs of v1 (missing socket handlers,
> unregistered blueprints, broken call signaling, generic notifications), and a brand-new
> **Material 3 + Liquid Glass** interface.

</div>

---

## ✨ Features

<table>
<tr>
<td width="50%" valign="top">

### 🧑🤝🧑 Social
- 📸 **Photo feed** with cursor-based pagination
- ❤️ **Likes** with optimistic UI + rollback
- 💬 **Comments** with per-post thread
- 👤 **Profiles** — avatar, bio, post grid
- 🔍 **Discover people** & start a chat

### 💬 Messaging
- 1️⃣ **1-on-1 chat** (auto-reuses existing thread)
- 👥 **Group chat**
- 🖼️ **Photo & video** sharing in chat
- 🎤 **Voice notes** (hold-to-record)
- ⌨️ **Typing indicators**
- ✓✓ **Sent / delivered / read** states

</td>
<td width="50%" valign="top">

### 📞 Calling
- 🔊 **Audio calls**
- 🎥 **Video calls**
- 👨👩👧👦 **Group calls** — mesh, up to **4** people
- 🪟 **Liquid-glass** call screen with PiP self-view
- 🔁 Camera switch, mute, camera toggle
- 🌐 **Native WebRTC** (STUN built-in, TURN ready)

### 🎨 Experience
- 🧊 **Subtle Liquid Glass** — bottom nav, top bar, cards, call UI
- 🌗 **Light & dark** from a single brand seed `#0084FF`
- 🌀 **Motion** with critically-damped springs + reduced-motion respect
- 🔔 **Realtime notifications** via foreground service
- 🔐 **JWT** access + rotating refresh tokens

</td>
</tr>
</table>

---

## 🧱 Tech Stack

| Layer | Choice | Why |
|---|---|---|
| **UI** | Jetpack Compose + Material 3 | Modern, declarative, themed from one seed color |
| **Glass** | [Haze](https://github.com/chrisbanes/haze) | Real backdrop blur on API 33+, translucent fallback below |
| **Architecture** | Manual DI (`Graph`) + MVVM | No Hilt overhead for an app this size |
| **Network** | Retrofit + OkHttp + Gson | Boring, reliable, easy to debug |
| **Realtime** | Socket.IO | Chat + WebRTC signaling + notifications over one pipe |
| **Media** | Coil (images) · Media3 (video) · MediaRecorder (voice) | |
| **Calls** | Native WebRTC (`stream-webrtc-android`) | Mesh, no third-party SDK |
| **Storage** | DataStore Preferences | Tokens + server URL |
| **Backend** | Flask + Flask-SocketIO + SQLAlchemy | Small, readable, single process |
| **Database** | MariaDB | Solid, self-hostable |
| **Auth** | Flask-JWT-Extended | Access (30m) + refresh (30d), rotation |

---

## 🏗️ Architecture

```mermaid
flowchart LR
    subgraph Android["📱 Android (Compose)"]
        UI["Screens / Composable"] --> VM["ViewModels"]
        VM --> Repo["AppRepository"]
        Repo --> Api["Retrofit ApiService"]
        VM --> Sock["SocketManager"]
        Svc["SocketService (foreground)"] --> Sock
        Call["CallActivity + WebRtcEngine"] --> Sock
    end

    subgraph Server["🐍 Flask Backend"]
        Api --> Routes["REST Blueprints"]
        Sock --> Events["Socket.IO Handlers"]
        Routes --> DB[("MariaDB")]
        Events --> DB
        Routes --> Files["/uploads"]
    end

    Call -. "offer / answer / ICE" .-> Events
```

**Realtime event flow**

```mermaid
sequenceDiagram
    participant A as Alice 📱
    participant S as Server 🐍
    participant B as Bob 📱
    A->>S: send_message {group_id, text}
    S->>S: persist Message + MessageStatus
    S-->>B: new_message
    S-->>A: message_sent
    B->>S: mark_read
    S-->>A: messages_read
    Note over A,B: Call signaling: call_offer → incoming_call →<br/>call_answer → call_answered → ice_candidate ⇄
```

---

## 📁 Repository Layout

```
kaamchor-v2/
├── backend/                 🐍 Flask API + Socket.IO
│   ├── app.py               app factory (all blueprints registered)
│   ├── config.py            env-driven config
│   ├── models.py            SQLAlchemy models
│   ├── sockets.py           chat + WebRTC signaling + notifications
│   ├── notifications.py     socket-based notifier
│   ├── utils.py             uploads / media URLs / ffprobe
│   ├── routes/              auth, posts, likes_comments, profile, media, chat, calls
│   ├── tests/               integration + realtime tests
│   ├── init_db.py           create tables
│   ├── schema.sql           raw SQL schema
│   └── requirements*.txt
├── android/                 📱 Jetpack Compose app
│   └── app/src/main/java/com/kaamchor/
│       ├── ui/theme/        M3 palette (seed #0084FF)
│       ├── ui/components/   glass + avatar + translucent cards
│       ├── ui/screens/      auth, feed, post, profile, chat, settings
│       ├── ui/call/         WebRtcEngine, CallViewModel, CallScreen, CallActivity
│       ├── data/            remote (Retrofit), local (DataStore), realtime (Socket.IO)
│       ├── service/         foreground SocketService
│       └── Graph.kt         manual DI container
└── .github/workflows/       🤖 CI: android-build, backend-ci
```

---

## 🚀 Quick Start

### 1️⃣ Backend

```bash
cd backend

# database
mariadb -uroot -e "CREATE DATABASE kaamchor CHARACTER SET utf8mb4; \
  CREATE USER 'kaamchor'@'localhost' IDENTIFIED BY 'kaamchor123'; \
  GRANT ALL ON kaamchor.* TO 'kaamchor'@'localhost'; FLUSH PRIVILEGES;"

# python env
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# config
cp .env.example .env      # then edit as needed

# tables + run
python init_db.py
python app.py             # → 0.0.0.0:5000
```

Check it: <http://localhost:5000/api/health> → `{"status":"ok"}`

<details>
<summary><b>🔧 Configuration (.env)</b></summary>

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | `mysql+pymysql://kaamchor:kaamchor@localhost:3306/kaamchor` | SQLAlchemy URL |
| `JWT_SECRET_KEY` | `jwt-dev-secret-change-me` | **Change in production** |
| `JWT_ACCESS_MINUTES` | `30` | Access token lifetime |
| `JWT_REFRESH_DAYS` | `30` | Refresh token lifetime |
| `SERVER_BASE_URL` | `http://localhost:5000` | Base used to build media URLs |
| `UPLOAD_FOLDER` | `./uploads` | Media storage |
| `MAX_CONTENT_LENGTH` | `52428800` | Max upload (50 MB) |
| `VIDEO_MAX_SECONDS` | `15` | Max chat video length |
| `CORS_ORIGINS` | `*` | Allowed origins |
| `ICE_SERVERS` | Google STUN | Edit `config.py` to add coturn/TURN |

</details>

### 2️⃣ Android

```bash
cd android

# local.properties (point at your SDK)
echo "sdk.dir=$ANDROID_HOME" > local.properties

gradle assembleDebug        # CI uses Gradle 8.9 directly
# or generate a wrapper once:
# gradle wrapper && ./gradlew assembleDebug
```

Install on a phone, open **Settings → Server**, and enter your server URL
(e.g. `http://192.168.1.20:5000`). Same Wi-Fi = no tunneling needed.

> 🔓 Cleartext HTTP is enabled for LAN testing. Use HTTPS (reverse proxy + TLS) in production.

---

## 🤖 Building with GitHub Actions

You don't need Android Studio locally — **push to GitHub and let CI build the APK.**

### What's included

| Workflow | File | Does |
|---|---|---|
| 🟢 **Android Build** | `.github/workflows/android-build.yml` | Builds **debug** APK on every push/PR; builds **release** APK on tags / manual run |
| 🐍 **Backend CI** | `.github/workflows/backend-ci.yml` | Spins up MariaDB, runs HTTP integration (26 checks) + realtime Socket.IO (7 checks) |

### Step-by-step

#### 1. Create the repository & push

```bash
cd kaamchor-v2
git init
git add .
git commit -m "Kaam Chor v2"

# create an empty repo on github.com first, then:
git remote add origin https://github.com/OWNER/REPO.git
git branch -M main
git push -u origin main
```

#### 2. Watch the first build

Open **Actions → Android Build**. The `build-debug` job runs automatically. When it's green:

**Actions → latest run → Artifacts → `kaamchor-debug-apk`** → download → install on your phone. 🎉

> ⏱️ First build takes ~5–8 min (Gradle + Compose). Later builds are cached and much faster.
> 🆓 Public repos get unlimited Actions minutes; private repos have a free monthly quota.

#### 3. Manual trigger (optional)

```bash
gh workflow run "Android Build"
# then download the newest run's artifact
gh run download
```

#### 4. 🔐 Signed release APK (optional)

To publish a proper signed release, create a keystore and add 4 secrets.

```bash
# 1. generate a keystore (keep it safe & backed up!)
keytool -genkeypair -v \
  -keystore release.jks \
  -alias kaamchor \
  -keyalg RSA -keysize 2048 -validity 10000

# 2. base64-encode it (single line)
base64 -w0 release.jks > release.jks.b64        # Linux
# macOS / Termux: base64 release.jks | tr -d '\n' > release.jks.b64

# 3. add GitHub secrets
gh secret set KEYSTORE_BASE64     < release.jks.b64
gh secret set KEYSTORE_PASSWORD   # paste keystore password
gh secret set KEY_ALIAS           # e.g. kaamchor
gh secret set KEY_PASSWORD        # paste key password
```

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | base64 of `release.jks` (single line) |
| `KEYSTORE_PASSWORD` | keystore store password |
| `KEY_ALIAS` | key alias (e.g. `kaamchor`) |
| `KEY_PASSWORD` | key password |

> Without secrets, the release job still builds — it just falls back to **debug signing**,
> which is fine for testing but **not** for Play Store uploads.

#### 5. Cut a release

```bash
git tag v2.0.0
git push origin v2.0.0
```

A tag triggers the `build-release` job → **Artifacts → `kaamchor-release-apk`**.

<details>
<summary><b>🧩 Troubleshooting CI</b></summary>

| Symptom | Fix |
|---|---|
| `Could not resolve …` dependency | Bump versions in `android/app/build.gradle.kts` |
| `SDK location not found` | The workflow uses `android-actions/setup-android`; make sure you didn't remove it |
| Haze / Compose compile error | Align `haze` version with the Compose BOM |
| Backend test can't reach DB | Wait for MariaDB healthcheck (already configured, 15 retries) |
| `assembleRelease` unsigned | Add the 4 keystore secrets (step 4) |

</details>

---

## ✅ Testing

```bash
cd backend
pip install -r requirements.txt -r requirements-dev.txt

# HTTP integration (drops & recreates tables)
DATABASE_URL="mysql+pymysql://kaamchor:kaamchor123@127.0.0.1:3306/kaamchor" \
  python tests/test_integration.py      # → 26/26

# realtime (needs a running server)
python app.py & 
python tests/test_realtime.py           # → 7/7
```

Covered: auth + refresh rotation + revocation, posts, feed pagination, likes (toggle),
comments, 1-on-1 group reuse, text + media + voice messages, read state, groups,
ICE config, call records, profile edit, media upload, auth guards, plus live
`new_message` / `user_typing` / `incoming_call` / `call_answered` / notifications.

---

## ⚠️ Notifications & Calls — what to expect

- **Notifications** are delivered over Socket.IO while the app process is alive,
  kept warm by a **foreground service**. If the app is **force-stopped** by the user/OS,
  delivery stops until it's reopened. No FCM/Play dependency by design.
- **Calls** use **STUN** by default (great on the same network). For **4G ↔ Wi-Fi** or
  restrictive NATs, self-host **coturn** and add a TURN server in `backend/config.py`
  → `ICE_SERVERS`.
- Calls start from inside a chat (audio or video buttons in the chat app bar).

---

## 🗺️ Roadmap

- [ ] Push notifications (optional FCM bridge)
- [ ] TURN/coturn docker-compose recipe
- [ ] Message reactions & reply-threading
- [ ] Stories / ephemeral posts
- [ ] End-to-end encrypted DMs
- [ ] Web client

---

## 📜 License

MIT — do what you want, keep the notice. Built with ☕ on **Termux**.

<div align="center">

**⭐ If Kaam Chor is useful, star the repo.**

</div>
