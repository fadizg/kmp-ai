# kmp-ai sample iOS app

Standalone Xcode shell that hosts the Compose Multiplatform chat UI from
`samples/app/src/iosMain/.../MainViewController.kt` on iOS. Open in
Xcode, pick a simulator, tap Run.

## First-time setup

```bash
cd samples/app/iosApp
./setup.sh         # installs xcodegen via brew if needed, then generates iosApp.xcodeproj
open iosApp.xcodeproj
```

In Xcode: select a simulator (e.g. *iPhone 15 Pro*), hit ⌘R.

The first build downloads `llama.xcframework` (~200 MB) into
`<repo>/.cache/`. Subsequent builds reuse it.

## What the project does

- A pre-build script invokes
  `./gradlew :samples:app:embedAndSignAppleFrameworkForXcode`, which
  builds and embeds `ComposeApp.framework` for the SDK + arch Xcode is
  driving (Debug/Release × iphoneos/iphonesimulator × arm64/x86_64).
- `llama.xcframework` is added as an embedded dependency from
  `<repo>/.cache/llama.xcframework`. Xcode auto-selects the right slice
  per SDK and embeds the dylibs into the `.app` bundle.
- `ContentView.swift` wraps `MainViewController()` from the Compose
  framework via `UIViewControllerRepresentable`.

## Running on a real device

Set a `DEVELOPMENT_TEAM` in `project.yml`:

```yaml
settings:
  base:
    DEVELOPMENT_TEAM: YOURTEAMID
```

…then re-run `./setup.sh` and select your device target in Xcode.

## Why XcodeGen instead of a committed `.xcodeproj`

Xcode's `project.pbxproj` is a 400+ line opaque format that's painful to
review in a PR and prone to merge conflicts. `project.yml` is a single
80-line spec that diff-reviews clean. Trade-off: contributors need
`xcodegen` (one `brew install`, handled by `setup.sh`).
