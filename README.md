# Simplescan

Einfacher Dokumentenscanner für Android.

- **Scannen:** Kamera über ein Blatt halten, die Seitenränder werden automatisch erkannt. Danach lässt sich der Ausschnitt von Hand korrigieren, drehen und ein Filter (z. B. Schwarzweiß) wählen. Mehrere Seiten landen in einem PDF. Bilder aus der Galerie gehen auch.
- **Speichern:** als PDF im **Download-Ordner** oder in einem **selbst gewählten Ordner** auf dem Telefon (oben auf „Speicherort“ tippen). Vor dem Speichern lässt sich der Dateiname ändern.
- **Zuletzt gespeichert:** Antippen öffnet das PDF, das Teilen-Symbol schickt es weiter.

Der Scanner kommt aus den Google-Play-Diensten (ML Kit Document Scanner). Er braucht ein Gerät mit Google-Play-Diensten und beim ersten Start einmal Internet, um das Scan-Modul zu laden. Die App selbst braucht keine Berechtigungen und schickt nichts ins Netz.

## Installation

Die aktuelle APK gibt es immer unter
`https://github.com/FHeiCode/Simplescan/releases/latest/download/simplescan.apk`

Android 10 oder neuer.

## Build und Release

- Jeder Push baut über GitHub Actions eine APK, zu finden unter *Actions → Lauf → Artifacts*. Dabei laufen auch die Unit-Tests.
- **Release:** Steht in `version.txt` auf `main` eine Version, zu der es noch kein Release gibt, legt der Build das Release `v<Version>` mit APK an. Titel und Text kommen aus `release-notes.md`.
- **Signatur:** Damit Updates sich über die installierte App installieren lassen, im Repo unter *Settings → Secrets and variables → Actions* diese Secrets anlegen:

| Secret | Inhalt |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Keystore als Base64 (`base64 -w0 release.jks`) |
| `SIGNING_STORE_PASSWORD` | Keystore-Passwort |
| `SIGNING_KEY_ALIAS` | Alias des Schlüssels |
| `SIGNING_KEY_PASSWORD` | Schlüssel-Passwort |

Einen neuen Keystore erzeugst du mit:
```bash
keytool -genkeypair -v -keystore release.jks -alias simplescan -keyalg RSA -keysize 4096 -validity 10000
```

Lokal bauen: `gradle testDebugUnitTest assembleDebug` (Gradle 9.8, JDK 17, Android SDK 35).

## Technik

Kotlin, Jetpack Compose, Material 3, DataStore, ML Kit Document Scanner. minSdk 29.
