# Replace VLC with MPV on Desktop — Design

Date: 2026-02-28
Status: Approved

## Goal
Replace the desktop VLC backend with direct MPV integration in `jvmMain` to reduce CPU and memory usage while maintaining core playback parity.

## Scope
### In scope (phase 1)
- Desktop targets: macOS, Linux, Windows
- Core parity only:
  - play/pause
  - seek
  - next/previous (through existing queue flow)
  - stream URL playback
  - position/duration/progress updates

### Out of scope (phase 1)
- VLC-specific advanced behavior beyond core controls
- Dual backend/fallback complexity

## Constraints and Success Criteria
- Primary success criterion: lower CPU/memory usage versus current VLC desktop backend.
- Keep existing shared architecture intact (`MusicPlayer` contract, store/viewmodel flow).
- Minimize UI-layer impact.

## Approaches Considered

### A) Embedded `libmpv` via direct JVM binding (Recommended)
Use direct MPV library bindings from `jvmMain` (e.g., JNA/JNR) and an event-driven bridge.

**Pros**
- Best potential CPU/memory efficiency
- Fine-grained tuning control
- Strong architecture fit with existing platform-specific factory pattern

**Cons**
- Higher native integration complexity
- Requires robust cross-platform native packaging

### B) `mpv` subprocess + JSON IPC
Control standalone `mpv` process via IPC.

**Pros**
- Faster prototype path
- Simpler native interop concerns

**Cons**
- Extra process overhead (less aligned with perf goal)
- Process lifecycle complexity

### C) Hybrid rollout (IPC first, embedded later)
Ship IPC first, then migrate internals to embedded libmpv.

**Pros**
- Lower initial migration risk

**Cons**
- Duplicated work
- Delayed performance target

## Selected Architecture
Adopt **Approach A**: direct MPV binding in `jvmMain` with thin bridging and event-driven state updates.

### Components
1. **`MpvNativeLoader`** (`jvmMain`)
   - Detect OS/arch
   - Load `libmpv` from packaged native location
   - Fail fast with clear logs if loading fails

2. **`MpvBridge`** (`jvmMain`)
   - Low-level wrapper around MPV API calls:
     - init/shutdown context
     - set options
     - execute commands
     - observe properties/events
   - Isolates native details from player logic

3. **`MpvMusicPlayer`** (`jvmMain`)
   - Implements existing `MusicPlayer` interface
   - Maps core commands to MPV
   - Converts MPV events/properties into existing state update flow

4. **Desktop factory/DI wiring update**
   - Replace VLC desktop implementation instantiation with MPV-backed one

### Data Flow
1. Existing app/store logic calls `MusicPlayer` APIs.
2. `MpvMusicPlayer` forwards operations via `MpvBridge`.
3. MPV emits observed property/event updates.
4. `MpvMusicPlayer` maps updates into existing state flow.
5. UI continues consuming unchanged store/viewmodel state.

## Core Parity Mapping
- `play(url)` → MPV `loadfile`
- `pause/resume` → MPV `pause` property
- `seekTo(ms)` → absolute seek command
- `next/prev` → existing queue orchestration remains unchanged
- progress/duration updates → property observation (event-driven) rather than tight polling

## Performance Strategy
- Prefer event-driven updates over frequent polling loops
- Throttle progress propagation to avoid unnecessary recomposition churn
- Enable hardware decode defaults when stable on target OS
- Keep cache/buffer tuning conservative and measurable

## Error Handling
- **Init failures** (missing lib/unsupported arch/init error):
  - clear `LogManager` errors
  - playback initialization fails with user-visible error state
- **Runtime command errors**:
  - check and log MPV return codes with context
- **Stream/decode/eof events**:
  - map to existing track-completion/error semantics
- **Shutdown/cleanup**:
  - deterministic native dispose to avoid leaks

## Cross-Platform Packaging
- Bundle native MPV libraries for macOS/Linux/Windows desktop distributions
- Resolve runtime load paths via `MpvNativeLoader`
- Add startup verification for native availability
- No VLC fallback in phase 1 (avoid unnecessary complexity)

## Validation and Testing
1. Manual desktop validation on macOS/Linux/Windows:
   - play/pause/seek/next/prev
   - stream playback
   - end-of-track behavior
2. Verify state propagation correctness (position/duration/pause)
3. Compare CPU and memory vs VLC baseline under same playback scenario
4. Confirm app still runs with:
   - `./gradlew :composeApp:run`

## Risks and Mitigations
- **Native packaging variance across OSes**
  - Mitigation: explicit loader checks + startup diagnostics
- **Interop lifecycle mistakes (leaks/crashes)**
  - Mitigation: strict init/dispose ownership in `MpvMusicPlayer`
- **Event mapping mismatches with existing queue/state assumptions**
  - Mitigation: keep queue logic unchanged; adapt in player boundary only

## Performance Validation Snapshot
- Benchmark notes: `docs/plans/2026-02-28-replace-vlc-with-mpv-benchmark-notes.md`
- Current measurements are mixed across short and long runs; core parity is confirmed, but CPU/RSS acceptance requires one more controlled benchmark pass using identical playback scenario and duration for VLC vs MPV.

