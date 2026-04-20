<p align="center">
  <img src="fastlane/metadata/android/en-US/images/icon.png" width="120" alt="Auxify Logo">
</p>

<h1 align="center">Auxify</h1>

<p align="center">
  <strong>Subscribe. Sync. Listen offline. Your music, your way.</strong>
</p>

<p align="center">
  <a href="https://github.com/PianoNic/Auxify/stargazers"><img src="https://img.shields.io/github/stars/PianoNic/Auxify?style=flat&color=1DB954" alt="Stars"/></a>
  <a href="https://github.com/PianoNic/Auxify/releases"><img src="https://img.shields.io/github/v/release/PianoNic/Auxify?include_prereleases&color=1DB954&label=Latest" alt="Release"/></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/license-GPL%20v3-1DB954.svg?style=flat" alt="License"/></a>
  <img alt="API" src="https://img.shields.io/badge/API-24%2B-1DB954?style=flat">
</p>

## 🎵 About

Auxify is an offline-first music player that bridges Spotify with high-quality local playback. Subscribe to your Spotify playlists, albums, or liked songs — Auxify automatically downloads them in lossless FLAC and plays them locally with gapless audio.

## ✨ Features

- 🔄 **Subscribe & Sync** — Subscribe to Spotify playlists, albums, and liked songs
- 📥 **Auto Download** — Hourly background sync downloads new tracks in FLAC (Tidal/Qobuz)
- 🎧 **Offline Playback** — Gapless audio with ReplayGain and ExoPlayer + FFmpeg
- 🧠 **Smart Suggestions** — Daily mixes based on your listening habits
- 📂 **Import Existing Library** — Point to your SpotiFLAC folder, metadata already embedded
- 🎨 **Material 3 UI** — Clean, snappy interface with dynamic theming
- 🔋 **Background Sync** — Works even when the app is closed (WorkManager)
- 🚗 **Android Auto** — Full car playback support

## 📸 Screenshots

<!-- TODO: Add screenshots -->

## 📦 Installation

Download the latest APK from the [Releases](https://github.com/PianoNic/Auxify/releases) page.

## 🔨 Building from Source

> ⚠️ This project depends on a private library (`KotifyClient.jar`) not included in the repository. It will not compile without it.

```bash
# Clone with submodules
git clone --recurse-submodules https://github.com/PianoNic/Auxify.git

# Place KotifyClient.jar in app/libs/
cp /path/to/KotifyClient.jar app/libs/

# Build
./gradlew assembleDebug
```

**Requirements:**
- **WSL (Windows Subsystem for Linux)** or a Unix-based OS — native FFmpeg build requires shell scripts
- Android SDK with NDK 28.2
- Java 21
- cmake + ninja-build

## 🏗️ Architecture

| Layer | Purpose |
|-------|---------|
| Playback | ExoPlayer + FFmpeg (gapless, ReplayGain) |
| Library | Musikr — fast file indexing via TagLib/JNI |
| Download | SongLink → Tidal/Qobuz FLAC resolution |
| Sync | WorkManager — hourly background downloads |
| Spotify | KotifyClient — playlists, metadata, recs |
| Recommendations | Local listening history algorithm |

## 🙏 Credits

- [Auxio](https://github.com/OxygenCobalt/Auxio) — Base music player engine
- [SpotiFLAC](https://github.com/spotiflacapp/SpotiFLAC-Mobile) — Download logic inspiration
- [KotifyClient](https://github.com/PianoNic/KotifyClient) — Spotify API (private)

## 📄 License

GPL-3.0 — See [LICENSE](LICENSE) for details.
