# Zork I for Light Phone III

A port of the classic interactive fiction game **Zork I** to the Light Phone III SDK.
Runs entirely offline — the Zork story file is embedded in the APK, so no internet
is needed.

## Download APK

`Zork-I-release.apk` in the root of this repo is a signed release build, arm64-v8a
only, ~16MB. Install it on a Light Phone III:

```bash
adb install -r Zork-I-release.apk
```

## Build from source

Requires the Light Phone III SDK and Android SDK (OpenJDK 17).

```bash
# Debug
JAVA_HOME=/usr/local/opt/openjdk@17 ANDROID_SDK_ROOT=~/Library/Android/sdk \
  ./gradlew assembleDebug

# Release (arm64 only, minified)
JAVA_HOME=/usr/local/opt/openjdk@17 ANDROID_SDK_ROOT=~/Library/Android/sdk \
  ./gradlew assembleRelease
```

## Architecture

```
zork/
├── build.gradle.kts                    # Android app module — depends on :engine
├── engine/                             # Java library module — the Z-machine interpreter
│   ├── build.gradle.kts
│   └── src/main/java/de/onyxbits/textfiction/zengine/
│           ├── ZMachine*.java          # Z-machine v3/v5/v8 opcodes
│           ├── ZWindow.java            # Frame buffer + input handling
│           └── ...                     # Dictionary, object tree, IFF parsing
└── src/main/kotlin/com/mortimort/zork/
    ├── ZorkScreen.kt                   # Main game UI + keyboard editor + About page
    ├── ZorkViewModel.kt                # State: transcript, status line, engine wrapper
    ├── ZorkEngine.kt                   # Kotlin adapter over the Java Z-machine
    ├── ZorkEntryPoint.kt               # SDK lifecycle hook (no-op — offline tool)
    ├── StoryData.kt                    # Zork I story file as base64 chunks
    └── AboutText.kt                    # About page content (copyright, MIT, SDK credit)
```

- **Engine (Java):** Vendored [Zplet](http://zplet.sourceforge.net/) interpreter
  (`de.onyxbits.textfiction.zengine`, Apache-2.0). Compiled as a `:engine` Java library
  module — the Light SDK's Kotlin-only source check only applies to a tool module's
  own `src/`, so Java dependencies are allowed.
- **UI (Kotlin):** Three screens — main game, keyboard editor, About page.

## Project status

- [x] Z-machine v3 engine (Zplet) compiled as Java lib
- [x] Zork I story file embedded as base64
- [x] Main screen with scrollable transcript, command input via LP3 keyboard editor
- [x] About page (title card, copyright, Microsoft MIT release note, SDK credit)
- [x] Restart button on About page — resets Z-machine, returns to main screen
- [x] Back button on About page — returns to main screen
- [x] Release APK built and included (`Zork-I-release.apk`, 16MB arm64)
- [x] Save-game support — three numbered slots, explicit "save" command, top-left Saves browser (load/delete)

## Credits & licensing

- **Zork I story & source:** Originally by Infocom (1980). In 2025, Microsoft released
  the Zork I source code and Z-Machine specification under the MIT License.
- **Interpreter:** Zplet by Matthew T. Russotto, released under the Artistic License.
- **Light Phone III SDK:** com.thelightphone.sdk (thelightphone.com).

This is an independent, non-commercial fan project. Zork and Infocom are trademarks
of their respective owners.
