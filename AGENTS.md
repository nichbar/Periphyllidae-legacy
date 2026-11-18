# Project Contract

Kotlin Multiplatform (Android + Desktop JVM) Subsonic music client using Compose Multiplatform, Decompose, Koin, Ktor, MediaMP (ExoPlayer on Android), and MPV/JNA (desktop). See `README.md` for feature overview.

## Build And Test

- Compile shared/desktop JVM: `./gradlew :composeApp:compileKotlinJvm`
- Compile Android debug: `./gradlew :composeApp:compileDebugKotlinAndroid`
- Test JVM/common: `./gradlew :composeApp:jvmTest`
- Test single JVM class: `./gradlew :composeApp:jvmTest --tests "<fqcn>"`
- Run full checks: `./gradlew :composeApp:check`
- Run desktop app (manual inspection): `./gradlew :composeApp:run`
- Build Android debug APK: `./gradlew :composeApp:assembleDebug`
- Package desktop distributable / DMG / UberJAR: `./gradlew :composeApp:createDistributable`, `./gradlew :composeApp:packageDmg`, `./gradlew :composeApp:packageUberJarForCurrentOS`

## Architecture Boundaries

- Shared logic and UI live in `composeApp/src/commonMain/kotlin/now/link/periphyllidae/`; platform code lives in `androidMain/` and `jvmMain/`
- Navigation lives in `components/` (`RootComponent.kt` for root stack, `MainComponent.kt` for tabs), routed via `ui/content/` and `ui/layout/`
- Cross-screen MVI state lives in `store/` (`AuthStore.kt`, `MusicStore.kt`, `AppStore.kt`); screen-scoped logic lives in `viewmodels/`
- Data access lives in `repository/` (`SubsonicRepository.kt`); do not let new ViewModels call `SubsonicRepository` directly (route via `store/`; `AlbumDetailViewModel.kt` is legacy tech debt)
- Platform playback differences go behind `platform/PlaybackEngineFactory.kt`, not UI or store code
- Desktop MPV bridge (`MpvPlaybackEngine.kt`, `JnaMpvBridge.kt`, `MpvNativeLoader.kt`) stays in `jvmMain/.../platform/`; Android ExoPlayer/service stays in `androidMain/.../`
- DI bindings live in `commonMain/.../di/` and platform `PlatformModule.*.kt`

## Coding Conventions

- Follow `StateFlow` MVI: Intent -> Store/ViewModel handler -> `StateFlow` update; collect in UI with `collectAsStateWithLifecycle`
- Keep search/find state screen-scoped in ViewModels rather than adding a global SearchStore
- Launch long-running coroutines on `ApplicationScopeManager.scope` (`commonMain/.../utils/ApplicationScopeManager.kt`)
- Log only via `LogManager` (`commonMain/.../utils/LogManager.kt`) with appropriate level and tag
- Manage dependencies via `gradle/libs.versions.toml`
- Follow `kotlin.code.style=official` (`gradle.properties`)

## Safety Rails

### NEVER

- Edit `.env`, `local.properties`, or CI/signing secrets without explicit approval
- Modify the `mediamp/` git submodule or native binaries in `composeApp/appResources/` unless explicitly requested
- Introduce new global stores/singletons without explicit justification
- Commit without running targeted compile and test checks

### ALWAYS

- Show diff before committing
- Keep `commonMain` free of JVM-only (`java.awt`, JNA, `MpvPlaybackEngine`) or Android-only imports

## Verification

- Shared/common or desktop changes: `./gradlew :composeApp:compileKotlinJvm` + `./gradlew :composeApp:jvmTest`
- Android-touching changes: `./gradlew :composeApp:compileDebugKotlinAndroid` (+ `./gradlew :composeApp:assembleDebug` if manifest/packaging changed)
- Playback/MPV changes: `./gradlew :composeApp:jvmTest --tests "now.link.periphyllidae.platform.*"`
- Broad/cross-layer changes: `./gradlew :composeApp:check`

## Compact Instructions

Preserve:

1. Architecture decisions (NEVER summarize)
2. Modified files and key changes
3. Current verification status (pass/fail commands)
4. Open risks, TODOs, rollback notes
