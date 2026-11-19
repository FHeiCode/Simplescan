# Simplescan

Einfacher Dokumentenscanner für Android.

<img src="store/icon_512.png" width="96" alt="Simplescan-Logo">

- **Scannen:** Kamera über ein Blatt halten, die Seitenränder werden automatisch erkannt. Danach lässt sich der Ausschnitt von Hand korrigieren, drehen und ein Filter (z. B. Schwarzweiß) wählen. Mehrere Seiten landen in einem PDF. Bilder aus der Galerie gehen auch.
- **Speichern:** als PDF im **Download-Ordner**, in einem **selbst gewählten Ordner** auf dem Telefon oder direkt auf **Nextcloud / WebDAV** (oben auf „Speicherort“ tippen). Vor dem Speichern lassen sich Dateiname und Ziel ändern.
- **Nextcloud / WebDAV:** Server-Adresse, Benutzername, Passwort und Ordner eintragen, „Prüfen und speichern“ tippen. Bei Nextcloud genügt die normale Adresse (z. B. `cloud.example.de`), die App ergänzt `/remote.php/dav/files/<Benutzer>/`. Am besten ein App-Passwort nehmen (Nextcloud → Persönliche Einstellungen → Sicherheit). Meldest du dich mit E-Mail an, trag die vollständige WebDAV-Adresse aus Nextcloud (Dateien → Einstellungen → WebDAV) als Server-Adresse ein. Fehlt der Ordner, wird er angelegt. Gleichnamige Dateien werden nicht überschrieben, sondern bekommen „(1)“, „(2)“ … angehängt. Das Passwort liegt verschlüsselt im Android-Keystore. Hochgeladene Scans öffnen sich aus der Liste im Browser; Teilen schickt den Link.
- **Zuletzt gespeichert:** Antippen öffnet das PDF, das Teilen-Symbol schickt es weiter. Das Brief-Symbol öffnet direkt eine E-Mail: lokal gespeicherte PDFs hängen als Anhang dran, bei Nextcloud kommt ein öffentlicher Link hinein (Nur-Lesen-Freigabe, wird einmal erstellt und dann wiederverwendet), bei anderen WebDAV-Servern die direkte Adresse. Dasselbe geht gleich nach dem Speichern über „Per E-Mail“ im Hinweis unten.
- **Feste Empfänger-Adresse:** auf der Startseite unter „E-Mail an“ festlegen (mehrere mit Komma). Sie steht dann bei jedem Versand per E-Mail gleich im Feld „An“; leer lassen für keine.
- **Mehrere auf einmal verschicken:** einen Eintrag lange drücken, weitere antippen (oder oben „Alle auswählen“), dann oben „Per E-Mail“ oder „Teilen“. Lokale PDFs kommen als Anhänge mit, hochgeladene als Links im Text. Dateien, die du inzwischen gelöscht hast (auf dem Handy oder auf dem Server), verschwinden beim nächsten Öffnen der App aus der Liste. Ist der Server gerade nicht erreichbar, bleiben die Einträge stehen.

Der Scanner kommt aus den Google-Play-Diensten (ML Kit Document Scanner). Er braucht ein Gerät mit Google-Play-Diensten und beim ersten Start einmal Internet, um das Scan-Modul zu laden. Die App braucht nur die Internet-Berechtigung, und die nur für Nextcloud/WebDAV. Sonst geht nichts ins Netz.

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

Kotlin, Jetpack Compose, Material 3, DataStore, ML Kit Document Scanner, OkHttp (WebDAV). minSdk 29.
