# Zork I

An offline interactive-fiction player for the Light Phone III. Zork I: The Great Underground Empire, running on a Z-machine interpreter built into the tool.

A community tool for the [Light Phone III](https://www.thelightphone.com/), built on the official
[light-sdk](https://github.com/lightphone/light-sdk) with Kotlin, Jetpack Compose, `LightScreen`
and `LightViewModel`, themed with `sdk:ui`.

## Layout

```
zork-i/
├── light-sdk/            # git submodule -> upstream Light SDK (pinned commit)
├── tool/                 # the ONLY dev-owned module
│   ├── lighttool.toml    # tool id, label, version, declared permissions
│   ├── build.gradle.kts  # dependencies (project(":sdk:client") + light.sdk plugin)
│   └── src/main/**       # tool source
├── settings.gradle.kts   # grafts the submodule's SDK projects into this build
├── build.gradle.kts      # thin root: plugin classpath + ext build knobs
├── gradle.properties
└── gradlew, gradle/       # wrapper
```

Light's containerized builder clones this repo and extracts **only**
`tool/build.gradle.kts`, `tool/lighttool.toml`, and `tool/src/main/**`, then compiles them against
its own pinned, sandboxed copy of the SDK. It generates `AndroidManifest.xml` from `lighttool.toml`,
so this repo intentionally contains no hand-written manifest and never sets `applicationId`,
`versionCode`, `versionName`, or `namespace`.

## Building locally

Requires JDK 17 and an Android SDK. Then:

```
git submodule update --init --recursive
./gradlew :tool:assembleDebug
# -> tool/build/outputs/apk/debug/tool-debug.apk
```

The APK is signed with the shared Light dev keystore (from the submodule) for local sideloading.
Light's build service re-signs with its own key.

## Privacy

Zork I runs entirely on-device. It makes no network requests, collects no data, and has no analytics or accounts. The story file is bundled in the APK.

See PRIVACY.md for the network endpoints it contacts.

## Content & licensing

- Zork I source code and the Z-Machine specification: MIT License (Microsoft).
- Interpreter engine `de.onyxbits.textfiction.zengine`: Apache-2.0.
- Original Zplet interpreter by Matthew T. Russotto: Artistic License.

## License

MIT, the same as upstream light-sdk. See [LICENSE](LICENSE).

The Light Phone, LightOS, and Light Phone III are names of The Light Phone; this project is not
affiliated with or endorsed by The Light Phone.
