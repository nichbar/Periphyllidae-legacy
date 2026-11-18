# Project Contract

Kotlin Multiplatform (`jvm()` target) Subsonic music client using Compose Multiplatform, Decompose, Koin, Ktor, and MPV/JNA for desktop playback. See `README.md` for feature overview.

## Build And Test

- Compile shared/desktop JVM: `./gradlew :composeApp:compileKotlinJvm`
- Test JVM/common: `./gradlew :composeApp:jvmTest`
- Test single JVM class: `./gradlew :composeApp:jvmTest --tests "<fqcn>"`
- Run full checks: `./gradlew :composeApp:check`
- Run desktop app (manual inspection): `./gradlew :composeApp:run`
- Package desktop distributable / DMG / UberJAR: `./gradlew :composeApp:createDistributable`, `./gradlew :composeApp:packageDmg`, `./gradlew :composeApp:packageUberJarForCurrentOS`

## Architecture Boundaries

- Shared logic and UI live in `composeApp/src/commonMain/kotlin/now/link/periphyllidae/`; desktop/JVM-only code lives in `jvmMain/`
- Navigation lives in `components/` (`RootComponent.kt` for root stack, `MainComponent.kt` for tabs), routed via `ui/content/` and `ui/layout/`
- Cross-screen MVI state lives in `store/` (`AuthStore.kt`, `MusicStore.kt`, `AppStore.kt`); screen-scoped logic lives in `viewmodels/`
- Data access lives in `repository/` (`SubsonicRepository.kt`); do not let new ViewModels call `SubsonicRepository` directly (route via `store/`; `AlbumDetailViewModel.kt` is legacy tech debt)
- Platform playback differences go behind `platform/PlaybackEngineFactory.kt`, not UI or store code
- Desktop MPV bridge (`MpvPlaybackEngine.kt`, `JnaMpvBridge.kt`, `MpvNativeLoader.kt`) stays in `jvmMain/.../platform/`
- DI bindings live in `commonMain/.../di/` and `jvmMain/.../di/PlatformModule.jvm.kt`

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
- Modify native binaries in `composeApp/appResources/` unless explicitly requested
- Introduce new global stores/singletons without explicit justification
- Commit without running targeted compile and test checks

### ALWAYS

- Show diff before committing
- Keep `commonMain` free of JVM-only (`java.awt`, JNA, `MpvPlaybackEngine`) imports

## Verification

- Shared/common or desktop changes: `./gradlew :composeApp:compileKotlinJvm` + `./gradlew :composeApp:jvmTest`
- Playback/MPV changes: `./gradlew :composeApp:jvmTest --tests "now.link.periphyllidae.platform.*"`
- Broad/cross-layer changes: `./gradlew :composeApp:check`

## Compact Instructions

Preserve:

1. Architecture decisions (NEVER summarize)
2. Modified files and key changes
3. Current verification status (pass/fail commands)
4. Open risks, TODOs, rollback notes
