# iosApp — Xcode shell

The shared UI is 100% `commonMain`; this folder is only the thin Swift shell that
hosts `MainViewController()` from `composeApp/src/iosMain`.

`iosApp.xcodeproj` is **generated** from `project.yml` and is gitignored — the
spec is the source of truth. Regenerate after editing `project.yml` or adding
Swift files.

From the repository root:

```bash
brew install xcodegen                 # once
xcodegen --spec iosApp/project.yml
open iosApp/iosApp.xcodeproj          # choose an installed simulator and Run
# or build from the command line:
xcodebuild -project iosApp/iosApp.xcodeproj -scheme UniqueLo \
  -destination 'generic/platform=iOS Simulator' -configuration Debug build
```

The `Compile Kotlin Framework` pre-build phase invokes
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`, so the Kotlin
framework is always built to match Xcode's configuration, architecture and
SDK. The framework is static, so Xcode skips the embed/sign copy step;
`assembleDebugAppleFrameworkForXcode` builds the archive. Script sandboxing is
disabled (`ENABLE_USER_SCRIPT_SANDBOXING = NO`) because Gradle writes into the
source tree during that phase. For Xcode-only UI work, add
`OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED=YES` to the scheme's environment (or pass
it to `xcodebuild`) to skip the Gradle phase when the framework is already
built.

Notes:

- Bundle id `com.uniquelo.app` mirrors the Android `applicationId`; change both
  together if the app is re-branded for the store.
- Deployment target is iOS 15.0 (chosen minimum). Xcode 27.1 warns that the
  bundled ICU data object was built for iOS-simulator 17.2; the object has a
  zero-byte `__text` section (data only), so the warning is non-fatal. Keep the
  15.0 target unless we decide to drop iOS 15/16 support.
- `UniqueLoApp.swift` applies `.ignoresSafeArea()`; Compose draws the app
  background under the device safe area and pads content with
  `WindowInsets.safeDrawing`. `SystemBarsEffect.ios.kt` sets the key window's
  `overrideUserInterfaceStyle` from the resolved theme, so status-bar icons and
  the keyboard follow the in-app Light/Dark choice even when the system
  appearance differs. System mode clears the override, so live OS appearance
  changes are still followed.
- `Assets.xcassets` carries the red/white app icon (D-16 square geometry), the
  `AccentColor` brand red `#D5001C`, and a `LaunchBackground` color that matches
  `Palette.Bone` in both appearances so there is no flash before the first
  Compose frame.
- No signing team is checked in; select one in Xcode (Signing & Capabilities)
  or pass `DEVELOPMENT_TEAM=` to `xcodebuild` for device builds.
