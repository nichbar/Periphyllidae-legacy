# MPV vs VLC Benchmark Notes

Date: 2026-03-02
Platform: macOS arm64 (Darwin 25.1.0), Zulu JDK 17

## Method

Sampling command:

- `ps -o %cpu=,rss= -p <pid>`
- sampled every 5 seconds

Data recorded:

- average CPU (`%cpu`)
- average RSS (`KB`)
- max RSS (`KB`)

## Runs

### 1) VLC baseline (main branch)

- App command: `./gradlew :composeApp:run` (main repo)
- PID sampled: `39639`
- Duration: 60s (12 samples)

Results:

- Avg CPU: **35.94**
- Avg RSS: **946,928 KB**
- Max RSS: **1,365,648 KB**

### 2) MPV variant (feature worktree)

- App command: `./gradlew :composeApp:run` (worktree)
- PID sampled: `41870`
- Duration: 600s (120 samples)

Results:

- Avg CPU: **418.57**
- Avg RSS: **2,443,669 KB**
- Max RSS: **3,321,040 KB**

### 3) MPV short spot-check (feature worktree)

- PID sampled: `37692`
- Duration: 60s (12 samples)

Results:

- Avg CPU: **0.12**
- Avg RSS: **660,539 KB**
- Max RSS: **660,672 KB**

## Interpretation

The short MPV spot-check was better than VLC baseline, but the 10-minute MPV run showed much higher CPU and memory. These results indicate the benchmark scenario was not stable/consistent enough yet for final pass-fail conclusions.

## Next Benchmark Pass (recommended)

To produce final acceptance evidence, rerun with controlled conditions:

1. Same exact playlist (same tracks/order)
2. Same duration for both variants (10 minutes)
3. Same UI state and interaction pattern
4. Record warm-up separately from steady-state window
5. Capture logs alongside metrics for correlation

## Current Status

- Core playback parity: **validated**
- Performance acceptance (CPU/RSS lower than VLC): **not yet conclusively validated**
