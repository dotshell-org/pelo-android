<p align="center">
<img src="pelo-icon.png" alt="Pelo Logo" width="120" height="120">
</p>

<h1 align="center">Pelo</h1>

<p align="center">
<strong>Public transport app for Lyon and its surroundings</strong>
</p>

<p align="center">
<a href="#features">Features</a> •
<a href="#screenshots">Screenshots</a> •
<a href="#technologies">Technologies</a> •
<a href="#installation">Installation</a> •
<a href="#build">Build</a> •
<a href="#license">License</a>
</p>

---

## About

**Pelo** is a modern, open-source Android application designed to easily navigate the Lyon public transport network (TCL). Built with the latest Android technologies, it offers a smooth and intuitive user experience.

## Features

* 🗺️ **Interactive Map** — Visualize transport stations and lines on a MapLibre map
* 🔍 **Station Search** — Quickly find a station by name
* 🚌 **Line Information** — View details for all lines (metro, tramway, bus, funicular)
* 📍 **Geolocation** — Locate yourself on the map to find the nearest stations
* 🛤️ **Route Planning** — Plan your trips using the RAPTOR algorithm for fast and accurate results
* 📅 **Real-time Schedules** — Access upcoming departure times
* ⭐ **Favorite Lines** — Bookmark your favorite lines for quick access
* 🌙 **Dark UI** — Modern design with a dark theme for optimal visual comfort

## Screenshots

*Coming soon*

## Technologies

### Tech Stack

| Category | Technologies |
| --- | --- |
| **Language** | Kotlin |
| **UI** | Jetpack Compose, Material 3 |
| **Maps** | MapLibre GL Native |
| **Navigation** | Navigation Compose |
| **Network** | Retrofit, OkHttp |
| **Routing** | RAPTOR (Raptor-KT) |
| **Serialization** | Kotlinx Serialization, Gson |
| **Location** | Google Play Services Location |

### Prerequisites

* Android 7.0 (API 24) or higher
* Android Studio Ladybug or higher
* JDK 11

## Installation

### From Source

1. Clone the repository:
```bash
git clone https://github.com/dotshell-org/pelo-android.git
cd pelo-android

```


2. Open the project in Android Studio
3. Sync Gradle dependencies
4. Run the app on an emulator or physical device

## Build

### Debug

```bash
./gradlew assembleDebug

```

The APK will be available in `app/build/outputs/apk/debug/`

### Release

```bash
./gradlew assembleRelease

```

The APK will be available in `app/build/outputs/apk/release/`

## Project Structure

```
pelo-android/
├── app/
│   └── src/
│       └── main/
│           ├── java/com/pelotcl/app/
│           │   ├── data/          # Data layer (API, cache, GTFS)
│           │   ├── ui/            # User Interface
│           │   │   ├── components/  # Reusable components
│           │   │   ├── screens/     # App screens
│           │   │   ├── theme/       # Theme and colors
│           │   │   └── viewmodel/   # ViewModels
│           │   └── utils/         # Utilities
│           ├── assets/            # Precompiled GTFS data
│           └── res/               # Android resources
├── gradle/                        # Gradle configuration
└── scripts/                       # Utility scripts

```

## License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](https://www.google.com/search?q=LICENSE) file for details.

---

<p align="center">
Developed with ❤️ by <a href="[https://github.com/dotshell-org](https://github.com/dotshell-org)">dotshell</a>
</p>
