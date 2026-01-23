<p align="center">
  <img src="pelo-icon.png" alt="Pelo Logo" width="120" height="120">
</p>

<h1 align="center">Pelo</h1>

<p align="center">
  <strong>Application de transport en commun pour Lyon et ses environs</strong>
</p>

<p align="center">
  <a href="#fonctionnalités">Fonctionnalités</a> •
  <a href="#captures-décran">Captures d'écran</a> •
  <a href="#technologies">Technologies</a> •
  <a href="#installation">Installation</a> •
  <a href="#compilation">Compilation</a> •
  <a href="#licence">Licence</a>
</p>

---

## À propos

**Pelo** est une application Android moderne et open source permettant de naviguer facilement dans le réseau de transports en commun lyonnais (TCL). Conçue avec les dernières technologies Android, elle offre une expérience utilisateur fluide et intuitive.

## Fonctionnalités

- 🗺️ **Carte interactive** — Visualisez les stations et lignes de transport sur une carte MapLibre
- 🔍 **Recherche de stations** — Trouvez rapidement une station par son nom
- 🚌 **Informations sur les lignes** — Consultez les détails de toutes les lignes (métro, tramway, bus, funiculaire)
- 📍 **Géolocalisation** — Localisez-vous sur la carte pour trouver les stations les plus proches
- 🛤️ **Calcul d'itinéraires** — Planifiez vos trajets avec l'algorithme RAPTOR pour des résultats rapides et précis
- 📅 **Horaires en temps réel** — Accédez aux horaires des prochains passages
- ⭐ **Lignes favorites** — Marquez vos lignes préférées pour un accès rapide
- 🌙 **Interface sombre** — Design moderne avec thème sombre pour un confort visuel optimal

## Captures d'écran

<!-- Ajoutez vos captures d'écran ici -->
<!-- <p align="center">
  <img src="screenshots/screenshot1.png" width="200">
  <img src="screenshots/screenshot2.png" width="200">
  <img src="screenshots/screenshot3.png" width="200">
</p> -->

*À venir*

## Technologies

### Stack technique

| Catégorie | Technologies |
|-----------|-------------|
| **Langage** | Kotlin |
| **UI** | Jetpack Compose, Material 3 |
| **Cartes** | MapLibre GL Native |
| **Navigation** | Navigation Compose |
| **Réseau** | Retrofit, OkHttp |
| **Routage** | RAPTOR (Raptor-KT) |
| **Serialization** | Kotlinx Serialization, Gson |
| **Location** | Google Play Services Location |

### Prérequis

- Android 7.0 (API 24) ou supérieur
- Android Studio Ladybug ou supérieur
- JDK 11

## Installation

### Depuis les sources

1. Clonez le dépôt :
   ```bash
   git clone https://github.com/dotshell-org/pelo-android.git
   cd pelo-android
   ```

2. Ouvrez le projet dans Android Studio

3. Synchronisez les dépendances Gradle

4. Lancez l'application sur un émulateur ou un appareil physique

## Compilation

### Debug

```bash
./gradlew assembleDebug
```

L'APK sera disponible dans `app/build/outputs/apk/debug/`

### Release

```bash
./gradlew assembleRelease
```

L'APK sera disponible dans `app/build/outputs/apk/release/`

## Structure du projet

```
pelo-android/
├── app/
│   └── src/
│       └── main/
│           ├── java/com/pelotcl/app/
│           │   ├── data/          # Couche données (API, cache, GTFS)
│           │   ├── ui/            # Interface utilisateur
│           │   │   ├── components/  # Composants réutilisables
│           │   │   ├── screens/     # Écrans de l'application
│           │   │   ├── theme/       # Thème et couleurs
│           │   │   └── viewmodel/   # ViewModels
│           │   └── utils/         # Utilitaires
│           ├── assets/            # Données GTFS précompilées
│           └── res/               # Ressources Android
├── gradle/                        # Configuration Gradle
└── scripts/                       # Scripts utilitaires
```

## Licence

Ce projet est sous licence **GNU General Public License v3.0** — voir le fichier [LICENSE](LICENSE) pour plus de détails.

---

<p align="center">
  Développé avec ❤️ par <a href="https://github.com/dotshell-org">dotshell</a>
</p>
